#!/usr/bin/env sh
set -eu

log() {
  printf '[init-keycloak] %s\n' "$*"
}

# --------------------------------------------------------------------
# Конфиг из env
# --------------------------------------------------------------------
KC_URL="${KC_URL:-http://keycloak:8080}"

BOOT_USER="${BOOTSTRAP_ADMIN_USERNAME:-admin}"
BOOT_PASS="${BOOTSTRAP_ADMIN_PASSWORD:-admin}"

PERM_USER="${PERM_ADMIN_USERNAME:-kcadmin}"
PERM_PASS="${PERM_ADMIN_PASSWORD:-kcadmin}"

DELETE_BOOTSTRAP="${DELETE_BOOTSTRAP_ADMIN:-false}"

MASTER_REALM="master"
REALM_MGMT_CLIENT_ID="realm-management"
REALM_ADMIN_ROLE_NAME="realm-admin"

log "KC_URL=${KC_URL}"
log "BOOT_USER=${BOOT_USER}, PERM_USER=${PERM_USER}, DELETE_BOOTSTRAP=${DELETE_BOOTSTRAP}"

# --------------------------------------------------------------------
# Минимальные зависимости
# --------------------------------------------------------------------
install_dependencies() {
  if ! command -v jq >/dev/null 2>&1; then
    log "Installing jq..."
    apk add --no-cache curl jq >/dev/null 2>&1
  fi
}

# --------------------------------------------------------------------
# Ожидание Keycloak
# --------------------------------------------------------------------
wait_for_keycloak() {
  log "Waiting for Keycloak at ${KC_URL} ..."
  while :; do
    if curl -fsS "${KC_URL}/realms/${MASTER_REALM}/.well-known/openid-configuration" \
        >/dev/null 2>&1; then
      log "Keycloak is ready"
      break
    fi
    log "Keycloak not ready yet, retrying in 5s..."
    sleep 5
  done
}

# --------------------------------------------------------------------
# Получение admin token по паролю bootstrap-админа
# --------------------------------------------------------------------
get_admin_token() {
  log "Getting admin token with bootstrap user '${BOOT_USER}'..."

  resp="$(
    curl -sS -w '\n%{http_code}' \
      -X POST "${KC_URL}/realms/${MASTER_REALM}/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data "grant_type=password&client_id=admin-cli&username=${BOOT_USER}&password=${BOOT_PASS}"
  )"

  http_code="$(printf '%s\n' "${resp}" | tail -n1)"
  body="$(printf '%s\n' "${resp}" | sed '$d')"

  if [ "${http_code}" != "200" ]; then
    log "Failed to obtain admin token, HTTP ${http_code}"
    log "Response body: ${body}"
    exit 1
  fi

  TOKEN="$(printf '%s\n' "${body}" | jq -r '.access_token // empty')"

  if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
    log "access_token not found in response"
    log "Response body: ${body}"
    exit 1
  fi

  log "Got admin token"
}

# --------------------------------------------------------------------
# Обёртка над admin API
# --------------------------------------------------------------------
kc_api() {
  method="$1"; shift
  url_path="$1"; shift

  curl -sS \
    -X "${method}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    "${KC_URL}${url_path}" \
    "$@"
}

# --------------------------------------------------------------------
# Создать или получить permanent admin пользователя
# --------------------------------------------------------------------
ensure_perm_admin_user() {
  log "Checking if permanent admin '${PERM_USER}' exists..."

  user_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/users?username=${PERM_USER}&exact=true" || true)"
  user_id="$(printf '%s\n' "${user_json}" | jq -r '.[0].id // empty')"

  if [ -z "${user_id}" ]; then
    log "Creating permanent admin '${PERM_USER}'..."

    kc_api POST "/admin/realms/${MASTER_REALM}/users" \
      -d "$(jq -n --arg u "${PERM_USER}" '{username:$u, enabled:true}')"

    user_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/users?username=${PERM_USER}&exact=true" || true)"
    user_id="$(printf '%s\n' "${user_json}" | jq -r '.[0].id // empty')"
  fi

  if [ -z "${user_id}" ]; then
    log "Failed to resolve user id for '${PERM_USER}'"
    log "Last response: ${user_json}"
    exit 1
  fi

  PERM_USER_ID="${user_id}"
  log "Permanent admin '${PERM_USER}' has id=${PERM_USER_ID}"
}

set_perm_admin_password() {
  log "Setting password for '${PERM_USER}'..."

  kc_api PUT "/admin/realms/${MASTER_REALM}/users/${PERM_USER_ID}/reset-password" \
    -d "$(jq -n --arg p "${PERM_PASS}" \
            '{type:"password", value:$p, temporary:false}')" \
    >/dev/null 2>&1
}

# --------------------------------------------------------------------
# ВЫДАТЬ: realm-role 'admin' в master (глобальный админ)
# --------------------------------------------------------------------
grant_master_admin_role() {
  log "Granting realm role 'admin' in realm '${MASTER_REALM}' to '${PERM_USER}'..."

  admin_role_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/roles/admin" || true)"
  admin_role_name="$(printf '%s\n' "${admin_role_json}" | jq -r '.name // empty')"

  if [ -z "${admin_role_name}" ]; then
    log "Realm role 'admin' not found; cannot grant master admin rights"
    log "Response: ${admin_role_json}"
    exit 1
  fi

  kc_api POST "/admin/realms/${MASTER_REALM}/users/${PERM_USER_ID}/role-mappings/realm" \
    -d "$(printf '[%s]\n' "${admin_role_json}")" \
    >/dev/null 2>&1 || true

  log "Granted realm role 'admin' to '${PERM_USER}'"
}

# --------------------------------------------------------------------
# client-role realm-management:realm-admin (если есть)
# --------------------------------------------------------------------
grant_realm_admin_permissions_optional() {
  log "Best-effort: granting '${REALM_MGMT_CLIENT_ID}:${REALM_ADMIN_ROLE_NAME}' to '${PERM_USER}'..."

  # сначала пробуем по clientId
  clients_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/clients?clientId=${REALM_MGMT_CLIENT_ID}")"
  client_uuid="$(printf '%s\n' "${clients_json}" | jq -r '.[0].id // empty')"

  # fallback: вытаскиваем список и фильтруем по clientId
  if [ -z "${client_uuid}" ]; then
    clients_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/clients?first=0&max=1000")"
    client_uuid="$(
      printf '%s\n' "${clients_json}" \
        | jq -r '.[] | select(.clientId=="'"${REALM_MGMT_CLIENT_ID}"'") | .id' \
        | head -n1 || true
    )"
  fi

  if [ -z "${client_uuid}" ]; then
    log "WARN: client '${REALM_MGMT_CLIENT_ID}' not found in realm '${MASTER_REALM}', skipping client-role grant"
    return 0
  fi

  role_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/clients/${client_uuid}/roles/${REALM_ADMIN_ROLE_NAME}" || true)"
  role_name="$(printf '%s\n' "${role_json}" | jq -r '.name // empty')"

  if [ -z "${role_name}" ]; then
    log "WARN: role '${REALM_ADMIN_ROLE_NAME}' for client '${REALM_MGMT_CLIENT_ID}' not found, skipping"
    return 0
  fi

  kc_api POST "/admin/realms/${MASTER_REALM}/users/${PERM_USER_ID}/role-mappings/clients/${client_uuid}" \
    -d "$(printf '[%s]\n' "${role_json}")" \
    >/dev/null 2>&1 || true

  log "Best-effort client role '${REALM_MGMT_CLIENT_ID}:${REALM_ADMIN_ROLE_NAME}' granted to '${PERM_USER}'"
}

# --------------------------------------------------------------------
# Удаление bootstrap admin
# --------------------------------------------------------------------
delete_bootstrap_admin_if_needed() {
  if [ "${DELETE_BOOTSTRAP}" != "true" ] || [ "${BOOT_USER}" = "${PERM_USER}" ]; then
    log "Bootstrap admin deletion is disabled or same as permanent user, skipping"
    return
  fi

  log "Deleting bootstrap admin '${BOOT_USER}'..."

  boot_json="$(kc_api GET "/admin/realms/${MASTER_REALM}/users?username=${BOOT_USER}&exact=true" || true)"
  boot_id="$(printf '%s\n' "${boot_json}" | jq -r '.[0].id // empty')"

  if [ -n "${boot_id}" ]; then
    log "Found bootstrap admin id=${boot_id}, deleting..."
    kc_api DELETE "/admin/realms/${MASTER_REALM}/users/${boot_id}" >/dev/null 2>&1 || true
  else
    log "Bootstrap admin '${BOOT_USER}' not found, nothing to delete"
  fi
}

# --------------------------------------------------------------------
# Main
# --------------------------------------------------------------------
install_dependencies
wait_for_keycloak
get_admin_token
ensure_perm_admin_user
set_perm_admin_password
grant_master_admin_role
grant_realm_admin_permissions_optional
delete_bootstrap_admin_if_needed

log "Init completed successfully"