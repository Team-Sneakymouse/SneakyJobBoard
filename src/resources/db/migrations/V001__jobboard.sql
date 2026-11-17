-- SneakyJobBoard schema v1: Live baseline for lom2_listed_jobs and lom2_adverts (2026-11-17).
-- Compared with team_sneakymouse (Live) via pg_dump / information_schema on that date.
-- Operator provisions schema `lom`. Scripts never CREATE SCHEMA.
-- Shared lom.touch_updated() already exists on Live; CREATE OR REPLACE keeps empty-DB tests working.

CREATE OR REPLACE FUNCTION lom.touch_updated() RETURNS trigger
LANGUAGE plpgsql
SET search_path TO pg_catalog
AS $$
BEGIN
 NEW.updated = clock_timestamp();
 RETURN NEW;
END
$$;

CREATE TABLE lom.lom2_listed_jobs (
 id text PRIMARY KEY,
 "serverPort" bigint NOT NULL DEFAULT 0,
 uuid text NOT NULL,
 category text NOT NULL,
 poster text NOT NULL,
 "posterDisplayString" text NOT NULL,
 "posterIconBase64" text NOT NULL,
 location text NOT NULL,
 "locationDisplayString" text NOT NULL,
 "startTime" bigint NOT NULL DEFAULT 0,
 "durationMillis" bigint NOT NULL DEFAULT 0,
 tracking boolean NOT NULL DEFAULT false,
 persist boolean NOT NULL DEFAULT false,
 name text NOT NULL,
 description text NOT NULL,
 "discordEmbedIcon" text NOT NULL,
 "discordMessageId" text,
 "endTime" bigint NOT NULL DEFAULT 0,
 "endReason" text,
 created timestamptz NOT NULL DEFAULT now(),
 updated timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lom.lom2_adverts (
 id text PRIMARY KEY,
 uuid text NOT NULL,
 category text,
 posteruuid text NOT NULL,
 "posterDisplayString" text NOT NULL,
 name text NOT NULL,
 description text NOT NULL,
 "iconMaterial" text,
 "iconCustomModelData" bigint DEFAULT 0,
 enabled boolean DEFAULT false,
 deleted boolean DEFAULT false,
 created timestamptz NOT NULL DEFAULT now(),
 updated timestamptz NOT NULL DEFAULT now(),
 "serverPort" integer NOT NULL DEFAULT 0,
 "iconItemModel" text NOT NULL DEFAULT ''
);

CREATE INDEX lom2_listed_jobs_created_idx ON lom.lom2_listed_jobs (created DESC);
CREATE INDEX lom2_listed_jobs_endtime_idx ON lom.lom2_listed_jobs ("endTime");
CREATE INDEX lom2_adverts_created_idx ON lom.lom2_adverts (created DESC);
CREATE INDEX lom2_adverts_posteruuid_deleted_idx ON lom.lom2_adverts (posteruuid, deleted);

CREATE TRIGGER lom2_listed_jobs_touch_updated
 BEFORE UPDATE ON lom.lom2_listed_jobs
 FOR EACH ROW EXECUTE FUNCTION lom.touch_updated();

CREATE TRIGGER lom2_adverts_touch_updated
 BEFORE UPDATE ON lom.lom2_adverts
 FOR EACH ROW EXECUTE FUNCTION lom.touch_updated();
