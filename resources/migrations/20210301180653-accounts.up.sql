CREATE TABLE IF NOT EXISTS accounts (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    auth_zero_id VARCHAR(128) UNIQUE,
    email TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

--;;

CREATE TRIGGER set_timestamp_accounts
BEFORE UPDATE ON accounts
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();