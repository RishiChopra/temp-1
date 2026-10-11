-- Housekeeping: finished daily feeds past retention, a limited number per run.
-- Age is real time since the day was opened (not the business date, which test environments move).
DELETE FROM unpaid_daily_feed
 WHERE status IN ('DONE', 'CLOSED')
   AND opened_at < SYSTIMESTAMP - NUMTODSINTERVAL(:retentionDays, 'DAY')
   AND ROWNUM <= :batchSize
