# Qihui Pan - FinTech Payment Ledger

An engineering portfolio about reliable money movement, auditable accounting, and a usable self-hosted application.

- [Download the four-page portfolio PDF](QihuiPan_FinTech_Ledger_Portfolio.pdf)
- [Open the standalone portfolio HTML](index.html) after downloading this folder, or serve this folder with any static HTTP server.
- [Explore the v0.3.0 release](https://github.com/QihuiPan/fintech-payment-ledger/releases/tag/v0.3.0)

## Project summary

FinTech Payment Ledger is an independent Java, Spring Boot, PostgreSQL, React, and TypeScript project. It supports wallets, deposits, transfers, fixed-precision FX quotes, reversals, signed provider webhooks, reconciliation, and running statements. An append-only double-entry model keeps each currency balanced; persisted idempotency and account row locks address duplicate requests and concurrent spending.

The project includes an English operations console, a one-click demo workspace, Docker Compose, multi-architecture container publishing, a complete OpenAPI contract, and automated verification. Later main-branch work adds optional OpenTelemetry export, HTTP and worker signals, correlated request logs, and Prometheus exemplars.

## What the portfolio demonstrates

| Engineering area | Implementation evidence |
| --- | --- |
| Accounting correctness | Signed integer minor units, per-currency balancing, linked reversals, and PostgreSQL append-only guards |
| Payment reliability | Persisted idempotency fingerprints, ordered account locks, signed event identity, and reconciliation exceptions |
| Access control | Authenticated subject ownership and separate administrator permissions |
| Product delivery | React console, seeded demonstration, durable PostgreSQL storage, and one-command startup |
| Operational visibility | Invariant checks, audit records, health endpoints, and optional trace-linked telemetry |

## Evidence boundary

The portfolio's 12-test count, four successful CI jobs, and two published image architectures refer to the verified v0.3.0 baseline at commit `7e5b2f9`. The 1,000 randomized examples are iterations within one ledger-math test, not 1,000 separate tests. Core integration tests use H2; PostgreSQL CI verifies migration and application readiness. Later telemetry work is identified separately at `c0edf44`. No production throughput or availability benchmark is claimed.

Reviewed on 2026-09-11. The PDF and page include direct links to the release, implementation, tests, CI runs, deployment guide, and documented scope boundaries.

## Maintaining these artifacts

Keep page text, PDF claims, and source evidence aligned. Update the main `CHANGELOG.md` for every portfolio change. The authoring source for the PDF is `build_portfolio.py`; it uses ReportLab and Segoe UI/Consolas fonts on Windows. Run it from this directory, then render and inspect all four pages before replacing the published PDF.
