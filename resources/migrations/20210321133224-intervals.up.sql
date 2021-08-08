CREATE TABLE IF NOT EXISTS intervals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    interval TSTZRANGE,
    account UUID references accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(account, interval),
    CONSTRAINT interval_lower_not_null_ck CHECK (lower(interval) IS NOT NULL),
    CONSTRAINT interval_lower_not_infinity_ck CHECK ( lower(interval) > '-infinity' ),
    CONSTRAINT interval_upper_not_infinity_ck CHECK ( lower(interval) < 'infinity' ),
    CONSTRAINT interval_non_overlapping_intervals EXCLUDE USING GIST (
        account WITH =,
        interval WITH &&
    )
);

--;;

CREATE TRIGGER set_timestamp_intervals
BEFORE UPDATE ON intervals
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();


