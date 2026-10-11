-- The loan book's current business date, read exactly as the monolith reads it (PL or CL database).
-- Test environments move this date on; the extractor follows it.
-- TODO(confirm): the real name of eodc_date (function, package function or synonym) and that the
--                extractor user has EXECUTE on it (db/pl and db/cl grant scripts).
SELECT TRUNC(eodc_date) AS business_date
  FROM dual
