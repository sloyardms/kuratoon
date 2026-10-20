-- Baseline: extensions required by the data model (docs/spec/01-data-model.md).
-- Tables are created by later migrations.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;
