-- The most recent successful unpaids run, from the monolith's I_SCHEDULED_JOBS (main database).
-- Used by every batch feed: :jobId is 133 for PL and 91 for CL.
--
--   * END_TIME is only written when a run succeeds, so a row with an end time is a successful run.
--   * END_TIME >= START_TIME skips a row whose new run has started but not finished yet (the end time
--     still on it belongs to the previous run).
--   * The run is matched to a business date in Java (BatchFeedOpener): I_SCHEDULED_JOBS has no business
--     date, the run processes the loan book's eodc_date.
--   * RUN_REF identifies the run; a repeated run gets a new one.
--   * :notBefore (go-live date) ignores runs from before the extractor went live.
--
-- TODO(confirm): real column names for JOB_ID, START_TIME and END_TIME.
SELECT TO_CHAR(:jobId) || '@' || TO_CHAR(j.end_time, 'YYYY-MM-DD"T"HH24:MI:SS') AS run_ref,
       j.end_time                                                              AS end_time
  FROM i_scheduled_jobs j
 WHERE j.job_id = :jobId
   AND j.start_time IS NOT NULL
   AND j.end_time IS NOT NULL
   AND j.end_time >= j.start_time
   AND j.start_time >= :notBefore
 ORDER BY j.end_time DESC
 FETCH FIRST 1 ROW ONLY
