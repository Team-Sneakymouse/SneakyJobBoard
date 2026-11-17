# Spec: SneakyDB migrations for SneakyJobBoard

## Goal

Adopt [team database migrations](https://github.com/Team-Sneakymouse/team-development-policy/blob/main/policy/database-migrations.md) for job/advert tables in schema `lom` (`lom2_listed_jobs`, `lom2_adverts`, outbox/delivery/receipt tables, and capture triggers).

## Non-goals

- Do not create or drop schema `lom` in scripts (operator creates it).
- Do not change PocketBase snapshot field semantics, port isolation, or hooks' Discord/phone contract.

## Plugin id

`migrate` uses `sneakyjobboard` (`SneakyJobBoard.IDENTIFIER`).

## Scripts

| Script | Purpose |
|--------|---------|
| `V001__jobboard.sql` | **Live baseline (2026-11-17):** `lom2_listed_jobs` / `lom2_adverts` as on `team_sneakymouse`, plus Live indexes and `lom.touch_updated` triggers. No outbox. |
| `V002__jobboard_outbox_and_align.sql` | Align column types/nullability/checks with the plugin contract; add outbox, deliveries, phone receipts, and `capture_job_event`. **Breaking** retypes. |

## Evidence (V001 vs Live)

Compared on 2026-11-17 against Live (`team_sneakymouse`) with `pg_dump --schema-only` and `information_schema`. Live had no `job_outbox` / `job_deliveries` / `job_phone_receipts`, used `bigint` for jobs.`serverPort` and `endTime`, `bigint` nullable `iconCustomModelData`, and `lom.touch_updated` triggers named `lom2_*_touch_updated`. The former repo `jobboard.sql` (and the first merged V001) did not match that.

## Runtime

On enable, `SneakyJobBoard` registers `SneakyDBProvider.getApi().migrate(IDENTIFIER, SneakyJobBoard::class.java)` and `DatabaseManager` / `JobBoardRepository` chain that future before `whenReady()` and table preflight.

## Tests

`JobBoardMigrationTest` (tag `postgres`) applies every `db/migrations` script to an empty `lom` schema, then exercises `JobBoardRepository` inserts/updates. Run with `./gradlew postgresTest`.

## Operator adoption

1. Ensure schema `lom` exists and SneakyDB's role has `USAGE` and `CREATE` on it.
2. **Live / Staging** (tables match V001 today): record `sneakyjobboard` version `1` with a **null** checksum, then let `apply` fill it and run V002. Do **not** record V002 as applied.
3. **Dev** (already recorded the wrong V001 checksum and was altered toward that script): before deploying this jar, reset the job-board objects to the Live baseline (or restore from Live), `DELETE FROM sneakydb.schema_migrations WHERE plugin = 'sneakyjobboard'`, then record version `1` with null checksum as on Live. V002 then applies.
4. New empty databases: create `lom`, one server in `migrations.mode: apply`; V001 then V002 run at first restart.
