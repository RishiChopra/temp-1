# syntax=docker/dockerfile:1.7
# Multi-stage build: Gradle 9 + JDK 25 -> layered Spring Boot jar on a JRE 25 runtime.

FROM gradle:9.1.0-jdk25 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon bootJar -x test
RUN java -Djarmode=tools -jar build/libs/unpaids-event-extractor.jar extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:25-jre
ARG AI_AGENT_VERSION=3.7.10
WORKDIR /app

# Application Insights Java agent: logs, dependency traces (JDBC, Service Bus) and Micrometer metrics.
# It stays inert unless APPLICATIONINSIGHTS_CONNECTION_STRING is set.
ADD --chmod=644 https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${AI_AGENT_VERSION}/applicationinsights-agent-${AI_AGENT_VERSION}.jar /app/applicationinsights-agent.jar

RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001
ENV TZ=Europe/London \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom" \
    SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["java", "-javaagent:/app/applicationinsights-agent.jar", "org.springframework.boot.loader.launch.JarLauncher"]
