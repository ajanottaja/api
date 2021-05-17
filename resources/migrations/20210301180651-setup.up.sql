CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

--;;

CREATE EXTENSION IF NOT EXISTS btree_gist;

--;;

CREATE OR REPLACE FUNCTION trigger_set_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;