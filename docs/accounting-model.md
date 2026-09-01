# Accounting Model

## Conventions

- Amounts are signed `BIGINT` minor units. `1000` GBP means GBP 10.00.
- A positive user-account entry increases the user's asset balance; a negative entry decreases it.
- System accounts use the opposite sign needed to keep the ledger balanced.
- Every transaction balances to zero independently in each currency.
- A posted transaction and its entries are immutable.

## Core postings

### Deposit

Provider confirms GBP 100.00 for a wallet.

| Account | Currency | Amount minor |
| --- | --- | ---: |
| User wallet | GBP | 10000 |
| Platform cash | GBP | -10000 |
| **Sum** | **GBP** | **0** |

### Transfer

Sender moves GBP 25.00 to recipient.

| Account | Currency | Amount minor |
| --- | --- | ---: |
| Sender wallet | GBP | -2500 |
| Recipient wallet | GBP | 2500 |
| **Sum** | **GBP** | **0** |

### FX conversion

The user sells GBP 100.00, pays a GBP 0.20 fee, and receives EUR 117.00.

| Account | Currency | Amount minor |
| --- | --- | ---: |
| User GBP wallet | GBP | -10020 |
| FX clearing | GBP | 10000 |
| Fee revenue | GBP | 20 |
| User EUR wallet | EUR | 11700 |
| FX clearing | EUR | -11700 |
| **Sum** | **GBP / EUR** | **0 / 0** |

Rates are stored with ten decimal places. The quoted amount rounds once to the destination currency's minor unit. A quote has a short expiry and can be consumed only once.

### Reversal

A reversal copies every original entry with its sign inverted and links back to the original transaction. The original remains posted. This preserves evidence and ensures the net effect of the pair is zero.

## Balance projection

The account row stores posted and available balance snapshots for fast reads. They are not independent records of truth: the entry stream is authoritative. Every posting inserts entries and updates snapshots in the same database transaction. The invariant endpoint detects any drift between allowed states.

## Idempotency contract

The client supplies an `Idempotency-Key` for every money-moving request. The service hashes the operation name and normalized inputs.

| Retry | Result |
| --- | --- |
| Same key, same fingerprint | Return the original transaction |
| Same key, different fingerprint | Reject with `IDEMPOTENCY_KEY_REUSED` |
| New key | Evaluate and post a new transaction |

The unique database constraint prevents two committed transactions from sharing a key.
