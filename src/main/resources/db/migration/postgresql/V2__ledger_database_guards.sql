CREATE OR REPLACE FUNCTION reject_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Posted ledger records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_are_append_only
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER posted_transactions_are_append_only
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW
WHEN (OLD.status = 'POSTED')
EXECUTE FUNCTION reject_ledger_mutation();

CREATE OR REPLACE FUNCTION assert_posted_transaction_complete()
RETURNS TRIGGER AS $$
DECLARE
    entry_count BIGINT;
    unbalanced_currency VARCHAR(3);
    unbalanced_amount BIGINT;
BEGIN
    IF NEW.status <> 'POSTED' THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*)
      INTO entry_count
      FROM ledger_entries
     WHERE transaction_id = NEW.id;

    IF entry_count < 2 THEN
        RAISE EXCEPTION 'Posted ledger transaction % requires at least two entries', NEW.id;
    END IF;

    SELECT currency, SUM(amount_minor)
      INTO unbalanced_currency, unbalanced_amount
      FROM ledger_entries
     WHERE transaction_id = NEW.id
     GROUP BY currency
    HAVING SUM(amount_minor) <> 0
     LIMIT 1;

    IF unbalanced_currency IS NOT NULL THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced for % by % minor units',
            NEW.id, unbalanced_currency, unbalanced_amount;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER posted_transaction_completeness_guard
AFTER INSERT ON ledger_transactions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_posted_transaction_complete();

CREATE OR REPLACE FUNCTION assert_transaction_balanced_by_currency()
RETURNS TRIGGER AS $$
DECLARE
    unbalanced_currency VARCHAR(3);
    unbalanced_amount BIGINT;
BEGIN
    SELECT currency, SUM(amount_minor)
      INTO unbalanced_currency, unbalanced_amount
      FROM ledger_entries
     WHERE transaction_id = NEW.transaction_id
     GROUP BY currency
    HAVING SUM(amount_minor) <> 0
     LIMIT 1;

    IF unbalanced_currency IS NOT NULL THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced for % by % minor units',
            NEW.transaction_id, unbalanced_currency, unbalanced_amount;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_transaction_balance_guard
AFTER INSERT ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_transaction_balanced_by_currency();
