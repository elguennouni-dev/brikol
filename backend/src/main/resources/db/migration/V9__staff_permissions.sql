-- Administration team: one owner (super admin) and staff accounts with chosen permissions
ALTER TABLE users ADD COLUMN super_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN permissions VARCHAR(300);
