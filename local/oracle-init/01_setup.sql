-- Local only. Runs once as SYS on first container start.
-- Builds: mock monolith schemas (main_owner, pl_owner, cl_owner) with seed data, and three
-- extractor schemas using the SAME DDL as production (db/...).
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;

-- ---------------------------------------------------------------- monolith owners (mock)
CREATE USER main_owner IDENTIFIED BY owner QUOTA UNLIMITED ON users;
CREATE USER pl_owner    IDENTIFIED BY owner QUOTA UNLIMITED ON users;
CREATE USER cl_owner    IDENTIFIED BY owner QUOTA UNLIMITED ON users;

-- Mock of the monolith's scheduled-job table (main DB). Columns are assumptions: see
-- src/main/resources/sql/main/latest-finished-unpaids-run.sql
CREATE TABLE main_owner.i_scheduled_jobs (
  job_id      NUMBER       NOT NULL,
  start_time  DATE,
  end_time    DATE);

-- Mock of the monolith's business date (eodc_date) in each loan book database. To test another business
-- date locally:  UPDATE pl_owner.business_calendar SET eodc_date = DATE '2026-12-15'; COMMIT;
CREATE TABLE pl_owner.business_calendar (eodc_date DATE NOT NULL);
CREATE TABLE cl_owner.business_calendar (eodc_date DATE NOT NULL);
-- Yesterday: the seeded unpaids runs below are for yesterday's business date.
INSERT INTO pl_owner.business_calendar VALUES (TRUNC(SYSDATE) - 1);
INSERT INTO cl_owner.business_calendar VALUES (TRUNC(SYSDATE) - 1);
CREATE FUNCTION pl_owner.eodc_date RETURN DATE IS d DATE; BEGIN SELECT eodc_date INTO d FROM pl_owner.business_calendar; RETURN d; END;
/
CREATE FUNCTION cl_owner.eodc_date RETURN DATE IS d DATE; BEGIN SELECT eodc_date INTO d FROM cl_owner.business_calendar; RETURN d; END;
/

CREATE TABLE pl_owner.unpaid_details (
  unpaid_id    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  loan_number  VARCHAR2(30),
  unpaid_date  DATE,
  amount       NUMBER(12,2));

CREATE TABLE pl_owner.transactions (
  txn_id           NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  loan_number      VARCHAR2(30),
  txn_type         VARCHAR2(20),
  approval_status  VARCHAR2(20),
  effective_date   DATE,
  created_date     DATE,
  amount           NUMBER(12,2));

CREATE TABLE cl_owner.unpaid_details  AS SELECT * FROM pl_owner.unpaid_details  WHERE 1 = 0;
ALTER TABLE cl_owner.unpaid_details ADD CONSTRAINT pk_cl_ud PRIMARY KEY (unpaid_id);
CREATE TABLE cl_owner.transactions    AS SELECT * FROM pl_owner.transactions    WHERE 1 = 0;
ALTER TABLE cl_owner.transactions ADD CONSTRAINT pk_cl_txn PRIMARY KEY (txn_id);

-- ---------------------------------------------------------------- extractor schemas
-- Production uses one user named UNPAIDS_EXTRACTOR per database; here all three share one PDB.
CREATE USER unpaids_extractor_main IDENTIFIED BY extractor QUOTA UNLIMITED ON users;
CREATE USER unpaids_extractor_pl    IDENTIFIED BY extractor QUOTA UNLIMITED ON users;
CREATE USER unpaids_extractor_cl    IDENTIFIED BY extractor QUOTA UNLIMITED ON users;
GRANT CREATE SESSION TO unpaids_extractor_main, unpaids_extractor_pl, unpaids_extractor_cl;

GRANT SELECT ON main_owner.i_scheduled_jobs TO unpaids_extractor_main;
CREATE SYNONYM unpaids_extractor_main.i_scheduled_jobs FOR main_owner.i_scheduled_jobs;

GRANT SELECT ON pl_owner.unpaid_details TO unpaids_extractor_pl;
GRANT SELECT ON pl_owner.transactions   TO unpaids_extractor_pl;
CREATE SYNONYM unpaids_extractor_pl.unpaid_details      FOR pl_owner.unpaid_details;
CREATE SYNONYM unpaids_extractor_pl.manual_transactions FOR pl_owner.transactions;

GRANT SELECT ON cl_owner.unpaid_details        TO unpaids_extractor_cl;
GRANT SELECT ON cl_owner.transactions          TO unpaids_extractor_cl;
CREATE SYNONYM unpaids_extractor_cl.unpaid_details        FOR cl_owner.unpaid_details;
CREATE SYNONYM unpaids_extractor_cl.manual_transactions   FOR cl_owner.transactions;

GRANT EXECUTE ON pl_owner.eodc_date TO unpaids_extractor_pl;
GRANT EXECUTE ON cl_owner.eodc_date TO unpaids_extractor_cl;
CREATE SYNONYM unpaids_extractor_pl.eodc_date FOR pl_owner.eodc_date;
CREATE SYNONYM unpaids_extractor_cl.eodc_date FOR cl_owner.eodc_date;

ALTER SESSION SET CURRENT_SCHEMA = unpaids_extractor_main;
@/opt/db/main/01_unpaid_daily_feed.sql
ALTER SESSION SET CURRENT_SCHEMA = unpaids_extractor_pl;
@/opt/db/common/10_unpaid_event_ledger.sql
ALTER SESSION SET CURRENT_SCHEMA = unpaids_extractor_cl;
@/opt/db/common/10_unpaid_event_ledger.sql
ALTER SESSION SET CURRENT_SCHEMA = SYS;

-- ---------------------------------------------------------------- seed data (relative to today)
-- PL batch, completed yesterday
INSERT INTO pl_owner.unpaid_details (loan_number, unpaid_date, amount) VALUES ('PL0001', TRUNC(SYSDATE) - 1, 120.00);
INSERT INTO pl_owner.unpaid_details (loan_number, unpaid_date, amount) VALUES ('PL0002', TRUNC(SYSDATE) - 1,  80.50);
INSERT INTO pl_owner.unpaid_details (loan_number, unpaid_date, amount) VALUES ('pl 0003', TRUNC(SYSDATE) - 1, 10.00);  -- INVALID format
INSERT INTO pl_owner.unpaid_details (loan_number, unpaid_date, amount) VALUES ('PL0099', DATE '2025-06-01', 99.00);    -- before go-live: ignored
-- PL job 133 started yesterday 23:30 and finished today 00:15: the run for business date yesterday (eodc_date)
INSERT INTO main_owner.i_scheduled_jobs (job_id, start_time, end_time)
  VALUES (133, TRUNC(SYSDATE) - 1 + 23.5/24, TRUNC(SYSDATE) + 15/1440);
-- A FAILED PL run today (END_TIME never written) must not count:
INSERT INTO main_owner.i_scheduled_jobs (job_id, start_time, end_time)
  VALUES (133, TRUNC(SYSDATE) + 1/24, NULL);

-- PL manual entries
INSERT INTO pl_owner.transactions (loan_number, txn_type, approval_status, effective_date, created_date, amount)
  VALUES ('PL0100', 'MUNP', 'APPROVED', TRUNC(SYSDATE), TRUNC(SYSDATE), 55.00);                 -- published
INSERT INTO pl_owner.transactions (loan_number, txn_type, approval_status, effective_date, created_date, amount)
  VALUES ('PL0001', 'MUNP', 'APPROVED', TRUNC(SYSDATE) - 1, TRUNC(SYSDATE), 120.00);            -- COLLISION with batch PL0001
INSERT INTO pl_owner.transactions (loan_number, txn_type, approval_status, effective_date, created_date, amount)
  VALUES ('PL0101', 'MUNP', 'PENDING', TRUNC(SYSDATE), TRUNC(SYSDATE), 20.00);                  -- not approved: ignored
INSERT INTO pl_owner.transactions (loan_number, txn_type, approval_status, effective_date, created_date, amount)
  VALUES ('PL0102', 'PAYMENT', 'APPROVED', TRUNC(SYSDATE), TRUNC(SYSDATE), 300.00);             -- not a manual unpaid: ignored

-- CL batch, job 91 completed yesterday
INSERT INTO cl_owner.unpaid_details (unpaid_id, loan_number, unpaid_date, amount) VALUES (1, 'CL0001', TRUNC(SYSDATE) - 1, 400.00);
INSERT INTO cl_owner.unpaid_details (unpaid_id, loan_number, unpaid_date, amount) VALUES (2, 'CL0002', TRUNC(SYSDATE) - 1, 250.00);
INSERT INTO main_owner.i_scheduled_jobs (job_id, start_time, end_time)
  VALUES (91, TRUNC(SYSDATE) - 1 + 21/24, TRUNC(SYSDATE) - 1 + 21/24 + 25/1440);
-- An older job 91 row whose NEW run has started but not finished must not count:
INSERT INTO main_owner.i_scheduled_jobs (job_id, start_time, end_time)
  VALUES (91, TRUNC(SYSDATE) - 3 + 23/24, TRUNC(SYSDATE) - 4 + 21/24);

-- CL manual entry
INSERT INTO cl_owner.transactions (txn_id, loan_number, txn_type, approval_status, effective_date, created_date, amount)
  VALUES (1, 'CL0200', 'MUNP', 'APPROVED', TRUNC(SYSDATE), TRUNC(SYSDATE), 75.00);

COMMIT;
