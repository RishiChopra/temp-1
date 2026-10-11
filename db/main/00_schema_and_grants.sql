-- Monolith MAIN DB: a dedicated schema for the extractor's daily-feed table (run as DBA).
-- The extractor never writes to monolith-owned tables.
-- Replace MAIN_OWNER with the schema that owns I_SCHEDULED_JOBS.

CREATE USER unpaids_extractor IDENTIFIED BY "&extractor_password"
  DEFAULT TABLESPACE users QUOTA 200M ON users;
GRANT CREATE SESSION, CREATE TABLE, CREATE SYNONYM TO unpaids_extractor;

-- Read-only access to the monolith's scheduled-job table (PL unpaids = job 133, CL unpaids = job 91)
GRANT SELECT ON main_owner.i_scheduled_jobs TO unpaids_extractor;
CREATE SYNONYM unpaids_extractor.i_scheduled_jobs FOR main_owner.i_scheduled_jobs;
