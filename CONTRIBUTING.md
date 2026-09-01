# Contributing

## Development workflow

1. Create a focused branch and describe the invariant or behavior being changed.
2. Add or update automated tests before changing ledger behavior.
3. Run `./mvnw test` and `cd web && pnpm build`.
4. Update `CHANGELOG.md` in the same commit. Every API, schema, security, operations, dependency, documentation, or interface change needs an entry under `Unreleased`.
5. Keep code comments, interface labels, annotations, commits, and documentation in English.

## Ledger changes

Any new posting flow must document its entries, prove a zero sum per currency, define retry semantics, and state how it can be reversed. Never add an update or delete path for posted ledger entries.

## Commit style

Use concise imperative messages, for example:

```text
feat: add payout posting rule
fix: serialize concurrent balance updates
docs: explain reconciliation exceptions
```
