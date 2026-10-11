-- Run in BOTH the PL DB and the CL DB, in schema UNPAIDS_EXTRACTOR.
-- One ledger per loan book; it holds that book's batch AND manual unpaids:
--   PENDING   staged, waiting to be sent (or to be retried)
--   SENT      accepted by Service Bus
--   FAILED    used up its send attempts; needs an operator (db/runbook.sql)
--   INVALID   bad data (e.g. malformed loan number); never sent
--   COLLISION breaks "one unpaid per loan per date across batch and manual"; never sent, never purged.
--             LAST_ERROR holds the reason, CONFLICTING_LEDGER_ID the entry it clashes with.

CREATE TABLE unpaid_event_ledger (
  ledger_id              NUMBER GENERATED ALWAYS AS IDENTITY,
  event_id               RAW(16)                   NOT NULL,   -- MD5(loan book|table|ref|type) = Service Bus MessageId
  loan_book              VARCHAR2(10)              NOT NULL,   -- PL | CL
  origin                 VARCHAR2(10)              NOT NULL,   -- BATCH | MANUAL
  source_table           VARCHAR2(60)              NOT NULL,   -- logical source (UNPAID_DETAILS, MANUAL_TRANSACTIONS)
  source_ref             VARCHAR2(100)             NOT NULL,   -- PK of the source row
  loan_number            VARCHAR2(100),                        -- nullable: bad rows are kept and marked INVALID
  unpaid_date            DATE,
  event_type             VARCHAR2(40)              NOT NULL,
  business_date          DATE                      NOT NULL,   -- business date of the daily feed that staged it
  status                 VARCHAR2(10)              NOT NULL,
  attempts               NUMBER(5)       DEFAULT 0 NOT NULL,
  next_attempt_at        TIMESTAMP WITH TIME ZONE,
  last_error             VARCHAR2(4000),                       -- send error, INVALID reason or COLLISION reason
  conflicting_ledger_id  NUMBER,                               -- COLLISION only
  created_at             TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  sent_at                TIMESTAMP WITH TIME ZONE,
  CONSTRAINT pk_uel PRIMARY KEY (ledger_id),
  CONSTRAINT uq_uel_source UNIQUE (source_table, source_ref, event_type),   -- idempotent staging
  CONSTRAINT uq_uel_event  UNIQUE (event_id),
  CONSTRAINT ck_uel_status CHECK (status IN ('PENDING','SENT','FAILED','INVALID','COLLISION')),
  CONSTRAINT ck_uel_origin CHECK (origin IN ('BATCH','MANUAL'))
);

-- Business rule "one unpaid per loan per date across batch and manual", enforced for publishable rows only
-- (INVALID and COLLISION rows are left out, so the clashing row can be kept for an operator to see).
CREATE UNIQUE INDEX ux_uel_natural ON unpaid_event_ledger (
  CASE WHEN status NOT IN ('INVALID','COLLISION') AND loan_number IS NOT NULL AND unpaid_date IS NOT NULL THEN loan_number END,
  CASE WHEN status NOT IN ('INVALID','COLLISION') AND loan_number IS NOT NULL AND unpaid_date IS NOT NULL THEN unpaid_date END,
  CASE WHEN status NOT IN ('INVALID','COLLISION') AND loan_number IS NOT NULL AND unpaid_date IS NOT NULL THEN event_type END);

CREATE INDEX ix_uel_natural ON unpaid_event_ledger (loan_number, unpaid_date, event_type);       -- collision checks
CREATE INDEX ix_uel_due     ON unpaid_event_ledger (source_table, event_type, status, next_attempt_at); -- publisher
CREATE INDEX ix_uel_purge   ON unpaid_event_ledger (status, created_at);                          -- housekeeping, collisions list

COMMENT ON TABLE unpaid_event_ledger IS 'Unpaid events staged/published by the extractor (owned by the new-apps team)';
