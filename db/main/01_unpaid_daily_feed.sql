-- Main DB, schema UNPAIDS_EXTRACTOR. One row per feed per business date ("daily feed").
-- Written only by the extractor. Status lifecycle: see DailyFeedStatus.java and README "Operations".
CREATE TABLE unpaid_daily_feed (
  business_date    DATE                      NOT NULL,
  feed_id          VARCHAR2(50)              NOT NULL,   -- pl-batch | cl-batch | pl-manual | cl-manual
  loan_book        VARCHAR2(10)              NOT NULL,   -- PL | CL
  origin           VARCHAR2(10)              NOT NULL,   -- BATCH (after the unpaids run) | MANUAL (all day)
  unpaids_run_ref  VARCHAR2(100),                        -- which finished unpaids run opened it (batch only)
  status           VARCHAR2(20)              NOT NULL,
  claimed_by       VARCHAR2(100),                        -- ACA job execution id
  claimed_at       TIMESTAMP WITH TIME ZONE,
  attempts         NUMBER(5)       DEFAULT 0 NOT NULL,   -- failed attempts in a row; reset when DONE
  staged_count     NUMBER(10),
  collision_count  NUMBER(10),
  sent_count       NUMBER(10),
  invalid_count    NUMBER(10),
  last_error       VARCHAR2(4000),
  opened_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_run_at      TIMESTAMP WITH TIME ZONE,
  completed_at     TIMESTAMP WITH TIME ZONE,
  CONSTRAINT pk_unpaid_daily_feed PRIMARY KEY (business_date, feed_id),
  CONSTRAINT ck_udf_status    CHECK (status IN ('NEW','IN_PROGRESS','DONE','FAILED','ATTENTION','CLOSED')),
  CONSTRAINT ck_udf_loan_book CHECK (loan_book IN ('PL','CL')),
  CONSTRAINT ck_udf_origin    CHECK (origin IN ('BATCH','MANUAL'))
);

CREATE INDEX ix_udf_status ON unpaid_daily_feed (status, business_date);

COMMENT ON TABLE unpaid_daily_feed IS 'Unpaids event extractor: one row per feed per business date (owned by the new-apps team)';
