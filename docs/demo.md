# Demonstration Walkthrough

The web console provides the quickest path, while `api-examples.http` exposes the raw contract.

## 1. Create a ready-to-use workspace

Start the Docker Compose stack, open the console, and select **Create demo workspace**. The console creates an owner wallet, a recipient wallet, and a GBP 100.00 deposit. It selects the owner, fills the recipient wallet ID, and shows the first transaction and statement entry.

What to explain: creating a wallet provisions currency-specific user accounts. A balance is a projection over immutable entries, not a mutable bank statement.

## 2. Deposit and replay

With an HTTP client, repeat the seeded deposit using the exact request and `Idempotency-Key`; the transaction ID remains unchanged. Change the amount while reusing the key and observe a conflict.

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

## 7. Verify resource isolation

Change the API user credentials to a separately configured subject and try to open the first wallet. The API rejects the request because wallet and transaction access is bound to the subject that created them. Administrators retain cross-wallet inspection access.

What to explain: UUIDs are identifiers, not authorization. Ownership is enforced independently on every user-facing resource route.
