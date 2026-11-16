ALTER TABLE users ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN blocked_reason VARCHAR(200);
ALTER TABLE users ADD COLUMN last_login_at DATETIME;

-- No foreign keys on purpose: the audit trail must survive whatever happens to the rows it talks about.
CREATE TABLE audit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id    BIGINT,
  actor_name  VARCHAR(160),
  action      VARCHAR(40)  NOT NULL,
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      VARCHAR(500),
  ip          VARCHAR(64),
  created_at  DATETIME     NOT NULL
);
CREATE INDEX idx_audit_created ON audit_log (created_at);
CREATE INDEX idx_audit_action ON audit_log (action);
CREATE INDEX idx_audit_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_actor ON audit_log (actor_id);

CREATE TABLE app_settings (
  skey VARCHAR(60)  PRIMARY KEY,
  sval VARCHAR(500) NOT NULL
);
INSERT INTO app_settings (skey, sval) VALUES ('registrationsOpen', 'true');
INSERT INTO app_settings (skey, sval) VALUES ('bannerMessage', '');
