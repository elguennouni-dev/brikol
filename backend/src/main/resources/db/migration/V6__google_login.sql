ALTER TABLE users ADD COLUMN email VARCHAR(160);
ALTER TABLE users ADD COLUMN google_sub VARCHAR(64);
CREATE UNIQUE INDEX uk_users_google_sub ON users (google_sub);
CREATE INDEX idx_users_email ON users (email);
