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
