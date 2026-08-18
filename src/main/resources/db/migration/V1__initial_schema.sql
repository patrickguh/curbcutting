CREATE TABLE scan_jobs (
                           id            UUID PRIMARY KEY,
                           root_url      TEXT        NOT NULL,
                           status        TEXT        NOT NULL,
                           created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                           started_at    TIMESTAMPTZ,
                           finished_at   TIMESTAMPTZ,
                           error_message TEXT
);

CREATE TABLE pages (
                       id          UUID PRIMARY KEY,
                       scan_job_id UUID NOT NULL REFERENCES scan_jobs(id) ON DELETE CASCADE,
                       url         TEXT NOT NULL,
                       scanned_at  TIMESTAMPTZ
);

CREATE TABLE violations (
                            id           UUID PRIMARY KEY,
                            page_id      UUID NOT NULL REFERENCES pages(id) ON DELETE CASCADE,
                            rule_id      TEXT NOT NULL,
                            impact       TEXT,
                            help_text    TEXT,
                            help_url     TEXT,
                            target       TEXT,
                            html_snippet TEXT
);

CREATE INDEX idx_pages_scan_job    ON pages(scan_job_id);
CREATE INDEX idx_violations_page   ON violations(page_id);
CREATE INDEX idx_scan_jobs_status  ON scan_jobs(status, created_at);