-- Photos, videos and voice notes (requests), artisan documents and portfolio
CREATE TABLE media (
  id          VARCHAR(36)  PRIMARY KEY,
  owner_id    BIGINT       NOT NULL,
  purpose     VARCHAR(20)  NOT NULL,
  kind        VARCHAR(10)  NOT NULL,
  mime        VARCHAR(80)  NOT NULL,
  size_bytes  BIGINT       NOT NULL,
  file_name   VARCHAR(120) NOT NULL,
  request_id  BIGINT,
  artisan_id  BIGINT,
  created_at  DATETIME     NOT NULL
);
CREATE INDEX idx_media_request ON media (request_id);
CREATE INDEX idx_media_artisan ON media (artisan_id);
CREATE INDEX idx_media_owner ON media (owner_id, created_at);

-- Price bounds per trade (MAD) and the range fixed on each request
ALTER TABLE categories ADD COLUMN min_price INT NOT NULL DEFAULT 100;
ALTER TABLE categories ADD COLUMN max_price INT NOT NULL DEFAULT 50000;
UPDATE categories SET min_price = 150, max_price = 30000 WHERE code = 'paint';
UPDATE categories SET min_price = 100, max_price = 30000 WHERE code = 'wood';
UPDATE categories SET min_price = 100, max_price = 20000 WHERE code = 'mech';
UPDATE categories SET min_price = 150, max_price = 30000 WHERE code = 'metal';
UPDATE categories SET min_price = 80,  max_price = 15000 WHERE code = 'elec';
UPDATE categories SET min_price = 80,  max_price = 15000 WHERE code = 'plumb';
UPDATE categories SET min_price = 200, max_price = 40000 WHERE code = 'tile';
ALTER TABLE job_requests ADD COLUMN price_min INT;
ALTER TABLE job_requests ADD COLUMN price_max INT;

-- Negotiation on an offer: who spoke last and how many counter-offers
ALTER TABLE offers ADD COLUMN last_by VARCHAR(10) NOT NULL DEFAULT 'ARTISAN';
ALTER TABLE offers ADD COLUMN rounds INT NOT NULL DEFAULT 0;
ALTER TABLE offers ADD COLUMN first_price INT;
UPDATE offers SET first_price = price;

-- Artisan activation (existing artisans stay active)
ALTER TABLE artisan_profiles ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE artisan_profiles ADD COLUMN rejection_reason VARCHAR(300);
ALTER TABLE artisan_profiles ADD COLUMN cin VARCHAR(20);
ALTER TABLE artisan_profiles ADD COLUMN lat DOUBLE;
ALTER TABLE artisan_profiles ADD COLUMN lng DOUBLE;
ALTER TABLE artisan_profiles ADD COLUMN address VARCHAR(200);
ALTER TABLE artisan_profiles ADD COLUMN radius_km INT NOT NULL DEFAULT 15;
ALTER TABLE artisan_profiles ADD COLUMN avatar_id VARCHAR(36);
ALTER TABLE artisan_profiles ADD COLUMN submitted_at DATETIME;
ALTER TABLE artisan_profiles ADD COLUMN reviewed_at DATETIME;
