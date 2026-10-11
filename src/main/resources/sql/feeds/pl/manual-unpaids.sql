-- PL manual unpaids: entries keyed in by staff, approved, and posted as transactions. They never
-- appear in UNPAID_DETAILS.
-- Returns SOURCE_REF, LOAN_NUMBER, UNPAID_DATE.
-- Binds: :windowStart (on the created date), :manualTxnTypes (list) and :approvedStatus (query-params in YAML).
--
-- TODO(confirm) against the real schema:
--   * the transactions table (MANUAL_TRANSACTIONS is a synonym) and its key (TXN_ID)
--   * how the loan number is built (a plain column here)
--   * which date is the unpaid date (EFFECTIVE_DATE here)
--   * if there is an approval date, filter on it instead and shorten lookback-days in YAML
SELECT t.txn_id          AS source_ref,
       t.loan_number     AS loan_number,
       t.effective_date  AS unpaid_date
  FROM manual_transactions t
 WHERE t.txn_type IN (:manualTxnTypes)
   AND t.approval_status = :approvedStatus
   AND t.created_date >= :windowStart
