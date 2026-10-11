UPDATE unpaid_event_ledger
   SET status     = 'SENT',
       sent_at    = SYSTIMESTAMP,
       attempts   = attempts + 1,
       last_error = NULL
 WHERE ledger_id IN (:ids)
   AND status = 'PENDING'
