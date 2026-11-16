-- Exact place chosen on the map by the client
ALTER TABLE job_requests ADD COLUMN lat DOUBLE;
ALTER TABLE job_requests ADD COLUMN lng DOUBLE;
ALTER TABLE job_requests ADD COLUMN address VARCHAR(200);
