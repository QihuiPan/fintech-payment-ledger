ALTER TABLE wallets
    ADD COLUMN owner_subject VARCHAR(160) NOT NULL DEFAULT 'legacy';

ALTER TABLE ledger_transactions
    ADD COLUMN initiated_by VARCHAR(160) NOT NULL DEFAULT 'legacy';

CREATE INDEX ix_wallets_owner_created
    ON wallets(owner_subject, created_at, id);

CREATE INDEX ix_wallets_user
    ON wallets(user_id);

CREATE INDEX ix_fx_quotes_wallet_created
    ON fx_quotes(wallet_id, created_at, id);

CREATE INDEX ix_outbox_publish_queue
    ON outbox_events(published_at, created_at, id);
