# Changelog

All notable changes to this project are documented in this file. The format follows Keep a Changelog, and the project uses semantic versioning.

Every behavior, API, schema, security, operations, or user-interface change must update this file in the same commit.

## Unreleased

## 0.3.0 - 2026-09-07

### Added

- Added authenticated-subject ownership for wallets and initiated transactions, administrator overrides, legacy-resource assignment, and service and HTTP integration coverage.
- Added a one-click demo workspace that creates two wallets, funds the owner, fills the transfer destination, and displays signed ledger entries.
- Added a production Compose overlay, application and database health checks, configurable PostgreSQL connection pooling, and a hardened read-only application container.
- Added a complete OpenAPI 3.1 contract, deployment guide, security policy, PostgreSQL startup and container build verification, dependency update configuration, and multi-architecture GitHub Container Registry publishing.
- Added QEMU-backed container publishing for AMD64 and ARM64 hosts.
- Added Flyway indexes for wallet ownership, foreign keys, FX lookup, and the unpublished outbox queue.

### Changed

- Raised console text and control sizes, made administrator credentials configurable in the interface, and improved non-JSON API error handling.
- Restricted the H2 console to an explicit local profile, bound the demo stack to loopback, and made the PostgreSQL profile reject documented, placeholder, or undersized secrets.
- Restricted Actuator metrics to administrators, applied bounded rate limiting to provider webhooks, and documented TLS, backup, identity, and rollback requirements.
- Promoted the application version to `0.3.0` and made the default Compose stack compatible with a prebuilt GHCR image.
- Changed repository visibility to public on 2026-09-04 at the owner's request after reviewing tracked files and commit history for unintended disclosure.
- Upgraded GitHub Actions to maintained Node.js 24-based releases after the first successful CI run reported deprecation annotations.

## 0.2.0 - 2026-09-02

### Added

- Added an English React operations console for wallets, deposits, transfers, FX, reversals, statements, and invariant checks.
- Added Docker Compose, a multi-stage container image, GitHub Actions, architecture documentation, a threat model, API examples, and a narrated demo.
- Added contribution and pull-request checks that require a changelog entry.

### Changed

- Promoted the application version to `0.2.0`.
- Isolated each integration-test database to eliminate order-dependent results.

## 0.1.0 - 2026-09-01

### Added

- Added an append-only, double-entry ledger with per-currency balancing and non-negative user balances.
- Added wallet creation, deposits, transfers, fixed-precision FX quotes and conversions, reversals, and running statements.
- Added request-fingerprint idempotency, signed provider webhooks, duplicate delivery handling, reconciliation, audit logs, outbox storage, metrics, and rate limiting.
- Added PostgreSQL mutation guards and eight automated tests, including concurrent-spend and randomized-invariant coverage.

## Observability integration - 2026-09-07

- Added opt-in authenticated OpenTelemetry traces/logs and bounded SLO metrics with exact latency buckets.
- Included a pinned Java agent and platform onboarding instructions.
- Validation is recorded by the integration branch CI; no production diagnosis or throughput claims are made.

- Emit bounded request-completion logs inside the active trace context for metrics-to-trace-to-log navigation.
