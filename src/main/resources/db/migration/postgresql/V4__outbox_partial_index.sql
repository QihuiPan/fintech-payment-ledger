CREATE INDEX ix_outbox_unpublished_partial
    ON outbox_events(created_at, id)
    WHERE published_at IS NULL;
