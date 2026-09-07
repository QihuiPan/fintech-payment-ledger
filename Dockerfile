FROM node:24-alpine AS web-build
WORKDIR /web
RUN corepack enable
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm build

FROM eclipse-temurin:25-jdk-alpine AS api-build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode -DskipTests dependency:go-offline
COPY src/ src/
COPY --from=web-build /web/dist/ src/main/resources/static/
RUN ./mvnw --batch-mode -DskipTests package

RUN ./mvnw -B dependency:copy -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.14.0 -DoutputDirectory=/otel

FROM eclipse-temurin:25-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/QihuiPan/fintech-payment-ledger" \
      org.opencontainers.image.description="Append-only double-entry payment ledger" \
      org.opencontainers.image.licenses="MIT"
RUN addgroup -S ledger && adduser -S ledger -G ledger
WORKDIR /app
COPY --from=api-build /otel/opentelemetry-javaagent-2.14.0.jar /app/opentelemetry-javaagent.jar
COPY --from=api-build /workspace/target/fintech-payment-ledger-*.jar app.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
