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
| `V001__jobboard.sql` | Canonical DDL ported from former `psql-hooks/sql/jobboard.sql`: jobs, adverts (incl. `iconItemModel` and `serverPort`), outbox, deliveries, phone receipts, touch/capture functions and triggers. No transaction control, no `CREATE SCHEMA`, plain `CREATE` for clean init. Omits the operator-only fail-closed `information_schema` type guard (Live adoption does not re-run this script). |

## Runtime

On enable, `SneakyJobBoard` registers `SneakyDBProvider.getApi().migrate(IDENTIFIER, SneakyJobBoard::class.java)` and `DatabaseManager` / `JobBoardRepository` chain that future before `whenReady()` and table preflight.

## Tests

`JobBoardMigrationTest` (tag `postgres`) applies `V001__jobboard.sql` to an empty `lom` schema, then exercises `JobBoardRepository` inserts/updates. Run with `./gradlew postgresTest`.

## Operator adoption (Live)

1. Ensure schema `lom` exists and SneakyDB's role has `USAGE` and `CREATE` on it (see [SneakyDB migrations](https://github.com/Team-Sneakymouse/SneakyDB#migrations)).
2. If Live already matches the former `jobboard.sql` DDL (applied by operator), record `sneakyjobboard` version `1` in `sneakydb.schema_migrations` with the jar's V001 checksum **before** the first server starts with a build that calls `migrate` (SneakyDB **Existing tables**).
3. New databases: create `lom`, deploy SneakyDB with `migrations.mode: apply` on one server; V001 runs at first restart.
4. `psql-hooks` no longer ships duplicate CREATE DDL; hooks tests bootstrap from this plugin's `V001__jobboard.sql`.