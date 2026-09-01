# Threat Model

## Assets and trust boundaries

The protected assets are ledger correctness, wallet balances, provider credentials, personal identifiers, idempotency history, reconciliation evidence, and audit logs. Trust boundaries exist at the public API, provider webhook, administrator routes, database connection, and deployment environment.

## Threats and controls

| Threat | Current control | Production follow-up |
| --- | --- | --- |
| Duplicate client requests | Unique idempotency key plus request fingerprint | Share replay coordination across instances and return the winner after a unique-key race |
| Concurrent overspend | Sorted row locks and a post-lock available-balance check | Load test at target contention and monitor lock waits |
| Ledger tampering | Append-only service API and PostgreSQL update/delete triggers | Restrict direct database roles and stream tamper-evident audit records |
| Unbalanced posting | Service validation and deferred PostgreSQL zero-sum trigger | Keep invariant alerts on every release and migration |
| Webhook forgery | HMAC-SHA256 over timestamp and raw body, constant-time comparison | Rotate secrets, use a secret manager, and allow overlapping keys during rotation |
| Webhook replay | Five-minute timestamp window, unique provider event ID, payload hash | Alert on repeated IDs with changed hashes and rate-limit at the edge |
| Oversized webhook denial of service | 64 KiB application limit | Enforce a smaller request-body limit at the reverse proxy before buffering |
| Brute-force authentication | Per-address API rate limit | Replace Basic auth with OIDC, MFA for operators, edge throttling, and lockout telemetry |
| Cross-wallet access | UUID identifiers reduce guessing | Add tenant-scoped claims and enforce wallet ownership on every user route |
| Privileged misuse | Administrator role, required reconciliation reason, audit records | Use least-privilege roles, dual approval for adjustments, and immutable external audit storage |
| Secret disclosure | Environment-variable configuration and ignored `.env` | Use managed secrets, automatic rotation, and secret scanning in CI |
| Sensitive log leakage | API errors omit internal exception details | Add structured redaction tests and avoid logging raw provider payloads |
| Outbox backlog | Durable outbox records and invariant count | Add a broker relay, retry policy, dead-letter queue, and age-based alert |
| Multi-worker event race | Provider and ledger uniqueness constraints stop duplicate commits | Claim inbox rows atomically with `FOR UPDATE SKIP LOCKED` |

## Known MVP boundaries

The bundled authentication is suitable only for a local portfolio demonstration. The application has no user enrollment, KYC, AML, sanctions screening, payout approval, encryption-key management, regional privacy workflow, or regulated data-retention policy. Deploying it as a financial service requires those product and governance controls in addition to engineering hardening.
