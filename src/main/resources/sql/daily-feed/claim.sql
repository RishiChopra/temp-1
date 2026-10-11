-- Claim a daily feed for this execution. Exactly one execution can succeed (1 row updated).
-- Claimable: NEW or FAILED; a manual feed that is DONE (more approvals keep arriving during the day);
-- or IN_PROGRESS with an expired claim (the execution holding it died).
UPDATE unpaid_daily_feed
   SET status      = 'IN_PROGRESS',
       claimed_by  = :executionId,
       claimed_at  = SYSTIMESTAMP,
       last_run_at = SYSTIMESTAMP,
       attempts    = attempts + 1
 WHERE business_date = :businessDate
   AND feed_id       = :feedId
   AND attempts      < :maxAttempts
   AND (status IN ('NEW', 'FAILED')
        OR (status = 'DONE' AND origin = 'MANUAL')
        OR (status = 'IN_PROGRESS' AND claimed_at < SYSTIMESTAMP - NUMTODSINTERVAL(:claimExpirySeconds, 'SECOND')))
