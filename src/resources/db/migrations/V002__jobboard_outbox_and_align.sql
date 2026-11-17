-- Bring Live-shaped V001 up to the durable outbox / capture contract and column shape the plugin expects.
-- Breaking: retypes lom2_listed_jobs.serverPort and endTime, and lom2_adverts.iconCustomModelData;
-- tightens nullability/defaults and adds id checks. Stop every sneakyjobboard server on a database
-- before the first jar with this script starts there.

ALTER TABLE lom.lom2_listed_jobs
 ALTER COLUMN "serverPort" TYPE integer USING "serverPort"::integer,
 ALTER COLUMN "endTime" TYPE numeric USING "endTime"::numeric,
 ALTER COLUMN uuid SET DEFAULT '',
 ALTER COLUMN category SET DEFAULT '',
 ALTER COLUMN poster SET DEFAULT '',
 ALTER COLUMN "posterDisplayString" SET DEFAULT '',
 ALTER COLUMN "posterIconBase64" SET DEFAULT '',
 ALTER COLUMN location SET DEFAULT '',
 ALTER COLUMN "locationDisplayString" SET DEFAULT '',
 ALTER COLUMN name SET DEFAULT '',
 ALTER COLUMN description SET DEFAULT '',
 ALTER COLUMN "discordEmbedIcon" SET DEFAULT '';

ALTER TABLE lom.lom2_listed_jobs
 ADD CONSTRAINT lom2_listed_jobs_id_check CHECK (id ~ '^[a-z0-9]{15}$');

UPDATE lom.lom2_adverts
 SET category = COALESCE(category, ''),
     "iconMaterial" = COALESCE("iconMaterial", ''),
     "iconCustomModelData" = COALESCE("iconCustomModelData", 0),
     enabled = COALESCE(enabled, false),
     deleted = COALESCE(deleted, false);

ALTER TABLE lom.lom2_adverts
 ALTER COLUMN "iconCustomModelData" TYPE numeric USING "iconCustomModelData"::numeric,
 ALTER COLUMN "iconCustomModelData" SET DEFAULT 0,
 ALTER COLUMN "iconCustomModelData" SET NOT NULL,
 ALTER COLUMN uuid SET DEFAULT '',
 ALTER COLUMN category SET DEFAULT '',
 ALTER COLUMN category SET NOT NULL,
 ALTER COLUMN posteruuid SET DEFAULT '',
 ALTER COLUMN "posterDisplayString" SET DEFAULT '',
 ALTER COLUMN name SET DEFAULT '',
 ALTER COLUMN description SET DEFAULT '',
 ALTER COLUMN "iconMaterial" SET DEFAULT '',
 ALTER COLUMN "iconMaterial" SET NOT NULL,
 ALTER COLUMN enabled SET DEFAULT false,
 ALTER COLUMN enabled SET NOT NULL,
 ALTER COLUMN deleted SET DEFAULT false,
 ALTER COLUMN deleted SET NOT NULL;

ALTER TABLE lom.lom2_adverts
 ADD CONSTRAINT lom2_adverts_id_check CHECK (id ~ '^[a-z0-9]{15}$');

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

CREATE TRIGGER capture_job_event
 AFTER INSERT OR UPDATE ON lom.lom2_listed_jobs
 FOR EACH ROW EXECUTE FUNCTION lom.capture_job_event();

CREATE TABLE lom.job_phone_receipts (
 event_id bigint NOT NULL REFERENCES lom.job_outbox (event_id),
 token text NOT NULL,
 delivered_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (event_id, token)
);
