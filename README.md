# SneakyJobBoard

A Paper plugin for the LoM2 job board, persistent jobs, job history, adverts and invitations.

## PostgreSQL persistence

Requires the **SneakyDB** Paper runtime and published API `2026.10.26-fd4e29e90ad0`. Connection credentials stay in SneakyDB. The consumer JAR does not bundle its API, PostgreSQL driver or connection pool.

See [the PostgreSQL migration/runbook](docs/postgresql-migration.md) before cutover, including operator-owned DDL/imports, trigger-based Discord/phone delivery, configuration migration, failure behavior and the in-game checklist. This repository does not execute a live migration or deploy services.

```text
DatabaseManager (Paper-thread snapshots / fenced UI callbacks)
  JobBoardRepository (readiness + read-only preflight + ordered parameterized SQL)
    SneakyDB -> lom.lom2_listed_jobs / lom.lom2_adverts
      operator-installed database triggers -> hooks durable delivery
```

All reads and updates use the **exact nonzero Paper backend listening port**, not the proxy port. A defensive numeric ownership check precedes job restoration, history deduplication/limits and advert loading. Zero/NULL/missing-port legacy records remain unowned. Legacy adverts require explicit operator backend assignment before becoming visible. Shared backends must have distinct listening ports.

Pre-boot ephemeral jobs and expired persistent jobs are ended during startup reconciliation; valid persistent jobs can restore on the current JVM boot as well. Shutdown removes local state without database end writes. History keeps the nine newest unique owned listings as fresh relisting templates. Adverts preserve categories, icons, edit/enable/soft-delete, invitations and dispatch, with the same ownership fencing as jobs.

## Build and tests

```sh
./gradlew build --max-workers=1 -Dorg.gradle.jvmargs='-Xmx512m -XX:ActiveProcessorCount=2' -Pkotlin.compiler.execution.strategy=in-process
```

Automated tests mock SneakyDB and Bukkit boundaries and exercise real repository SQL and manager/UI logic. They do not establish live database permissions, Paper loading or Discord/phone delivery.
