CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    kyc_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    wallet_id UUID REFERENCES wallets(id),
    code VARCHAR(160) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    posted_balance_minor BIGINT NOT NULL DEFAULT 0,
    available_balance_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT user_balance_non_negative CHECK (
        account_type <> 'USER' OR available_balance_minor >= 0
    ),
    CONSTRAINT wallet_account_shape CHECK (
        (account_type = 'USER' AND wallet_id IS NOT NULL)
        OR (account_type <> 'USER' AND wallet_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_wallet_currency_user_account
    ON accounts(wallet_id, currency, account_type);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    transaction_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reference VARCHAR(160) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    reverses_transaction_id UUID UNIQUE REFERENCES ledger_transactions(id),
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT posted_status_only CHECK (status = 'POSTED')
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT non_zero_entry CHECK (amount_minor <> 0)
);

CREATE INDEX ix_ledger_entries_account_created
    ON ledger_entries(account_id, created_at, id);
CREATE INDEX ix_ledger_entries_transaction
    ON ledger_entries(transaction_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_outbox_unpublished
    ON outbox_events(created_at, id);

CREATE TABLE fx_quotes (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    base_amount_minor BIGINT NOT NULL,
    quote_amount_minor BIGINT NOT NULL,
    fee_minor BIGINT NOT NULL,
    rate DECIMAL(20, 10) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT positive_quote_amounts CHECK (
        base_amount_minor > 0 AND quote_amount_minor > 0 AND fee_minor >= 0
    ),
    CONSTRAINT distinct_quote_currencies CHECK (base_currency <> quote_currency)
);

CREATE TABLE provider_events (
    provider_event_id VARCHAR(160) PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    wallet_id UUID,
    currency VARCHAR(3),
    amount_minor BIGINT,
    provider_reference VARCHAR(160),
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(500),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_provider_events_status
    ON provider_events(status, received_at);

CREATE TABLE reconciliation_exceptions (
    id UUID PRIMARY KEY,
    business_date DATE NOT NULL,
    category VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(160) NOT NULL,
    currency VARCHAR(3),
    provider_amount_minor BIGINT,
    local_amount_minor BIGINT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(business_date, category, provider_reference)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor VARCHAR(160) NOT NULL,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(160) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_audit_logs_entity
    ON audit_logs(entity_type, entity_id, created_at);
