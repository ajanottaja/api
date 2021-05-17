-- CREATE TABLE IF NOT EXISTS work_intervals (
--     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
--     interval_start TIMESTAMPTZ NOT NULL,
--     interval_stop TIMESTAMPTZ,
--     workday_id UUID references workdays(id),
--     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
--     updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
--     UNIQUE(interval_start, interval_stop, workday_id),
--     CHECK ( interval_start < interval_stop ),
--     CONSTRAINT overlapping_intervals EXCLUDE USING GIST (
--         workday_id WITH =,
--         TSTZRANGE(interval_start, interval_stop) WITH &&
--     )
-- );

CREATE TABLE IF NOT EXISTS work_intervals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    interval TSTZRANGE,
    workday_id UUID references workdays(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(workday_id, interval),
    CONSTRAINT work_interval_lower_not_null_ck CHECK (lower(interval) IS NOT NULL),
    CONSTRAINT work_interval_lower_not_infinity_ck CHECK ( lower(interval) > '-infinity' ),
    CONSTRAINT work_interval_upper_not_infinity_ck CHECK ( lower(interval) < 'infinity' ),
    CONSTRAINT work_interval_non_overlapping_intervals EXCLUDE USING GIST (
        workday_id WITH =,
        interval WITH &&
    )
);

--;;

CREATE TRIGGER set_timestamp_work_intervals
BEFORE UPDATE ON work_intervals
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_timestamp();


