-- Housekeeping: published (or invalid) entries past retention, a limited number per run.
-- PENDING, FAILED and COLLISION entries are never deleted.
DELETE FROM unpaid_event_ledger
 WHERE status IN ('SENT', 'INVALID')
   AND created_at < SYSTIMESTAMP - NUMTODSINTERVAL(:retentionDays, 'DAY')
   AND ROWNUM <= :batchSize
