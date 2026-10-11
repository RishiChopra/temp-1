-- Stage a feed's new unpaids into the ledger as PENDING events.
-- {{FEED_QUERY}} is replaced by the feed's query (SOURCE_REF, LOAN_NUMBER, UNPAID_DATE).
--
--   feed_unpaids : the feed's unpaids in the lookback window, tidied up
--   not_staged   : not yet in the ledger, in any status (so re-runs add nothing)
--   ranked       : numbers repeats of the same loan and unpaid date (only the first is staged)
--   final WHERE  : the loan and unpaid date are not already taken by any feed (batch vs manual rule)
--
-- Unpaids with no loan number or date are staged anyway; the publisher marks them INVALID so they stay
-- visible. Unpaids that clash are staged as COLLISION by collisions.sql.
INSERT INTO unpaid_event_ledger
       (event_id, loan_book, origin, source_table, source_ref, loan_number, unpaid_date,
        event_type, business_date, status, attempts, created_at)
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
),
ranked AS (
    SELECT n.*,
           ROW_NUMBER() OVER (PARTITION BY n.loan_number, n.unpaid_date ORDER BY n.source_ref) AS rn
      FROM not_staged n
)
SELECT STANDARD_HASH(:loanBook || '|' || :sourceTable || '|' || r.source_ref || '|' || :eventType, 'MD5'),
       :loanBook, :origin, :sourceTable, r.source_ref, r.loan_number, r.unpaid_date,
       :eventType, :businessDate, 'PENDING', 0, SYSTIMESTAMP
  FROM ranked r
 WHERE (r.rn = 1 OR r.loan_number IS NULL OR r.unpaid_date IS NULL)
   AND NOT EXISTS (SELECT 1 FROM unpaid_event_ledger l
                    WHERE l.loan_number = r.loan_number
                      AND l.unpaid_date = r.unpaid_date
                      AND l.event_type  = :eventType
                      AND l.status NOT IN ('INVALID', 'COLLISION'))
