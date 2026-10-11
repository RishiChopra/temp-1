-- What is left for a feed after publishing: still pending (including waiting to retry) and failed.
SELECT COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending,
       COUNT(CASE WHEN status = 'FAILED'  THEN 1 END) AS failed
  FROM unpaid_event_ledger
 WHERE source_table = :sourceTable
   AND event_type   = :eventType
   AND status IN ('PENDING', 'FAILED')
