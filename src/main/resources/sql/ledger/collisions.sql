-- Record unpaids that break the rule "one unpaid per loan per date" as COLLISION entries in the ledger.
-- Runs straight after stage.sql, in the same transaction, with the same feed query.
--
-- Anything still not in the ledger at this point clashes with an unpaid already there:
--   NATURAL_KEY_COLLISION : from a different source table (e.g. a manual entry for a batch unpaid)
--   DUPLICATE_IN_SOURCE   : from the same source table
-- The unique index ux_uel_natural allows at most one live entry per loan and date, so the join below
-- matches at most one row. COLLISION entries are never sent and never purged.
INSERT INTO unpaid_event_ledger
       (event_id, loan_book, origin, source_table, source_ref, loan_number, unpaid_date,
        event_type, business_date, status, attempts, last_error, conflicting_ledger_id, created_at)
WITH feed_unpaids AS (
    SELECT TO_CHAR(f.source_ref) AS source_ref,
           TRIM(f.loan_number)   AS loan_number,
           TRUNC(f.unpaid_date)  AS unpaid_date
      FROM (
{{FEED_QUERY}}
           ) f
),
not_staged AS (
    SELECT u.*
      FROM feed_unpaids u
     WHERE NOT EXISTS (SELECT 1 FROM unpaid_event_ledger l
                        WHERE l.source_table = :sourceTable
                          AND l.source_ref   = u.source_ref
                          AND l.event_type   = :eventType)
)
SELECT STANDARD_HASH(:loanBook || '|' || :sourceTable || '|' || n.source_ref || '|' || :eventType, 'MD5'),
       :loanBook, :origin, :sourceTable, n.source_ref, n.loan_number, n.unpaid_date,
       :eventType, :businessDate, 'COLLISION', 0,
       CASE WHEN taken.source_table = :sourceTable THEN 'DUPLICATE_IN_SOURCE'
            ELSE 'NATURAL_KEY_COLLISION' END,
       taken.ledger_id, SYSTIMESTAMP
  FROM not_staged n
  JOIN unpaid_event_ledger taken
    ON taken.loan_number = n.loan_number
   AND taken.unpaid_date = n.unpaid_date
   AND taken.event_type  = :eventType
   AND taken.status NOT IN ('INVALID', 'COLLISION')
