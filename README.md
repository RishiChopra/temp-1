# Unpaids event extractor

Publishes an `UnpaidRecorded` event for every unpaid in the two loan books, **PL** (personal loans) and
**CL** (commercial loans), to the Azure Service Bus topic `unpaids-events`. Unpaids come from the daily
**batch** unpaids run (`UNPAID_DETAILS`) and from **manual** entries that staff key in and approve (posted
as transactions). Runs as a scheduled **Azure Container Apps Job**.

Built with Java 25, Spring Boot 4.1 and Gradle 9 (Groovy DSL).

### Vocabulary

| Term | Meaning |
|---|---|
| **Loan book** | PL or CL. Each has its own monolith database. |
| **Feed** | One place unpaids come from: `pl-batch`, `cl-batch`, `pl-manual`, `cl-manual`. Configured in `application.yml`. |
| **Unpaids run** | The monolith's daily batch job: 133 for PL, 91 for CL, recorded in `I_SCHEDULED_JOBS`. |
| **Daily feed** | One feed for one business date, the unit of work (table `UNPAID_DAILY_FEED`, main DB). |
| **Ledger** | Per loan book, every unpaid the extractor has staged and whether it was sent (`UNPAID_EVENT_LEDGER`). |

**The monolith (Java 8 / Spring 3.2) needs no code changes.** Both batch runs already record their end
time in `I_SCHEDULED_JOBS` (main DB), and the extractor polls that table. The extractor's own tables
live in a separate `UNPAIDS_EXTRACTOR` schema in each database.

---

## How it works

### Schedule (weekdays, Europe/London)

Batch unpaids are uploaded around 10:00; jobs 133 and 91 start around 10:00 and usually finish by
10:30. Transactions arrive around 15:00. The job only runs when there is likely to be work:

| ACA Job | Cron (UTC) | London time | Why |
|---|---|---|---|
| `job-unpaids-extractor-batch-<env>` | `*/10 9-11 * * 1-5` | every 10 min, 10:00–12:50 (BST) / 09:00–11:50 (GMT) | Publish batch unpaids soon after the jobs finish |
| `job-unpaids-extractor-sweep-<env>` | `0 15-18 * * 1-5` | hourly, covering 16:00 and 18:00 in both BST and GMT | Afternoon run after the 15:00 transactions, plus the 18:00 late sweep |

* **Why the windows are wide:** ACA evaluates cron in UTC only, so each window also covers the other
  half of the year. The extra runs find nothing to do and exit in seconds.
* **Why two schedules can run together safely:** both jobs use the same image, and a daily feed can
  only be claimed by one execution at a time.
* **Late runs** are not detected by the extractor. Use an Azure Monitor alert for a missing "opened
  pl-batch / cl-batch" log line by 11:30 (see [Operations](#operations)).
* **Not covered:**
  * Weekend runs. Add `0,6` to the day field if needed.
  * Bank holidays, which will raise a false late-run alert.
  * Manual approvals after 18:00, which are published on the next morning's batch window.

### Each execution

1. **Read each loan book's business date** from the monolith: `SELECT eodc_date FROM dual` in the PL
   and CL databases (`EodcBusinessCalendar`). It is read once per execution. See
   [Business dates and testing](#business-dates-and-testing).
2. **Open the daily feeds that are due** (`DailyFeedOpening`, with a `FeedOpener` per origin):
   * **Batch feeds** open once a new unpaids run has finished (`sql/main/latest-finished-unpaids-run.sql`).
     `END_TIME` is only written when a run succeeds, so a failed run never opens a feed. The latest successful run not yet used for any day belongs to the current
     business date. A row whose new run has started but not finished yet is ignored. A repeated run
     re-opens that business date's feed once.
   * **Manual feeds** open for the current business date and are picked up by every execution;
     earlier business dates are closed.
3. **Work through each open daily feed in turn** (`DailyFeedProcessor`):
   1. **Claim** it with one guarded `UPDATE`, so only one execution can work on it. If an execution
      dies, its claim expires after `claim-expires-after`.
   2. **Stage**: two statements run inside the loan book's own database, in one transaction:
      * `sql/ledger/stage.sql` adds the feed's new unpaids to the ledger as `PENDING`. Running it again
        adds nothing, because it skips anything already staged for the same source row.
      * `sql/ledger/collisions.sql` adds unpaids that break the rule "one unpaid per loan per date,
        across batch and manual" to the same ledger with status `COLLISION`, and raises an alert.
        A `COLLISION` entry is never sent, records the reason and the entry it clashes with, and is
        never purged.
   3. **Publish** pending unpaids in size-safe Service Bus batches, then mark them `SENT`:
      * an unpaid with bad data (e.g. a malformed loan number) is marked `INVALID` and never sent;
      * if Service Bus refuses a batch, its unpaids wait and are retried with growing back-off. After
        `max-send-attempts` they are parked as `FAILED`.
      Publishing stops at the first refused batch, so a Service Bus outage is not hammered.
   4. **Decide and record the outcome** (`FeedOutcomePolicy`, pure logic): `Done` if nothing is left
      in the ledger, `RetryLater` while unpaids are waiting
      to resend, `NeedsAttention` once any unpaid is `FAILED` or the daily feed has used up its attempts.
      These map to `DONE`, `FAILED` and `ATTENTION` on `UNPAID_DAILY_FEED`.
4. **Housekeeping** deletes published ledger entries and finished daily feeds past retention.

Daily feeds are processed one after another. Volumes are a few thousand unpaids a day, so a run
takes seconds; nothing is gained from running them in parallel.

### Business dates and testing

The extractor never uses the wall clock as a business date. Each loan book's date comes from the same
place the monolith reads it, `eodc_date` in the PL or CL database. PL and CL can be on different days.

**How an unpaids run is matched to a business date.** `I_SCHEDULED_JOBS` only records times, while the run
processes `eodc_date`. The latest successful run that has not already been used for a day belongs to the
current business date. This means:

* **Testers can move `eodc_date` on, even several times in one real day.** Each new run opens the new
  business date.
* **If `eodc_date` has moved on but that day's run has not finished**, nothing opens.
* **A repeated run on the same business date** re-opens that date once, so the extra unpaids are published.
* **Runs before `go-live-date` are ignored**, so a pre-go-live run cannot open the first day.
* **Limit: a run is attributed to the business date current when the extractor first sees it.** In
  production the schedule polls until 18:00, so this is the same day. If the extractor were down from
  the run until after end of day, the run would be attributed to the next business date. Its unpaids
  are still published, because the lookback window covers them.

**How the test team sets the business date:**

| How | When to use it |
|---|---|
| Move the monolith's `eodc_date` (nothing to change in the extractor) | Normal end-to-end testing: the extractor follows PL and CL |
| Locally: `UPDATE pl_owner.business_calendar SET eodc_date = DATE '2026-12-15'; COMMIT;` | The local Oracle mocks `eodc_date` with this table (one per loan book) |

Claims, send back-off, `occurredAt` and housekeeping age use real time on purpose: they are about when
things happened, not which business day they belong to.

**Delivery guarantee:** at-least-once, and effectively once on the topic.
* `MessageId` is a deterministic event id (MD5 of `loan book|table|ref|type`), so the topic's duplicate
  detection drops a resend after a crash.
* Consumers must still be idempotent on `eventId`.

**Ordering:** `SessionId` is `PL:<loan>` or `CL:<loan>`, so a loan's events arrive in order and PL and CL
loan numbers can never clash.

### Event contract (v1.0)

```json
{
  "eventId": "8c1b…",
  "eventType": "UnpaidRecorded",
  "schemaVersion": "1.0",
  "source": "urn:premiumfinance:monolith:unpaids:pl",
  "occurredAt": "2026-10-08T01:30:12.345Z",
  "data": {
    "sourceSystem": "PL",
    "origin": "BATCH",
    "loanNumber": "PL0001",
    "unpaidDate": "2026-10-07",
    "businessDate": "2026-10-07",
    "sourceTable": "UNPAID_DETAILS",
    "sourceReference": "12345"
  }
}
```

**Message headers:**
* `MessageId` = `eventId`
* `SessionId` = `PL:PL0001`
* `Subject` = `UnpaidRecorded`
* Application properties: `eventType`, `schemaVersion`, `sourceSystem`, `origin`, `businessDate`. Use
  these for subscription filters.

`sourceSystem` carries the loan book. The field names are a contract: the case creator relies on them,
so change them only with a new schema version.

---

## Repository layout

| Path | What |
|---|---|
| `src/main/java/.../businessdate` | `BusinessCalendar` **port** (eodc_date), `BusinessDates` |
| `src/main/java/.../feed` | `UnpaidFeed`, `FeedCatalog`, `LoanBook`, `UnpaidOrigin` |
| `src/main/java/.../dailyfeed` | `DailyFeed`, `FeedOutcome` (sealed), `FeedCounts`, and the `DailyFeedStore` **port** |
| `src/main/java/.../ledger` | `LedgerEntry`, and the `UnpaidLedger` **port** (one per loan book) |
| `src/main/java/.../publish` | `UnpaidEventPublisher`, `EventValidator`, and the `UnpaidEventSender` **port** |
| `src/main/java/.../opening` | Opening daily feeds (`BatchFeedOpener`, `ManualFeedOpener`) and the `FinishedUnpaidsRuns` **port** |
| `src/main/java/.../processing` | `DailyFeedProcessor` (one daily feed end to end) and `FeedOutcomePolicy` |
| `src/main/java/.../housekeeping` | `Housekeeping` and the `ExpiredDataPurger` **port** |
| `src/main/java/.../job` | `ExtractionJob` (one execution), `RunSummary`, `JobRunner` (exit code) |
| `src/main/java/.../metrics` | `ExtractorMetrics`: every metric name in one place |
| `src/main/java/.../infrastructure/jdbc` | **Adapters**: `JdbcDailyFeedStore`, `JdbcUnpaidLedger`, `JdbcFinishedUnpaidsRuns`, `SqlLoader` |
| `src/main/java/.../infrastructure/servicebus` | **Adapter**: `ServiceBusUnpaidEventSender`, message mapping, the event contract records |
| `src/main/java/.../config` | Settings records (one per concern) and the wiring of adapters to ports |
| `src/main/resources/sql` | **Every SQL statement**, one file each: `daily-feed/`, `ledger/`, `main/`, and `feeds/pl`, `feeds/cl` (each feed's query: edit these, not Java) |
| `db/` | DBA scripts: schemas, grants, synonyms, tables, read indexes, operator runbook |
| `local/` | docker-compose: Oracle Free (three schemas) + Service Bus emulator, seed data |
| `infra/` | Bicep: topic, subscriptions, identity, RBAC, ACA Job |

### Code structure (ports and adapters)

The core (`feed`, `dailyfeed`, `ledger`, `publish`, `opening`, `processing`, `housekeeping`, `job`) has
no JDBC or Azure SDK imports. It talks to the outside through small interfaces, which
`infrastructure.jdbc` and `infrastructure.servicebus` implement:

| Port | Adapter | Used by |
|---|---|---|
| `BusinessCalendar` | `EodcBusinessCalendar` (PL and CL DBs) | `ExtractionJob` |
| `DailyFeedStore` | `JdbcDailyFeedStore` (main DB) | openers, processor, job |
| `FinishedUnpaidsRuns` | `JdbcFinishedUnpaidsRuns` (`I_SCHEDULED_JOBS`) | `BatchFeedOpener` |
| `UnpaidLedger` | `JdbcUnpaidLedger` (one bean each for PL and CL) | processor, publisher |
| `UnpaidEventSender` | `ServiceBusUnpaidEventSender` | publisher |
| `ExpiredDataPurger` | the daily feed store and both ledgers | `Housekeeping` |

How the SOLID principles show up:

* **Single responsibility:** each class has one reason to change.
  * Batching to Service Bus is in the sender.
  * Ledger bookkeeping is in the publisher.
  * Retry and give-up rules are in `FeedOutcomePolicy`.
  * Metrics are in `ExtractorMetrics`.
  * The exit code is in `JobRunner`.
* **Open/closed:** no `if (batch)` branches.
  * A new origin is a new `FeedOpener` bean; `DailyFeedOpening` refuses to start if an origin has none.
  * Origin behaviour (`followsUnpaidsRun`) lives on `UnpaidOrigin`.
  * Each `FeedOutcome` knows its own status.
* **Liskov:** both ledgers, and every purger, are interchangeable behind their interface.
* **Interface segregation:** settings are split into `ExtractorProperties`, `PublishProperties`,
  `ServiceBusProperties` and `HousekeepingProperties`. Adapters are given only the values they need.
* **Dependency inversion:** use cases depend on ports. Adapters are wired in `config/JdbcConfig` and
  `config/ServiceBusConfig`. That is also why the processor and publisher are unit-tested with plain mocks.

Moving the unpaids runs out of the monolith later means a new `FinishedUnpaidsRuns` adapter. Nothing in
the core changes.

---

## Before go-live: confirm the `TODO(confirm)` items

Search the repository for `TODO(confirm)`. You only need to edit SQL and YAML; no Java changes.

1. The `UNPAID_DETAILS` primary key column in PL and CL (assumed `UNPAID_ID`).
2. The real `I_SCHEDULED_JOBS` column names (`sql/main/latest-finished-unpaids-run.sql`). The
   success rule is already confirmed.
3. The real name and owner of `eodc_date` in PL and CL, and the `EXECUTE` grant for the extractor
   user (`sql/business-date/eodc-date.sql`, `db/pl` and `db/cl` grant scripts).
4. For manual entries:
   * the transactions table and its primary key;
   * how the loan number is constructed;
   * which column holds the unpaid date;
   * the manual type codes and the approval status value;
   * whether there is an approval date. If there is, window on it and reduce `lookback-days`.
5. The real loan-number format (`extractor.publish.loan-number-pattern`).
6. `go-live-date`.

Two rules keep the extractor correct:
* **Never rename a feed `id` or `source-table` once live.** Both are part of the idempotency key.
* Each feed query must return **one row per `SOURCE_REF`**. If it returns duplicates, staging fails
  loudly and the daily feed ends up in `ATTENTION`.

---

## Build and test

> First time only: generate the Gradle wrapper with `gradle wrapper --gradle-version 9.1.0`.
> It was not committed because the wrapper jar is a binary.

```bash
./gradlew build              # compile + unit tests + integration tests (integration needs Docker)
./gradlew test               # unit tests only
./gradlew integrationTest    # Testcontainers: real Oracle Free + production DDL + seed data
```

The integration test runs the whole job twice against Oracle and checks that:
* batch and manual unpaids are published;
* the manual-vs-batch collision is kept in the ledger as `COLLISION` and not sent;
* the bad loan number is marked `INVALID`;
* the unapproved and non-manual transactions are ignored;
* the second run publishes nothing (idempotency).

## Run locally

```bash
docker compose -f local/docker-compose.yml up -d     # wait for "DATABASE IS READY TO USE!" (docker logs -f unpaids-oracle)
./gradlew bootRun --args='--spring.profiles.active=local'
```

Each `bootRun` is one job execution: it processes what's due and exits. Run it again to see that it's
idempotent.

Messages land in the emulator subscription `local-inspect`, which uses sessions. To reset everything:
`docker compose -f local/docker-compose.yml down -v`.

## Run locally against the real dev Service Bus

The dev namespace is private, so this needs:
* the VPN;
* DNS that resolves the namespace to its private IP;
* `az login`, with your account in the developer group;
* the dev `createSandboxTopic` option, which creates `unpaids-events-sandbox` with no consumer
  subscriptions, so test events never reach Flexys.

```bash
az login
nslookup sb-premiumfinance-dev.servicebus.windows.net      # must return a 10.x.x.x address
./gradlew bootRun --args='--spring.profiles.active=local,local-azure'
```

`local-azure` overrides only Service Bus settings: it uses your Entra identity and AMQP over
WebSockets (port 443). The databases are still the local Docker Oracle.

## Deploy (dev)

1. **DBA** runs the scripts in `db/`, in this order:
   * `main/00` then `main/01` (main DB);
   * `cl/00`, then `common/10` as `UNPAIDS_EXTRACTOR`;
   * `pl/00`, then `common/10`;
   * `source_indexes.sql`.
2. Put three Key Vault secrets in place:
   * `unpaids-extractor-main-db-password`
   * `unpaids-extractor-pl-db-password`
   * `unpaids-extractor-cl-db-password`
3. Build and push the image: `docker build -t <acr>.azurecr.io/unpaids-event-extractor:0.1.0 . && docker push …`
4. Fill in `infra/dev.bicepparam`, then deploy:
   `az deployment group create -g <rg> -f infra/main.bicep -p infra/dev.bicepparam`.
   Platform grants the job's identity `AcrPull` on the registry.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `extractor.go-live-date` | 2026-11-01 | Nothing earlier is ever published |
| `extractor.claim-expires-after` | 35m | Must be longer than the ACA `replicaTimeout` (30m) |
| `extractor.max-attempts` | 10 | Failed attempts at a daily feed before `ATTENTION` |
| `extractor.publish.chunk-size` | 500 | Ledger entries per read; at most 1000 |
| `extractor.publish.max-send-attempts` | 8 | Per unpaid, before `FAILED` |
| `extractor.publish.retry-backoff` / `max-retry-backoff` | 1m / 30m | Grows with each failed send |
| `extractor.feeds[*].lookback-days` | 7 batch / 30 manual | How far back each run looks |
| `extractor.housekeeping.retention-days` | 400 | Must be more than the longest lookback + 7 (checked at startup) |

The dev profile reads these environment variables:
* `SERVICEBUS_NAMESPACE`, `AZURE_CLIENT_ID`
* `{MAIN,PL,CL}_DB_URL`, `{MAIN,PL,CL}_DB_PASSWORD` (`_USERNAME` defaults to `unpaids_extractor`)
* `{PL,CL}_MANUAL_TXN_TYPES`
* `APPLICATIONINSIGHTS_CONNECTION_STRING`

---

## Operations

| Status | Meaning | What to do |
|---|---|---|
| Daily feed `FAILED` | Retried automatically by the next execution | Nothing, unless it keeps happening |
| Daily feed `ATTENTION` | Retries used up, or unpaids could not be sent | Fix the cause, then `db/runbook.sql` §2 / §3 |
| Ledger entry `INVALID` | Bad source data, never sent | `db/runbook.sql` §4 |
| Ledger entry `COLLISION` | A manual and a batch unpaid for the same loan and date | The business corrects the data, then mark it resolved with `db/runbook.sql` §5 |

**Alerts.** Create Azure Monitor log alerts on these log keys:
* `alert=UNPAID_COLLISION`
* `alert=INVALID_UNPAID`
* `alert=DAILY_FEED_NEEDS_ATTENTION`

Also alert on failed job executions (exit code 1) and on subscription DLQ depth > 0.

**Late unpaids run.** The extractor logs `Unpaids run … opened pl-batch for business date …` when a run
finishes. Alert when that line has not appeared by 11:30 on a weekday. The example below is for a
workspace-based Application Insights resource; check the table name in yours:

```kusto
AppTraces
| where TimeGenerated > startofday(now())
| where Message has "opened pl-batch for business date"   // one alert rule per feed (pl-batch, cl-batch)
| count
```
Schedule it at 11:30 on weekdays and fire when the count is 0.

**Metrics** (Micrometer, shipped by the App Insights agent):
* `unpaids.events.sent|invalid|collisions|send_failures` (tagged by `feed`)
* `unpaids.daily_feed.duration` (tagged by `feed` and `outcome`)

## Known limits / next steps
* The ledger is not partitioned, to avoid depending on the Oracle Partitioning licence. Housekeeping
  deletes in bounded batches instead.
* Reversals of manual unpaids (`UnpaidReversed`) are out of scope. They would be added as another
  feed with its own event type.
* The Service Bus emulator does not support every Premium feature. Duplicate detection and sessions
  are configured locally, but test DLQ behaviour in dev.
