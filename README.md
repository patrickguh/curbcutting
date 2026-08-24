# Allyscan

An automated web accessibility auditor. Submit a URL, and Allyscan crawls the
site, runs an [axe-core](https://github.com/dequelabs/axe-core) accessibility
scan (WCAG/AODA rules) against every page it finds, and produces a report —
including an AI-generated plain-language summary of the most important issues.

**Live:** https://allyscan.fly.dev/

Built as a 21-day self-paced learning project to go from "can write a script"
to "can explain every architectural decision in an interview" — then kept
growing past Day 21 as a real, deployed side project.

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
                    Markdown summary, stored on the job, rendered
                    to HTML at view time (commonmark)
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
- **Claude Haiku 4.5, not a frontier model, for the summary.** Summarizing a
  fixed-format violation list is a short, formulaic task — Haiku is a fraction
  of the cost of a reasoning-tier model with no meaningful quality loss here.
- **The AI summary is stored as Markdown, rendered to HTML at view time.**
  `ScanViewController` parses `job.interpretation` with commonmark
  (`escapeHtml(true)`) rather than storing pre-rendered HTML. Keeps the raw
  value portable (the JSON API returns plain Markdown) while the escape-on-
  render step closes off a reflected-XSS path — the summary text is built
  from content pulled off the scanned page, so it isn't fully trusted input.
- **Anonymous scans are actually deleted, not just hidden.** A scan run
  without an account is a one-time view — `AnonymousScanCleanup` purges
  anonymous jobs (cascading to their pages/violations) after 1 hour. It's not
  meant to be a permanent record of a site you didn't ask to keep.
- **"Keep me signed in" uses persistent remember-me tokens, not a longer
  session cookie.** Backed by Spring Security's `PersistentTokenRepository`
  (`JdbcTokenRepositoryImpl`), so staying signed in survives a browser
  restart without just extending the session's own lifetime.

## Running locally

Requirements: Java 17, Docker (for Postgres), and optionally an
`ANTHROPIC_API_KEY` if you want AI summaries (billed per token — the app
works fully without it, just without the "Summary" section on the report
page).

```bash
docker run -d --name allyscan-db -p 5432:5432 \
  -e POSTGRES_DB=allyscan -e POSTGRES_PASSWORD=devpassword postgres:16

./mvnw spring-boot:run
```

Then open http://localhost:8080/ — submit a URL, watch it move from
`QUEUED` → `RUNNING` → `DONE`, and click through to the report for the
violation breakdown. Sign up for a free account to keep a private scan
history; scanning without one is a one-time anonymous view.

To enable AI summaries, set `ANTHROPIC_API_KEY` before starting the app.

## Pages

| Path                | Description                                          |
|----------------------|------------------------------------------------------|
| `/`                  | Scan list + submit form (signed-in users see their own history) |
| `/scans/{id}/report` | Violation breakdown by page, plus the AI summary      |
| `/how-it-works`      | Plain-language explanation of the scanning pipeline   |
| `/login`, `/register` | Auth forms                                           |
| `/forgot-password`, `/reset-password` | Emailed reset-code flow             |
| `/settings`          | Appearance (theme), account, log out                  |
| `/account/scans`     | Full scan history for a signed-in user                |

## API

| Method | Path                     | Auth        | Description                          |
|--------|--------------------------|-------------|---------------------------------------|
| POST   | `/scans?url=...`         | required    | Enqueue a scan (JSON)                 |
| GET    | `/scans`                 | —           | List scan jobs (JSON)                 |
| GET    | `/scans/{id}`            | —           | Get one scan job, incl. Markdown interpretation |
| GET    | `/scans/{id}/violations` | —           | Flat list of violations for a job     |
| POST   | `/guest-scan`            | —           | Enqueue an anonymous one-time-view scan (HTML form) |

## Deployment

Containerized with a multi-stage Dockerfile (Maven build stage, then a
Playwright-preinstalled runtime image so headless Chromium is available
without extra setup). Deployed to Fly.io as the `allyscan` app, backed by a
free-tier Neon Postgres instance — local dev uses a Docker Postgres instead,
switched via `SPRING_DATASOURCE_*` Fly secrets overriding
`application.properties` (Spring's env-var precedence). Scaled to a single
machine deliberately: two machines both trying to run Flyway migrations
against a brand-new database on first boot is a real race, not a theoretical
one.

`main` pushes trigger `.github/workflows/fly-deploy.yml` via `FLY_API_TOKEN`.

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
  architecture write-up, first working deployment to Fly.io + Neon.

## Since Day 21

The curriculum was the floor, not the ceiling — kept building after "done":

- Accounts (email/password, BCrypt), scans scoped to their owner, and a
  forgot-password flow with emailed one-time reset codes.
- Anonymous scanning as a genuine one-time view (not just hidden — actually
  purged after an hour) instead of requiring an account for every scan.
- A full UI redesign: persistent sidebar navigation, animated status badges,
  violation categorization, a homepage rewrite with a "How it works" page,
  dark/light theming, and a consolidated single-card settings page.
- Persistent "keep me signed in" via remember-me tokens.
- Rebranded from Curbcutting to Allyscan — new package, database, Fly app,
  and branding throughout.
- Switched the summary model from Opus 5 to Haiku 4.5 (cost-appropriate for
  a short summarization task) and fixed the summary rendering as real HTML
  instead of literal Markdown syntax.
