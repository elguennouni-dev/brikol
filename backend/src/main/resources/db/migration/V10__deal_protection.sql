-- Protecting both sides of a job without handling the money (it stays cash):
-- written agreement, extras and advances confirmed by the other side, a 4-digit code to close the job,
-- a 7-day warranty, a "problem" button that reaches the team, and strikes that suspend repeat offenders.

ALTER TABLE users ADD COLUMN suspended_until DATETIME;

ALTER TABLE job_requests ADD COLUMN done_code      VARCHAR(4);
ALTER TABLE job_requests ADD COLUMN code_attempts  INT NOT NULL DEFAULT 0;
ALTER TABLE job_requests ADD COLUMN arrived_at     DATETIME;
ALTER TABLE job_requests ADD COLUMN finished_at    DATETIME;
ALTER TABLE job_requests ADD COLUMN done_at        DATETIME;
ALTER TABLE job_requests ADD COLUMN done_by        VARCHAR(10);
ALTER TABLE job_requests ADD COLUMN warranty_until DATETIME;

-- EXTRA: more work at an extra price (artisan proposes, client answers)
-- ADVANCE: cash given before the end, usually for materials (one side declares it, the other confirms)
CREATE TABLE job_items (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id   BIGINT       NOT NULL,
  kind         VARCHAR(10)  NOT NULL,
  label        VARCHAR(120),
  amount       INT          NOT NULL,
  status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
  created_by   VARCHAR(10)  NOT NULL,
  media_id     VARCHAR(36),
  created_at   DATETIME     NOT NULL,
  answered_at  DATETIME
);
CREATE INDEX idx_job_items_request ON job_items (request_id);

-- PROBLEM (either side, handled by the team) or WARRANTY (client, within 7 days of the end)
CREATE TABLE job_cases (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id   BIGINT        NOT NULL,
  kind         VARCHAR(10)   NOT NULL,
  reason       VARCHAR(20)   NOT NULL,
  opened_by    BIGINT        NOT NULL,
  opened_role  VARCHAR(10)   NOT NULL,
  details      VARCHAR(1000),
  media_id     VARCHAR(36),
  status       VARCHAR(12)   NOT NULL DEFAULT 'OPEN',
  resolution   VARCHAR(500),
  handled_by   BIGINT,
  created_at   DATETIME      NOT NULL,
  updated_at   DATETIME      NOT NULL,
  handled_at   DATETIME
);
CREATE INDEX idx_job_cases_request ON job_cases (request_id);
CREATE INDEX idx_job_cases_status ON job_cases (status, created_at);

CREATE TABLE strikes (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT       NOT NULL,
  request_id  BIGINT,
  case_id     BIGINT,
  reason      VARCHAR(20)  NOT NULL,
  created_by  BIGINT,
  removed     BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  DATETIME     NOT NULL
);
CREATE INDEX idx_strikes_user ON strikes (user_id, created_at);
