-- Daily feeds that may have work to do. claim.sql makes the final decision.
SELECT business_date, feed_id, status, attempts
  FROM unpaid_daily_feed
 WHERE business_date >= :fromDate
   AND attempts < :maxAttempts
   AND (status IN ('NEW', 'FAILED', 'IN_PROGRESS')
        OR (status = 'DONE' AND origin = 'MANUAL'))
 ORDER BY business_date, feed_id
