# Changelog

All notable changes to this project are documented in this file. The format follows Keep a Changelog, and the project uses semantic versioning.

Every behavior, API, schema, security, operations, or user-interface change must update this file in the same commit.

## Unreleased

### Changed

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
