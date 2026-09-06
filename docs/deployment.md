# Deployment Guide

## Choose an operating mode

| Mode | Command | Intended use |
| --- | --- | --- |
| Local demo | `docker compose up --build --wait` | Evaluation on one machine with documented demo credentials |
| Prebuilt image | `docker compose pull && docker compose up --no-build --wait` | Evaluation without a local Java or Node.js toolchain |
| Hardened baseline | `docker compose -f compose.yaml -f compose.production.yaml up -d --wait` | Private self-hosting behind TLS after secrets are configured |

The default port is bound to `127.0.0.1`. Set `APP_PORT` to change the host port. Do not bind the service publicly while using the demo profile.

## Production configuration

Copy `.env.example` to `.env` and replace every placeholder with an independently generated value. The PostgreSQL profile refuses to start when the database password, API passwords, or webhook secret are too short or still use a documented demo or placeholder value.

Required settings:

| Variable | Purpose |
| --- | --- |
| `DATABASE_USERNAME` | PostgreSQL login used by the application |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `LEDGER_USER_NAME` | Wallet API subject |
| `LEDGER_USER_PASSWORD` | Wallet API password, at least 12 characters |
| `LEDGER_ADMIN_NAME` | Operations administrator subject |
| `LEDGER_ADMIN_PASSWORD` | Administrator password, at least 12 characters |
| `PROVIDER_WEBHOOK_SECRET` | HMAC secret, at least 24 characters |
| `DATABASE_POOL_SIZE` | Maximum application-side PostgreSQL connections; defaults to 10 |
| `APP_PORT` | Loopback host port; defaults to 8080 |

Start the hardened configuration:

```bash
docker compose -f compose.yaml -f compose.production.yaml up -d --wait
```

Confirm readiness:

```bash
curl --fail http://localhost:8080/actuator/health/readiness
```

## Database operations

PostgreSQL is the durable source of truth. The named `ledger-data` volume survives container replacement. Back up the database before application upgrades and rehearse restoration separately from the live volume.

Flyway applies migrations at startup. Never edit a migration that has reached a shared environment; add a new versioned migration instead. PostgreSQL-specific migrations enforce append-only ledger records and deferred per-currency zero-sum checks.

The application uses HikariCP with a default maximum of ten connections. Size the pool together with PostgreSQL's connection budget and any proxy or pooler. Do not connect with a PostgreSQL superuser.

## Reverse proxy and TLS

HTTP Basic credentials are only acceptable over TLS. Place the service behind a reverse proxy that:

- terminates TLS and redirects plaintext HTTP;
- limits request bodies before buffering them;
- applies edge rate limits to the API and provider webhook;
- records a request identifier without logging credentials or raw webhook bodies;
- restricts administrator and metrics routes to an operations network where possible.

Health endpoints are public. Metrics and all `/api/admin/**` routes require the administrator role.

## Identity and resource ownership

Wallets and initiated transactions are bound to the authenticated API subject. A normal user can operate only its own wallets and transactions; an administrator can inspect all resources. The bundled in-memory user directory is appropriate for a small private deployment or demonstration. Replace it with OIDC or another external identity provider before supporting independent end users.

## Image releases

The `Container` workflow publishes multi-architecture images to `ghcr.io/qihuipan/fintech-payment-ledger`. The `latest` tag follows `main`; semantic-version tags are published when a matching Git tag is pushed. Prefer a versioned tag for repeatable deployments.

## Upgrade and rollback

1. Back up PostgreSQL and verify the backup artifact.
2. Read `CHANGELOG.md` for schema and configuration changes.
3. Pull the target image and start it in a staging environment against a restored backup.
4. Verify readiness, a wallet read, a money-movement idempotency replay, and administrator invariants.
5. Deploy the versioned image.

Application rollback does not automatically reverse Flyway migrations. Confirm that the previous binary is compatible with the migrated schema before rolling back.
