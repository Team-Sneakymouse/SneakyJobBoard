# Agent instructions (SneakyJobBoard)

Team Sneakymouse / Lords of Minecraft plugin. Follow the shared conventions in EmptyPlugin's AGENTS.md.

## Repo-specific rules

- **DB access only via JobBoardRepository.** All SQL goes through JobBoardRepository/DatabaseManager. Never run queries on the main thread or add SQL elsewhere; results touching Bukkit objects must be marshalled back through DatabaseManager.main{} (fenced callbacks).
- **Never change DDL from the plugin.** Do not add CREATE/ALTER statements or schema auto-migration. Schema and triggers are operator-owned; document needed changes in docs/postgresql-migration.md and keep startup preflight read-only.
- **Keep port-ownership fence on every read/write.** Every new query or update on jobs/adverts must filter and write serverPort = Bukkit.getPort() (exact, nonzero) and pass the owns() check before restoring/deduplicating.
- **command_console is trusted-config only.** Only config.yml-defined buttons may carry the command_console PDC key. Never populate it from player input or DB rows, and never accept items bearing it from outside the GUI; keep InventoryClick/Drag cancelled for these holders.
- **Spell names are cross-repo contracts.** If you change a 'cast forcecast ... jobboard-*/advert-*' spell name or args, change MagicSpells-Data (branch dev) in the same task and tell Rawb; check knowledge/plugins/MagicSpells.md first.
- **Resources live in src/resources.** Edit src/resources/config.yml and paper-plugin.yml there; do not create src/main/resources. New config keys need a default in that file and must survive reload (JobBoardManager re-reads getConfigFile()).
