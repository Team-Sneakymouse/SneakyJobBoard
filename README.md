A Minecraft Paper plugin designed for managing the job board on the LoM2 server

## Startup reconciliation ownership

Startup reconciliation only fetches and processes job records whose `serverPort`
exactly matches the backend's Bukkit listening port. This applies to both ending
old ephemeral jobs and restoring or ending persistent jobs. The JVM boot boundary
still determines which ephemeral jobs are eligible for restart cleanup.

Legacy records with a missing or zero `serverPort` are treated as unowned: startup
reconciliation neither ends nor restores them. Assign their verified backend port
explicitly before expecting automatic reconciliation; no ownership is inferred from
categories, locations, or shared plugin configuration. Backends sharing a PocketBase
jobs collection must use distinct listening ports for this ownership scheme.

## Job history ownership

Job history uses the same exact listening-port ownership rule: the query retains
its player-name predicate and `-created` ordering, and only matching `serverPort`
rows can contribute to the nine most recent unique listings. A defensive numeric
comparison runs before parsing listing fields, deduplication, or slot allocation;
fractional values are not truncated into a match. Zero, missing, and null ports
are excluded, not treated as backwards-compatible history.

Existing jobs must be assigned their verified backend port before they appear in
history or participate in reconciliation. The database migration is an operator
responsibility; this plugin change does not execute a migration.

## Persistence / PocketBase path audit

All PocketBase operations in main source are in `PocketbaseManager`:

| Path | Ownership / targeting |
| --- | --- |
| `listJob` / `createJobDataMap` | Creates a new job with numeric `Bukkit.getPort()` ownership. |
| `reconcileOpenJobs` | Port-scoped query plus exact defensive guard before any ending or persistent restoration. Legacy unowned rows are skipped. |
| `getJobHistory` | Player plus exact port query and defensive guard; builds fresh relisting templates, not live restored records. |
| `unlistJob` / `endJobRecord` | Updates the exact PocketBase record ID; no broad update query needs a port predicate. Reconciliation chooses IDs only after checking ownership. |
| `listAdvert` | Creates an advert without a `serverPort` field. |
| `getAdvertHistory` | Queries only `posteruuid` and `deleted=false`; loads results into `AdvertManager` on player join. **Not backend-scoped.** |
| `updateAdvert` | Updates the exact record ID (including edits, toggles, and soft deletion), not a broad query. This does not prevent a foreign advert from first being loaded by `getAdvertHistory`. |

Persistent jobs have no separate disk or shared-memory loader: plugin enable
creates a fresh `JobManager` with a local `jobs` map. After API readiness,
`reconcileOpenJobs` is the only persistent restoration path and calls
`JobManager.list(..., sendToPocketbase = false)`. Player joins only rebind already
loaded jobs to their poster. Shutdown removes local jobs without API requests.
Item persistent-data tags contain lookup UUIDs for local maps, not serialized job
records. Local file reads load configuration/categories/board definitions, not
persisted listings. The separate direct image download retrieves player skins;
it does not read or update jobs or adverts.

Advert ownership remains a separate gap if backends share the adverts collection:
player joins can load another backend's adverts, and subsequent exact-ID edits
can modify those loaded records. No advert schema or behavior is changed here;
port fields, legacy policy, and any migration require a separate decision.
