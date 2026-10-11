-- Open a daily feed (one feed, one business date).
-- If a batch feed's day has already finished and the unpaids run was re-run (new run ref), the day is
-- re-opened so the unpaids added by the re-run are published.
MERGE INTO unpaid_daily_feed d
USING (SELECT :businessDate  AS business_date,
              :feedId        AS feed_id,
              :unpaidsRunRef AS unpaids_run_ref
         FROM dual) s
   ON (d.business_date = s.business_date AND d.feed_id = s.feed_id)
 WHEN MATCHED THEN UPDATE
      SET d.status          = 'NEW',
          d.attempts        = 0,
          d.unpaids_run_ref = s.unpaids_run_ref,
          d.last_error      = NULL,
          d.opened_at       = SYSTIMESTAMP
    WHERE d.origin = 'BATCH'
      AND d.status IN ('DONE', 'FAILED', 'ATTENTION')
      AND NVL(d.unpaids_run_ref, '~') <> NVL(s.unpaids_run_ref, '~')
 WHEN NOT MATCHED THEN INSERT
      (business_date, feed_id, loan_book, origin, unpaids_run_ref, status, attempts, opened_at)
      VALUES (s.business_date, s.feed_id, :loanBook, :origin, s.unpaids_run_ref, 'NEW', 0, SYSTIMESTAMP)
