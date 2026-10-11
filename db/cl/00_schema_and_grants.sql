-- CL DB: extractor schema + read-only access to monolith tables (run as DBA).
-- Replace CL_OWNER with the schema owning UNPAID_DETAILS and the transactions table.

CREATE USER unpaids_extractor IDENTIFIED BY "&extractor_password"
  DEFAULT TABLESPACE users QUOTA 2G ON users;
GRANT CREATE SESSION, CREATE TABLE, CREATE SYNONYM TO unpaids_extractor;

GRANT SELECT ON cl_owner.unpaid_details TO unpaids_extractor;
CREATE SYNONYM unpaids_extractor.unpaid_details FOR cl_owner.unpaid_details;

-- TODO(confirm) real transactions table holding approved manual unpaids
GRANT SELECT ON cl_owner.transactions TO unpaids_extractor;
CREATE SYNONYM unpaids_extractor.manual_transactions FOR cl_owner.transactions;

-- The business date, read exactly as the monolith reads it (SELECT eodc_date FROM dual).
-- TODO(confirm) owner and real name of eodc_date (function / package function / synonym)
GRANT EXECUTE ON cl_owner.eodc_date TO unpaids_extractor;
CREATE SYNONYM unpaids_extractor.eodc_date FOR cl_owner.eodc_date;

-- then, connected as UNPAIDS_EXTRACTOR:  @../common/10_unpaid_event_ledger.sql
