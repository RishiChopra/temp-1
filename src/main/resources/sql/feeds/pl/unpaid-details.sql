-- PL batch unpaids, written by the PL unpaids run (job 133).
-- Returns SOURCE_REF, LOAN_NUMBER, UNPAID_DATE. Binds available: :windowStart, :businessDate, :goLiveDate
-- TODO(confirm): primary key column of UNPAID_DETAILS (assumed UNPAID_ID).
SELECT u.unpaid_id    AS source_ref,
       u.loan_number  AS loan_number,
       u.unpaid_date  AS unpaid_date
  FROM unpaid_details u
 WHERE u.unpaid_date >= :windowStart
