-- Record how a claimed daily feed ended (DONE, FAILED or ATTENTION) and add this attempt's counts.
-- Only the execution holding the claim can do this.
UPDATE unpaid_daily_feed
   SET status          = :status,
       last_error      = :error,
       attempts        = CASE WHEN :status = 'DONE' THEN 0 ELSE attempts END,
       completed_at    = CASE WHEN :status = 'DONE' THEN SYSTIMESTAMP ELSE completed_at END,
       staged_count    = NVL(staged_count, 0)    + :staged,
       collision_count = NVL(collision_count, 0) + :collisions,
       sent_count      = NVL(sent_count, 0)      + :sent,
       invalid_count   = NVL(invalid_count, 0)   + :invalid
 WHERE business_date = :businessDate
   AND feed_id       = :feedId
   AND claimed_by    = :executionId
