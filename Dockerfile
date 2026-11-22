# syntax=docker/dockerfile:1

# ---------- Stage 1: build (Maven + JDK 21) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Кэшируем зависимости отдельным слоем
COPY pom.xml .
COPY smartbot-common/pom.xml smartbot-common/
COPY smartbot-domain/pom.xml smartbot-domain/
COPY smartbot-application-api/pom.xml smartbot-application-api/
COPY smartbot-application-impl/pom.xml smartbot-application-impl/
COPY smartbot-adapter-telegram/pom.xml smartbot-adapter-telegram/
COPY smartbot-adapter-llm/pom.xml smartbot-adapter-llm/
COPY smartbot-adapter-search/pom.xml smartbot-adapter-search/
COPY smartbot-adapter-persistence/pom.xml smartbot-adapter-persistence/
COPY smartbot-infrastructure/pom.xml smartbot-infrastructure/
COPY smartbot-app/pom.xml smartbot-app/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline -DskipTests || true

COPY smartbot-common/src smartbot-common/src
COPY smartbot-domain/src smartbot-domain/src
COPY smartbot-application-api/src smartbot-application-api/src
COPY smartbot-application-impl/src smartbot-application-impl/src
COPY smartbot-adapter-telegram/src smartbot-adapter-telegram/src
COPY smartbot-adapter-llm/src smartbot-adapter-llm/src
COPY smartbot-adapter-search/src smartbot-adapter-search/src
COPY smartbot-adapter-persistence/src smartbot-adapter-persistence/src
COPY smartbot-infrastructure/src smartbot-infrastructure/src
COPY smartbot-app/src smartbot-app/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---------- Stage 2: runtime (JRE 21) ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system smartbot && useradd --system --gid smartbot smartbot \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/smartbot-app/target/smartbot-exec.jar app.jar

# ВАЖНО (Phase 0/7): корневой сертификат Минцифры РФ для TLS к GigaChat
# добавляется в truststore на этапе подготовки образа/хоста после проверки
# инфраструктуры. См. docs/architecture/gigachat-integration.md.

USER smartbot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
