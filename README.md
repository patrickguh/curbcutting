# Curbcutting

An automated web accessibility auditor. Submit a URL, and Curbcutting crawls the
site, runs an [axe-core](https://github.com/dequelabs/axe-core) accessibility
scan (WCAG/AODA rules) against every page it finds, and produces a report —
including an AI-generated plain-language summary of the most important issues.

Built as a 21-day self-paced learning project to go from "can write a script"
to "can explain every architectural decision in an interview."

## Architecture

```
Browser / curl
      |
      v
Spring MVC (ScanController = JSON API, ScanViewController = HTML UI)
      |
      v
ScanService  ---enqueue--->  scan_jobs (Postgres, status=QUEUED)
                                    ^
                                    | SELECT ... FOR UPDATE SKIP LOCKED
                                    |
                              ScanWorker (@Scheduled, polls every 2s)
                                    |
                    +---------------+----------------+
                    |                                |
              SiteCrawler (jsoup)              Playwright + axe-core
           same-host BFS, capped at            headless Chromium scan
           20 pages                            per discovered page
                    |                                |
                    +---------------+----------------+
                                    v
                          pages / violations tables
                                    v
                        ViolationInterpreter (Claude API)
                     plain-language summary, stored on the job
```

Key decisions and why:

- **Async job queue over synchronous scanning.** A full-site scan can take
  minutes; a request/response cycle shouldn't block on that. `ScanController`
  just enqueues a row and returns immediately; `ScanWorker` does the work on a
  schedule.
- **`SELECT ... FOR UPDATE SKIP LOCKED` for job claiming.** If more than one
  worker instance is ever running, this guarantees each queued job is claimed
  by exactly one worker, without a separate distributed lock.
- **`ScanWorker` is a separate class from `ScanService`.** Spring's
  `@Transactional` is implemented via a proxy; calling an `@Transactional`
  method from *within the same class* bypasses the proxy and the transaction
  never starts. Keeping the worker's orchestration outside the service avoids
  that trap.
- **Retry + reaper, not just try/once.** `attempts` tracks retries (max 3
  before `FAILED`); `reapStaleRunningJobs` recovers jobs stuck in `RUNNING`
  for more than 5 minutes, which happens if a worker crashes mid-scan.
- **Interpretation failure never fails the scan.** If the Claude API call
  errors (no key, rate limit, network), the scan is still marked `DONE` with
  its violations — the summary is a value-add, not a dependency.

## Running locally

Requirements: Java 17, Docker (for Postgres), and optionally an
`ANTHROPIC_API_KEY` if you want AI summaries (billed per token — the app
works fully without it, just without the "Summary" section on the report
page).

```bash
docker run -d --name curbcutting-db -p 5432:5432 \
  -e POSTGRES_DB=curbcutting -e POSTGRES_PASSWORD=devpassword postgres:16

./mvnw spring-boot:run
```

Then open http://localhost:8080/ — submit a URL, watch it move from
`QUEUED` → `RUNNING` → `DONE` (the list page doesn't auto-refresh; reload to
see updates), and click "View report" for the violation breakdown.

To enable AI summaries, set `ANTHROPIC_API_KEY` before starting the app.

## API

| Method | Path                     | Description                          |
|--------|--------------------------|---------------------------------------|
| POST   | `/scans?url=...`         | Enqueue a scan (JSON)                 |
| GET    | `/scans`                 | List all scan jobs (JSON)             |
| GET    | `/scans/{id}`            | Get one scan job, incl. interpretation |
| GET    | `/scans/{id}/violations` | Flat list of violations for a job     |
| GET    | `/`                      | HTML: scan list + submit form         |
| GET    | `/scans/{id}/report`     | HTML: violations by page + summary    |

## Deployment

Containerized with a multi-stage Dockerfile (Maven build stage, then a
Playwright-preinstalled runtime image so headless Chromium is available
without extra setup). Deployed to Fly.io; `main` pushes trigger
`.github/workflows/fly-deploy.yml` via `FLY_API_TOKEN`.

## Curriculum log

- **Days 1–2:** `/scan?url=...` synchronous endpoint, raw axe-core JSON.
- **Days 3–5:** Postgres + Flyway migrations, JPA entities, persisted scans.
- **Day 6:** Converted to an async job queue polled by a `@Scheduled` worker.
- **Days 7–10:** Concurrent-safe job claiming (`SKIP LOCKED`), retry/reaper
  logic for crashed workers, jsoup-based same-host crawler.
- **Days 13–15:** Claude API interpretation layer — violations are summarized
  into a plain-language, prioritized report.
- **Days 16–18:** Thymeleaf report UI (scan list + submit form, per-scan
  violation report grouped by page).
- **Days 19–21:** End-to-end verification against real Postgres, README and
  architecture write-up, deployment check.
