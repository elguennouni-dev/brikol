CREATE TABLE notifications (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NOT NULL,
  kind       VARCHAR(30)  NOT NULL,
  request_id BIGINT,
  actor_ar   VARCHAR(160),
  actor_fr   VARCHAR(160),
  detail     VARCHAR(160),
  is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at DATETIME     NOT NULL,
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_notification_user ON notifications (user_id, id);
