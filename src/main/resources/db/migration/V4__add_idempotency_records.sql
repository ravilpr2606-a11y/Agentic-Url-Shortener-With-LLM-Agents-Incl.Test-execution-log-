CREATE TABLE idempotency_records (
 key_value VARCHAR(200) PRIMARY KEY,
 short_url_id UUID NOT NULL REFERENCES short_urls(id) ON DELETE CASCADE,
 request_fingerprint VARCHAR(128) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
