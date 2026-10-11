UPDATE unpaid_event_ledger
   SET status     = 'INVALID',
       last_error = :reason
 WHERE ledger_id = :id
   AND status = 'PENDING'
