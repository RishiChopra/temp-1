-- Read-path indexes on MONOLITH tables (run as DBA in PL and CL DBs; adjust owner/table/column names).
-- Without these the windowed candidate queries scan 10 years of history on every run.
-- Create ONLINE to avoid blocking the monolith.

CREATE INDEX ix_unpaid_details_date ON pl_owner.unpaid_details (unpaid_date, loan_number) ONLINE;
-- CL DB:
-- CREATE INDEX ix_unpaid_details_date ON cl_owner.unpaid_details (unpaid_date, loan_number) ONLINE;

-- TODO(confirm) column names; supports "txn_type IN (...) AND created_date >= :windowStart"
CREATE INDEX ix_txn_manual_unpaid ON pl_owner.transactions (txn_type, created_date) ONLINE;
-- CL DB:
-- CREATE INDEX ix_txn_manual_unpaid ON cl_owner.transactions (txn_type, created_date) ONLINE;
