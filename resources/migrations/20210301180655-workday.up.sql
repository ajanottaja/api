CREATE TABLE IF NOT EXISTS workdays (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    work_date DATE NOT NULL,
    account_id UUID references accounts(id),
    work_duration INTERVAL NOT NULL, 
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(work_date, account_id)
);

--;;

CREATE TRIGGER set_timestamp_workdays
BEFORE UPDATE ON workdays
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();