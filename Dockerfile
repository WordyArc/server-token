# syntax=docker/dockerfile:1
# check=error=true

# Build
########################################################
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
RUN chmod +x gradlew

COPY build.gradle.kts settings.gradle.kts ./

COPY . .

RUN --mount=type=cache,id=gradle-cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /workspace/extracted


# Runtime
########################################################
FROM eclipse-temurin:25-jre-noble AS runtime

WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/application/ ./

VOLUME /tmp

LABEL org.opencontainers.image.title="server-token" \
      org.opencontainers.image.description="Server Token Spring Boot service" \
      org.opencontainers.image.vendor="Viktor Kogai"

ENV TZ=UTC \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
