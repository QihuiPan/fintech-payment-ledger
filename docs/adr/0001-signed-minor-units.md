# ADR 0001: Store money as signed integer minor units

- Status: Accepted
- Date: 2026-09-01

## Context

Payment postings require exact addition, subtraction, equality checks, and stable serialization for idempotency. Binary floating-point types cannot represent many decimal values exactly.

## Decision

Store every entry and balance as a signed 64-bit integer in the currency's minor unit. Store the ISO currency code on every account and entry. Use fixed-precision decimal values only for FX rates, then round once when producing a destination minor-unit amount.

## Consequences

- Zero-sum validation is exact and inexpensive.
- API examples can be replayed without locale-dependent decimal parsing.
- Sign conveys posting direction and makes reversals a mechanical negation.
- Currency metadata must define the minor-unit scale; this MVP demonstrates two-decimal GBP, EUR, and USD.
- Overflow checks and a wider numeric representation would be required before supporting extremely large aggregate system accounts.
