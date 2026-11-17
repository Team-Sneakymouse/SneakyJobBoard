# PostgreSQL / SneakyDB cutover

## Runtime contract

SneakyJobBoard requires the joined **SneakyDB** runtime with migration support (`sneakydb-api`; see `build.gradle.kts`). The API is compile-only and is not bundled, nor are a JDBC driver or connection pool. Configure credentials, TLS and connection limits in SneakyDB, not this plugin.

```text
SneakyDB.migrate(sneakyjobboard) -> SneakyDB.whenReady
  read-only canonical-column preflight
    startup job reconciliation + online-player advert restoration
listing/edit/end (main-thread snapshot, preassigned record ID)
  per-table/record ordered canonical INSERT/UPDATE
    PostgreSQL trigger -> durable outbox -> hooks Discord/phone delivery
```

The plugin applies its own schema through SneakyDB migrations (`src/resources/db/migrations/`; see `docs/specs/sneakydb-migrations.md`). It does not write the outbox directly nor call a notification HTTP endpoint. Skin images retain their existing HTTP download behavior, with bounded connection/read timeouts and worker-only image processing. All player, location, placeholder, category, inventory and manager access happens on the Paper thread. Queued results are fenced against manager close, stale requests, logout/reconnect and replacing an active job/advert.

`database-schema` defaults to `lom`; only lowercase SQL identifiers matching `[a-z_][a-z0-9_]{0,62}` are accepted. Table names are fixed: `lom2_listed_jobs` and `lom2_adverts`. All values are JDBC parameters and camelCase identifiers are quoted. Queries and updates require the exact positive Paper backend listening port, **not the proxy port**. Backends sharing these tables must use distinct ports.

## Frozen schema authority

Canonical DDL is **`V001__jobboard.sql`** in this repository (ported from the former `psql-hooks/sql/jobboard.sql`). Field semantics still trace to the `metadata/collections-20261025T095957Z.json` snapshot in `Team-Sneakymouse/pocketbase` and the reviewed cross-service contract. This is **snapshot verification, not live metadata/DB verification**. Migration notes previously described a jobs table as ready; this plugin does not assume it already exists.

- IDs are consumer-generated 15-character lowercase alphanumeric text; imports retain their IDs. INSERT uses `RETURNING id`. Job/advert UUIDs remain text with no new uniqueness assumption.
- Jobs retain `serverPort`, UUID/category/poster/display/icon/location fields, epoch-millisecond `startTime`/`durationMillis`, tracking/persist, name/description, Discord icon/message ID and end time/reason. `created`/`updated` are database-managed timestamps. `endTime` remains numeric, not an integer-only schema conversion.
- Adverts retain UUID, category, `posteruuid`, poster display, name/description, material/model data, enabled/deleted and timestamps. `serverPort integer default 0` is the deliberate ownership extension. The integer Minecraft UI projection of imported numeric model data does not overwrite its original fractional value on unrelated edits; explicitly changing the model selects an integer value.
- Advert icons are stored as an **item-model id** in the additive `"iconItemModel" text NOT NULL DEFAULT ''` column (see below). The numeric `iconCustomModelData` column is now legacy read-only data: the plugin never writes it.
- The plugin never updates `discordMessageId`. Hooks owns the atomic message-ID-only acknowledgement. Ending jobs updates only end fields and requires both ID and backend port.

## Operator sequence — do not run as part of a plugin build

1. Coordinate a maintenance window and stop the old PocketBase writer and realtime Discord consumer. Back up both stores and freeze writes before the final copy. Do not run both writer generations.
2. Ensure schema `lom` exists. On **new** databases, SneakyDB applies `V001__jobboard.sql` at startup (`migrations.mode: apply` on one server). On **Live** that already ran the former `psql-hooks` DDL, record V001 as applied before the first jar that calls `migrate` (see `docs/specs/sneakydb-migrations.md`). Grant the runtime role schema `USAGE`, table SELECT/INSERT/UPDATE on jobs/adverts, and the reviewed outbox trigger permissions (`sql/jobboard-plugin-grants.sql` in psql-hooks, after replacing the role name).
3. Import **all historical jobs and adverts**, preserving IDs, UUIDs, timestamps, end fields, numeric model data and Discord message IDs. Suppress/install delivery triggers after copy: historical creates must not generate notifications automatically. An explicit operator seed is required if active imported missing-message jobs should be announced.
4. **Explicitly assign legacy adverts to a verified backend port.** Previous adverts had no ownership field; imported `serverPort = 0`/NULL rows remain invisible and uneditable by all backends until an operator establishes ownership. Do not assign them automatically to the first server to boot. Existing jobs with missing/zero port likewise remain unowned. This is an intentional policy change, not data deletion.
5. Rename presentation settings without changing their values: `pocketbase-poster` -> `database-poster`, `pocketbase-location` -> `database-location`. Remove old collection/records-URL settings and set `database-schema: lom`. Presentation settings also supply invitation/job dispatch locations. Old keys are not silently used as connection configuration; manually copying them preserves customization.
6. Install the matching SneakyDB runtime and this plugin only after schema/permissions/import checks. Enable hooks' PostgreSQL outbox worker and the updated Discord claim/ack consumer together. The externally delivered live backend is port **50001**; dev/legacy records must never reach Discord/phone outputs. No shared-secret HTTP configuration belongs in SneakyJobBoard.
7. Validate read-only preflight and startup reconciliation; then explicitly verify in-game and end-to-end delivery below. Do not retire PocketBase until soak and rollback approval.

No step above has been performed by this change. No remote database writes, import, deployment, merge or restart is part of verification.

## Advert icon item models (additive column + lazy conversion)

Adverts used to store an integer `iconCustomModelData`; the integer cannot be applied any more (`setCustomModelData(int)` is gone, see the Paper items policy). The new `"iconItemModel"` column holds a namespaced item-model id such as `lom:icons/jobboard/bluefist`, shown on `iconMaterial` (the carrier).

**Schema.** V001 creates `"iconItemModel" text NOT NULL DEFAULT ''` on `lom2_adverts`. Nothing else about delivery/outbox changes: adverts have no triggers.

**Startup.** The read-only preflight probes `SELECT "iconItemModel" ... WHERE false`. If migrations have not run and the column is missing, the plugin still starts for local UI work, but cannot persist a newly chosen advert icon until migrations apply.

**Conversion of existing rows.** There is no bulk startup rewrite and no DDL/DML outside this server's own rows. Instead, when an owned advert row is loaded (player join, re-enable) the plugin resolves its icon:

1. non-blank `iconItemModel` wins;
2. otherwise a positive legacy `iconCustomModelData` (fractional values truncate to the integer, as before) is converted with the stored `iconMaterial` as carrier, using the bundled `legacy-item-models.json` (generated from craftengine-resources' `legacy-item-models.json` by `tools/generate-legacy-item-models.py`; an open-ended last route covers every higher integer, as the pack does);
3. if the column exists the converted id is written back with one owned-row UPDATE (`iconItemModel` only), ordered after the record's other writes and fenced by `serverPort` like every update, so the conversion is idempotent and each row converts once;
4. an integer with no mapping logs a warning (record ID only) and the advert shows its category icon.

Rows of offline players, deleted rows and rows owned by other ports are converted when (and only when) their owner backend loads them; until then they keep resolving through step 2, so the stored integer stays the source of truth for them. The integer column is never cleared, so rolling back to the previous plugin build shows (at worst) the old integer icons for unconverted rows and the plain material for rows created after the upgrade.

MagicSpells toast spells (`jobboard-invitation-received`, `advert-dispatch-other`, `jobboard-listed`, `jobboard-dispatch-other`) receive quoted `"<material>" "<item model>"` (empty model is `""` so later args do not shift). Job categories configured with only a legacy integer resolve it through the bundled legacy mapping for the toast. The matching spell definitions live in `MagicSpells_Scripting` (`spells-city-jobBoard.yml`) and must ship with this argument shape.

## Readiness, ordering and failure behavior

Writes and reads wait for connection readiness and read-only column preflight; there is no startup DDL and no blocking JDBC work on the Paper thread. Pre-boot ephemeral jobs end with `restart`; expired owned persistent jobs end with `expired`; valid persistent jobs restore even if created during the current JVM boot. Invalid persistent category/location rows only get restart-ended if older than boot. Shutdown removes local jobs without ending canonical rows.

Record IDs and all Bukkit-derived values are captured before dispatch. An immediate create->end or advert create->edit/delete cannot overtake its insert, even while readiness or the skin image is pending. Failed/uncertain writes are **not automatically retried** and poison dependent writes for that record until manager restart. Sanitized operation diagnostics omit SQL parameters and JDBC exception details. Availability/preflight failure leaves the local UI functional but does not assert persistence/delivery success; operators must repair the cause and reconcile uncertain writes before re-enable. Closing fences new/deferred operations but cannot retract a SQL operation already admitted to SneakyDB.

## Verification boundary / in-game checklist

Automated tests use the actual repository SQL builder and manager/UI paths with mocked SneakyDB/Bukkit boundaries; these are **not a live PostgreSQL or Paper test**. Cross-service PostgreSQL DDL/trigger/claim integration belongs to the companion hooks change.

- Start before/after delayed SneakyDB readiness; verify failures are visible without credentials in logs and no DDL permissions are needed.
- List a job then immediately unlist/expire; inspect canonical create/end order and Discord/phone work. Preserve names/descriptions, player face icon, district placeholders and tracking updates.
- Restart and same-JVM re-enable with owned persistent/ephemeral, expired and invalid-category/location jobs; confirm no foreign/legacy backend modifications.
- Open/relist the nine unique newest owned history entries; log out or issue a newer request before completion and ensure no stale inventory opens.
- Create/edit/enable/disable/delete adverts immediately, then reconnect/re-enable; check icons (item models, incl. rows that only had `iconCustomModelData` converting once), categories, invitation delivery and dispatch. Confirm zero/NULL legacy adverts stay hidden before explicit operator assignment and foreign adverts never load.
- Test DB outage/failed create and inspect uncertain state manually before retrying. Exercise imported existing Discord IDs, create/end notification order and hooks' phone retry policy separately.
