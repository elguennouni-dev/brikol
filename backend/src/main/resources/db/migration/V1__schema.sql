-- Sanai3i schema. Written to run on MySQL 8 and on H2 (MODE=MySQL, used by dev/tests).
-- Payments are cash only and happen outside the app, so there is no payment table.

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone         VARCHAR(20)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(120) NOT NULL,
  user_role     VARCHAR(20)  NOT NULL,
  city          VARCHAR(80)  NOT NULL,
  created_at    DATETIME     NOT NULL,
  CONSTRAINT uq_users_phone UNIQUE (phone)
);

CREATE TABLE categories (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(30) NOT NULL,
  name_ar    VARCHAR(60) NOT NULL,
  name_fr    VARCHAR(60) NOT NULL,
  sort_order INT         NOT NULL DEFAULT 0,
  CONSTRAINT uq_categories_code UNIQUE (code)
);

CREATE TABLE artisan_profiles (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT       NOT NULL,
  category_id      BIGINT       NOT NULL,
  name_ar          VARCHAR(120),
  name_fr          VARCHAR(120),
  trade_ar         VARCHAR(80),
  trade_fr         VARCHAR(80),
  neighborhood_ar  VARCHAR(80),
  neighborhood_fr  VARCHAR(80),
  city             VARCHAR(80)  NOT NULL,
  bio_ar           VARCHAR(1000),
  bio_fr           VARCHAR(1000),
  years_experience INT          NOT NULL DEFAULT 0,
  jobs_done        INT          NOT NULL DEFAULT 0,
  verified         BOOLEAN      NOT NULL DEFAULT FALSE,
  available_today  BOOLEAN      NOT NULL DEFAULT TRUE,
  whatsapp         VARCHAR(20),
  created_at       DATETIME     NOT NULL,
  CONSTRAINT uq_artisan_user UNIQUE (user_id),
  CONSTRAINT fk_artisan_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_artisan_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
CREATE INDEX idx_artisan_category ON artisan_profiles (category_id);
CREATE INDEX idx_artisan_city ON artisan_profiles (city);

CREATE TABLE artisan_services (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  artisan_id BIGINT       NOT NULL,
  label_ar   VARCHAR(120) NOT NULL,
  label_fr   VARCHAR(120) NOT NULL,
  price      INT          NOT NULL,
  unit_ar    VARCHAR(20)  NOT NULL DEFAULT '',
  unit_fr    VARCHAR(20)  NOT NULL DEFAULT '',
  CONSTRAINT fk_service_artisan FOREIGN KEY (artisan_id) REFERENCES artisan_profiles (id)
);
CREATE INDEX idx_service_artisan ON artisan_services (artisan_id);

CREATE TABLE job_requests (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_id         BIGINT        NOT NULL,
  category_id       BIGINT        NOT NULL,
  description       VARCHAR(2000) NOT NULL,
  city              VARCHAR(80)   NOT NULL,
  neighborhood      VARCHAR(80),
  when_pref         VARCHAR(20)   NOT NULL,
  budget            INT           NOT NULL,
  status            VARCHAR(20)   NOT NULL,
  accepted_offer_id BIGINT,
  created_at        DATETIME      NOT NULL,
  CONSTRAINT fk_request_client FOREIGN KEY (client_id) REFERENCES users (id),
  CONSTRAINT fk_request_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
CREATE INDEX idx_request_client ON job_requests (client_id);
CREATE INDEX idx_request_status_category ON job_requests (status, category_id);

CREATE TABLE offers (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT       NOT NULL,
  artisan_id BIGINT       NOT NULL,
  price      INT          NOT NULL,
  eta_text   VARCHAR(60),
  message    VARCHAR(500),
  status     VARCHAR(20)  NOT NULL,
  created_at DATETIME     NOT NULL,
  CONSTRAINT uq_offer_request_artisan UNIQUE (request_id, artisan_id),
  CONSTRAINT fk_offer_request FOREIGN KEY (request_id) REFERENCES job_requests (id),
  CONSTRAINT fk_offer_artisan FOREIGN KEY (artisan_id) REFERENCES artisan_profiles (id)
);

CREATE TABLE reviews (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  artisan_id BIGINT   NOT NULL,
  client_id  BIGINT   NOT NULL,
  request_id BIGINT,
  rating     INT      NOT NULL,
  body       VARCHAR(1000),
  created_at DATETIME NOT NULL,
  CONSTRAINT uq_review_request UNIQUE (request_id),
  CONSTRAINT fk_review_artisan FOREIGN KEY (artisan_id) REFERENCES artisan_profiles (id),
  CONSTRAINT fk_review_client FOREIGN KEY (client_id) REFERENCES users (id)
);
CREATE INDEX idx_review_artisan ON reviews (artisan_id);
