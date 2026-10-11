-- Operator runbook queries (connect as UNPAIDS_EXTRACTOR in the relevant DB).

-- 1. Which daily feeds are stuck? (main DB)
SELECT business_date, feed_id, status, attempts, last_error, last_run_at
  FROM unpaid_daily_feed
 WHERE status IN ('FAILED', 'ATTENTION', 'IN_PROGRESS')
 ORDER BY business_date, feed_id;

-- 2. Retry a daily feed after fixing the cause (main DB). Safe: every step can be repeated.
UPDATE unpaid_daily_feed SET status = 'NEW', attempts = 0, last_error = NULL
 WHERE business_date = DATE '2026-11-03' AND feed_id = 'cl-batch' AND status IN ('FAILED', 'ATTENTION');
COMMIT;

-- 3. Ledger entries that used up their send attempts (PL or CL DB), then retry them.
SELECT ledger_id, source_table, source_ref, loan_number, unpaid_date, attempts, last_error
  FROM unpaid_event_ledger WHERE status = 'FAILED';
UPDATE unpaid_event_ledger SET status = 'PENDING', attempts = 0, next_attempt_at = NULL
 WHERE status = 'FAILED';
COMMIT;
-- ...and retry the related ATTENTION daily feed with query 2.

-- 4. INVALID rows (bad loan number / missing date). Fix the source data, then re-stage by deleting the
--    ledger row: the next run stages it again from the (corrected) source row.
SELECT ledger_id, source_table, source_ref, loan_number, unpaid_date, last_error
  FROM unpaid_event_ledger WHERE status = 'INVALID' ORDER BY created_at DESC;
-- DELETE FROM unpaid_event_ledger WHERE ledger_id = :id AND status = 'INVALID'; COMMIT;

-- 5. Open collisions (manual vs batch for the same loan + unpaid date), with the entry each clashes with.
--    COLLISION entries are never sent and never purged.
SELECT c.ledger_id, c.last_error AS reason, c.source_table, c.source_ref, c.loan_number, c.unpaid_date,
       c.business_date, l.source_table AS published_from, l.source_ref AS published_ref, l.status AS published_status
  FROM unpaid_event_ledger c
  LEFT JOIN unpaid_event_ledger l ON l.ledger_id = c.conflicting_ledger_id
 WHERE c.status = 'COLLISION'
   AND c.last_error NOT LIKE '%| RESOLVED%'
 ORDER BY c.created_at;

-- Mark one resolved after the business has corrected the data (manual entries cannot be edited, so the
-- entry stays in the ledger; deleting it would only re-create it on the next run). Record who and why:
UPDATE unpaid_event_ledger
   SET last_error = SUBSTR(last_error || ' | RESOLVED ' || TO_CHAR(SYSDATE, 'YYYY-MM-DD') || ' by ' || USER
                           || ': Manual entry reversed in monolith', 1, 4000)
 WHERE ledger_id = :id AND status = 'COLLISION';
COMMIT;
