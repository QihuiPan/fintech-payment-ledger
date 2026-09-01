# Demonstration Walkthrough

The web console provides the quickest path, while `api-examples.http` exposes the raw contract.

## 1. Create wallets

Start the API and console, then create two wallets with `GBP, EUR, USD`. Copy the first wallet ID into the active-wallet field and the second into the transfer form.

What to explain: creating a wallet provisions currency-specific user accounts. A balance is a projection over immutable entries, not a mutable bank statement.

## 2. Deposit and replay

Post GBP 100.00. With an HTTP client, repeat the exact request and `Idempotency-Key`; the transaction ID remains unchanged. Change the amount while reusing the key and observe a conflict.

What to explain: a durable request fingerprint distinguishes a network retry from a new business instruction.

## 3. Transfer under contention

Transfer GBP 25.00 to the second wallet. The concurrent-spend test launches two GBP 8.00 withdrawals against GBP 10.00 and proves only one can commit.

What to explain: accounts are locked in stable order and the available balance is checked after the lock.

## 4. Convert currency

Request a GBP/EUR quote and execute it before expiry. Inspect the transaction's entries: the user, FX clearing, and fee revenue accounts balance independently in GBP and EUR.

What to explain: the quote stores fixed-precision rate, amount, fee, expiry, and one-time consumption state.

## 5. Reverse without rewriting history

Use the last transaction ID in the reversal panel. The reversal produces compensating entries and links to the original transaction.

What to explain: the original record remains visible to audit and reconciliation workflows.

## 6. Reconcile and inspect health

Run the invariant check with the administrator credentials. Submit settlement rows to the reconciliation API with a reason. The result separates matches, amount mismatches, missing local deposits, and missing provider records.

What to explain: financial correctness needs both preventive constraints and detective controls.
