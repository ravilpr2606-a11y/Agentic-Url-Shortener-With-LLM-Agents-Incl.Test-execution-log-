CREATE TABLE short_urls (
                            id UUID PRIMARY KEY,
                            short_code VARCHAR(7) NOT NULL UNIQUE,
                            original_url VARCHAR(2048) NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE click_events (
                              id UUID PRIMARY KEY,
                              short_url_id UUID NOT NULL,
                              clicked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              referrer VARCHAR(2048),
                              user_agent VARCHAR(1024),

                              CONSTRAINT fk_click_events_short_url
                                  FOREIGN KEY (short_url_id)
                                      REFERENCES short_urls(id)
                                      ON DELETE CASCADE
);

CREATE INDEX idx_click_events_short_url_id
    ON click_events(short_url_id);

CREATE INDEX idx_click_events_clicked_at
    ON click_events(clicked_at);