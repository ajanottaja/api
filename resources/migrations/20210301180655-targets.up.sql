CREATE TABLE IF NOT EXISTS targets (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    date DATE NOT NULL,
    account UUID references accounts(id),
    duration INTERVAL NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(date, account)
);

--;;

CREATE TRIGGER set_timestamp_targets
BEFORE UPDATE ON targets
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();