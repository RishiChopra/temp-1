-- Service Bus did not accept these entries: try again later, waiting longer each time
-- (backoff x 2^attempts, capped). After the last attempt the entry is parked as FAILED for an operator.
UPDATE unpaid_event_ledger
   SET attempts        = attempts + 1,
       last_error      = :error,
       status          = CASE WHEN attempts + 1 >= :maxSendAttempts THEN 'FAILED' ELSE 'PENDING' END,
       next_attempt_at = SYSTIMESTAMP
                         + NUMTODSINTERVAL(LEAST(:backoffSeconds * POWER(2, attempts), :maxBackoffSeconds), 'SECOND')
 WHERE ledger_id IN (:ids)
   AND status = 'PENDING'
