# Observability platform integration

The application image includes OpenTelemetry Java agent 2.14.0. The opt-in Compose overlay enables authenticated OTLP traces and logs, including JDBC/HTTP dependency spans and trace-correlated log records. Resource attributes include service name, version, environment, region and tenant.

Apply the platform service specification first. Set `OBSERVE_OTLP_ENDPOINT` to its authenticated gateway and `OBSERVE_OTLP_HEADERS` to `Authorization=Bearer%20TOKEN`, using a short-lived credential scoped to `team-payments`. Combine the original Compose file with `compose.observability.yaml`. Keep all secrets in the local ignored environment.

The canonical HTTP SLO counters and exact 300ms latency histogram are exposed through `/actuator/prometheus`. Configure an authenticated, private scrape target from the `team-payments` Prometheus server; never expose management endpoints publicly. HTTP 4xx, health and actuator requests are excluded. No user IDs, raw URLs or request IDs become metric labels.

The agent is disabled unless the overlay is selected, preserving existing deployments. In Kubernetes, use the platform's projected-service-account-token Collector sidecar instead of long-lived exporter credentials. Faults should target an isolated database/proxy; this change does not introduce production fault endpoints.
