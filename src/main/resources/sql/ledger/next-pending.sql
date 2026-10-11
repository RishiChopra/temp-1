-- The next pending entries for a feed, skipping any that are waiting to retry.
-- Ordered by loan so a loan's events leave in order (they share a Service Bus session).
SELECT ledger_id, event_id, loan_book, origin, source_table, source_ref, loan_number,
       unpaid_date, event_type, business_date, created_at
  FROM unpaid_event_ledger
 WHERE status       = 'PENDING'
   AND source_table = :sourceTable
   AND event_type   = :eventType
   AND (next_attempt_at IS NULL OR next_attempt_at <= SYSTIMESTAMP)
 ORDER BY loan_number, unpaid_date, ledger_id
 FETCH FIRST :limit ROWS ONLY
