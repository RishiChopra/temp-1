-- CL manual unpaids: entries keyed in by staff, approved, and posted as transactions.
-- Returns SOURCE_REF, LOAN_NUMBER, UNPAID_DATE.
-- Binds: :windowStart (on the created date), :manualTxnTypes (list) and :approvedStatus (query-params in YAML).
-- TODO(confirm) the same points as feeds/pl/manual-unpaids.sql.
SELECT t.txn_id          AS source_ref,
       t.loan_number     AS loan_number,
       t.effective_date  AS unpaid_date
  FROM manual_transactions t
 WHERE t.txn_type IN (:manualTxnTypes)
   AND t.approval_status = :approvedStatus
   AND t.created_date >= :windowStart
