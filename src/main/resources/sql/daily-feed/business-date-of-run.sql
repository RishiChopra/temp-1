-- Which business date (if any) an unpaids run has already been used for. A run is used once:
-- the latest run that has not been used yet belongs to the current business date.
SELECT business_date
  FROM unpaid_daily_feed
 WHERE feed_id         = :feedId
   AND unpaids_run_ref = :unpaidsRunRef
 ORDER BY business_date DESC
 FETCH FIRST 1 ROW ONLY
