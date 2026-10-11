-- A manual feed gets a new daily feed for each business date; once the current one is open, earlier
-- ones are closed (unless an execution is still actively working on one).
UPDATE unpaid_daily_feed
   SET status       = 'CLOSED',
       completed_at = NVL(completed_at, SYSTIMESTAMP)
 WHERE feed_id       = :feedId
   AND origin        = 'MANUAL'
   AND business_date < :businessDate
   AND (status IN ('NEW', 'DONE', 'FAILED')
        OR (status = 'IN_PROGRESS' AND claimed_at < SYSTIMESTAMP - NUMTODSINTERVAL(:claimExpirySeconds, 'SECOND')))
