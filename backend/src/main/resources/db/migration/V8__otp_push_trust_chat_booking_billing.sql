-- SMS one-time codes (sign-up, password reset)
CREATE TABLE otp_codes (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone       VARCHAR(20)  NOT NULL,
  purpose     VARCHAR(12)  NOT NULL,
  code_hash   VARCHAR(100) NOT NULL,
  attempts    INT          NOT NULL DEFAULT 0,
  used        BOOLEAN      NOT NULL DEFAULT FALSE,
  expires_at  DATETIME     NOT NULL,
  created_at  DATETIME     NOT NULL
);
CREATE INDEX idx_otp_phone ON otp_codes (phone, created_at);
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- push notification tokens (Firebase Cloud Messaging: Android app and browsers)
CREATE TABLE device_tokens (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  token         VARCHAR(400) NOT NULL,
  platform      VARCHAR(12)  NOT NULL,
  lang          VARCHAR(2)   NOT NULL DEFAULT 'ar',
  created_at    DATETIME     NOT NULL,
  last_seen_at  DATETIME     NOT NULL
);
CREATE UNIQUE INDEX uk_device_token ON device_tokens (token);
CREATE INDEX idx_device_user ON device_tokens (user_id);

-- reports (users, requests, reviews, messages)
CREATE TABLE reports (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  reporter_id     BIGINT        NOT NULL,
  target_type     VARCHAR(12)   NOT NULL,
  target_id       BIGINT        NOT NULL,
  target_user_id  BIGINT,
  reason          VARCHAR(30)   NOT NULL,
  details         VARCHAR(1000),
  status          VARCHAR(12)   NOT NULL DEFAULT 'OPEN',
  resolution      VARCHAR(500),
  handled_by      BIGINT,
  created_at      DATETIME      NOT NULL,
  handled_at      DATETIME
);
CREATE INDEX idx_report_status ON reports (status, created_at);
CREATE INDEX idx_report_target_user ON reports (target_user_id);

-- the artisan rates the client after the job
CREATE TABLE client_reviews (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id  BIGINT        NOT NULL,
  artisan_id  BIGINT        NOT NULL,
  client_id   BIGINT        NOT NULL,
  rating      INT           NOT NULL,
  body        VARCHAR(1000),
  created_at  DATETIME      NOT NULL
);
CREATE UNIQUE INDEX uk_client_review_request ON client_reviews (request_id);
CREATE INDEX idx_client_review_client ON client_reviews (client_id);

-- chat between the client and the chosen artisan
CREATE TABLE messages (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id  BIGINT        NOT NULL,
  sender_id   BIGINT        NOT NULL,
  body        VARCHAR(2000) NOT NULL,
  read_at     DATETIME,
  created_at  DATETIME      NOT NULL
);
CREATE INDEX idx_message_request ON messages (request_id, id);

-- appointment agreed after acceptance
ALTER TABLE job_requests ADD COLUMN appointment_at DATETIME;
ALTER TABLE job_requests ADD COLUMN appointment_by VARCHAR(10);
ALTER TABLE job_requests ADD COLUMN appointment_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_requests ADD COLUMN reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- artisan subscription and "featured" placement, paid outside the app and recorded by an admin
ALTER TABLE artisan_profiles ADD COLUMN subscription_until DATETIME;
ALTER TABLE artisan_profiles ADD COLUMN featured_until DATETIME;
CREATE TABLE payments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  artisan_id   BIGINT       NOT NULL,
  kind         VARCHAR(12)  NOT NULL,
  months       INT          NOT NULL,
  amount       INT          NOT NULL,
  method       VARCHAR(30)  NOT NULL,
  note         VARCHAR(300),
  recorded_by  BIGINT       NOT NULL,
  created_at   DATETIME     NOT NULL
);
CREATE INDEX idx_payment_artisan ON payments (artisan_id, created_at);
INSERT INTO app_settings (skey, sval) VALUES ('monetizationEnabled', 'false');
INSERT INTO app_settings (skey, sval) VALUES ('subscriptionPrice', '99');
INSERT INTO app_settings (skey, sval) VALUES ('trialDays', '30');
INSERT INTO app_settings (skey, sval) VALUES ('featuredPrice', '49');
INSERT INTO app_settings (skey, sval) VALUES ('paymentInstructions', '');
