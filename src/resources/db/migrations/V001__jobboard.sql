-- SneakyJobBoard schema v1: listed jobs, adverts, durable create/end outbox, delivery tables, and capture triggers.
-- Operator must create schema `lom` before the first apply (scripts must not CREATE SCHEMA).
-- The fail-closed column type guard from the former psql-hooks operator script is omitted here; adoption on Live
-- marks this script applied without running it when the database already matches.

CREATE TABLE lom.lom2_listed_jobs (
 id text PRIMARY KEY CHECK (id ~ '^[a-z0-9]{15}$'),
 "serverPort" integer NOT NULL DEFAULT 0,
 uuid text NOT NULL DEFAULT '',
 category text NOT NULL DEFAULT '',
 poster text NOT NULL DEFAULT '',
 "posterDisplayString" text NOT NULL DEFAULT '',
 "posterIconBase64" text NOT NULL DEFAULT '',
 location text NOT NULL DEFAULT '',
 "locationDisplayString" text NOT NULL DEFAULT '',
 "startTime" bigint NOT NULL DEFAULT 0,
 "durationMillis" bigint NOT NULL DEFAULT 0,
 tracking boolean NOT NULL DEFAULT false,
 persist boolean NOT NULL DEFAULT false,
 name text NOT NULL DEFAULT '',
 description text NOT NULL DEFAULT '',
 "discordEmbedIcon" text NOT NULL DEFAULT '',
 "discordMessageId" text,
 "endTime" numeric NOT NULL DEFAULT 0,
 "endReason" text,
 created timestamptz NOT NULL DEFAULT now(),
 updated timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lom.lom2_adverts (
 id text PRIMARY KEY CHECK (id ~ '^[a-z0-9]{15}$'),
 uuid text NOT NULL DEFAULT '',
 category text NOT NULL DEFAULT '',
 posteruuid text NOT NULL DEFAULT '',
 "posterDisplayString" text NOT NULL DEFAULT '',
 name text NOT NULL DEFAULT '',
 description text NOT NULL DEFAULT '',
 "iconMaterial" text NOT NULL DEFAULT '',
 "iconCustomModelData" numeric NOT NULL DEFAULT 0,
 "iconItemModel" text NOT NULL DEFAULT '',
 enabled boolean NOT NULL DEFAULT false,
 deleted boolean NOT NULL DEFAULT false,
 "serverPort" integer NOT NULL DEFAULT 0,
 created timestamptz NOT NULL DEFAULT now(),
 updated timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lom.job_outbox (
 event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 job_id text NOT NULL REFERENCES lom.lom2_listed_jobs (id),
 kind text NOT NULL CHECK (kind IN ('create', 'end')),
 payload jsonb NOT NULL,
 created timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lom.job_deliveries (
 event_id bigint NOT NULL REFERENCES lom.job_outbox (event_id),
 target text NOT NULL CHECK (target IN ('discord', 'phone')),
 lease_token text,
 lease_until timestamptz,
 attempts integer NOT NULL DEFAULT 0,
 done_at timestamptz,
 failed_at timestamptz,
 last_error text,
 PRIMARY KEY (event_id, target)
);

CREATE INDEX job_outbox_job_order ON lom.job_outbox (job_id, event_id);

CREATE OR REPLACE FUNCTION lom.job_touch_updated() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.updated = now();
 RETURN NEW;
END
$$;

CREATE TRIGGER job_touch_updated BEFORE UPDATE ON lom.lom2_listed_jobs FOR EACH ROW EXECUTE FUNCTION lom.job_touch_updated();

CREATE TRIGGER advert_touch_updated BEFORE UPDATE ON lom.lom2_adverts FOR EACH ROW EXECUTE FUNCTION lom.job_touch_updated();

CREATE OR REPLACE FUNCTION lom.capture_job_event() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 k text;
 eid bigint;
BEGIN
 IF NEW."serverPort" <> 50001 THEN
  RETURN NEW;
 END IF;
 IF TG_OP = 'INSERT' THEN
  IF NEW."endTime" <> 0 THEN
   RETURN NEW;
  END IF;
  k = 'create';
 ELSIF OLD."endTime" = 0 AND NEW."endTime" <> 0 THEN
  k = 'end';
 ELSE
  RETURN NEW;
 END IF;
 INSERT INTO lom.job_outbox (job_id, kind, payload) VALUES (NEW.id, k, to_jsonb(NEW)) RETURNING event_id INTO eid;
 INSERT INTO lom.job_deliveries (event_id, target) VALUES (eid, 'discord');
 IF k = 'create' THEN
  INSERT INTO lom.job_deliveries (event_id, target) VALUES (eid, 'phone');
 END IF;
 RETURN NEW;
END
$$;

CREATE TRIGGER capture_job_event AFTER INSERT OR UPDATE ON lom.lom2_listed_jobs FOR EACH ROW EXECUTE FUNCTION lom.capture_job_event();

CREATE TABLE lom.job_phone_receipts (
 event_id bigint NOT NULL REFERENCES lom.job_outbox (event_id),
 token text NOT NULL,
 delivered_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (event_id, token)
);