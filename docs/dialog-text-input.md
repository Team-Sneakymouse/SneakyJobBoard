# Native text input dialogs

## Behavior

- `/listjob` without explicit text opens one dialog with Name and multiline Description, Submit and Cancel.
- `/listadvert` still opens the existing icon selector first. Selecting an icon opens one Title/Description dialog.
- Advert management → Change Text opens the same form prefilled with both current fields and a Save button. A successful save returns to the existing edit menu.
- Neither draft entry nor Cancel changes a job/advert. Escape is enabled; closing the screen does not submit.
- Dialog fields require nonblank text, a single-line title of at most 128 characters, and a description of at most 4096 characters. Unsupported control characters are rejected server-side. Invalid submissions close without saving and report an error; reopen the flow to retry.
- Job names retain pipe removal, format-code conversion and MiniMessage escaping; descriptions retain conversion/escaping without pipe removal. Advert text retains its existing raw formatting semantics. Explicit job command text is unchanged.
- Player identity, current draft token, online/enabled state and single consumption protect submissions. All mutation runs in a Bukkit server task. Quit and plugin-disable events invalidate pending drafts, including already queued submissions. New dialogs, a new advert command, explicit job listing and `/unlistjob` cancel replaced drafts.
- Advert submission rechecks ownership, deleted/existing state, and the eight-advert limit for creation. Editing requires the exact advert to remain registered. The category edit button remains disabled; its obsolete chat handler was removed too.

No database schema changes. The two inventory-to-dialog transitions defer both inventory close and dialog opening to the next server tick, rechecking the original view, online/enabled state and current flow token. Icon editing and selector navigation are otherwise unchanged.

## Command timing follow-up

The command entry point still called `showDialog` synchronously, unlike the advert inventory listeners. Both player and console-target-player interactive `/listjob` now reserve a flow token immediately, then close inventory and open the dialog in one next-tick task. This invalidates prior submitted callbacks before the task runs. Location and command options remain captured at command execution; explicit full-text commands remain immediate. Interactive success is exactly yellow `Job listed.` without echoing either field; other feedback is unchanged.

The queued command accepts the originating view or the ordinary closed-player CRAFTING view, rather than requiring the original inventory to remain open. A different non-CRAFTING inventory cancels the opening. Assumption: a changed CRAFTING view represents a normal close; navigation away and back to the player inventory within the same tick cannot be distinguished. Advert listener transitions retain their stricter original-view checks and gain no extra delay.

Observed follow-up RED → GREEN: both player/console timing cases failed because `showDialog` ran inline (2/2), then both passed after command-boundary deferral. The yellow-success test failed on the uncoloured component (1/1), then passed. Additional regressions cover queued request replacement, explicit listing, unlist, offline, quit/disable, inventory navigation and stale already-queued submissions. The fixture drains a snapshot per explicit queue turn; callbacks added during that turn do not run in that same turn. API-only InventoryType setup required stubbing the MENU registry `getOrThrow(Key)` before enum initialization; setup failures are not counted as behavioral RED evidence.

This proves command-boundary ordering against a simulated caller that closes inventory after command return, not the unknown live inventory provider. No server changes or live reproduction were performed.

## Automated verification

Pinned project toolchain: Paper `26.2.build.121-stable`, Java 25, Kotlin `2.4.10`.

```sh
./gradlew --no-daemon --rerun-tasks --no-build-cache build --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx512m -XX:ActiveProcessorCount=2' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Actual follow-up result: `BUILD SUCCESSFUL` with `--rerun-tasks --no-build-cache`; JUnit XML contains **89 tests, 0 failures, 0 errors, 0 skipped** (67 dialog tests, 2 history tests, 20 PocketBase tests).

Observed RED → GREEN checkpoints included:

| Slice | Observed RED | Subsequent GREEN |
| --- | --- | --- |
| Interactive job command shows dialog | 1 test, 1 missing-showDialog failure | 1 passed |
| Duplicate submit | 2 tests, 1 failure | 2 passed |
| Validation, cancellation, disconnect/disable state and deferred mutation | 14 tests, 10 failures | 14 passed |
| Advert creation after icon selection | 15 tests, 1 failure | 15 passed |
| Advert submission limit | 16 tests, 1 failure | 16 passed |
| Prefilled edit form and disabled category action | 18 tests, 2 failures | 18 passed |
| Stale/foreign advert edits and bottom-inventory click | 24 tests, 5 failures | 24 passed |
| Lifecycle invalidation and explicit-command draft replacement | 28 tests, 3 failures | 28 passed |
| Invalid advert drafts | 32 tests, 3 failures | 32 passed |
| Foreign-player prefill disclosure guard | 32 tests, 1 failure | 32 passed |
| Advert command retains selector and replaces old draft | 33 tests, 1 failure | 33 passed |

The console target-player/location characterization test originally brought dialog coverage to 34 tests. Lifecycle regressions now bring it to 47: the actual edit and icon-selection listeners prove no inventory close or dialog display inside the click event, and positive display after the queued task. Both flows reject changed views, offline players, disabled plugins, replaced drafts, quit and disable lifecycle events. Observed RED checkpoints: edit transition 1/1 failed, creation transition 1/1 failed, and stale lifecycle 12/12 failed; all subsequently passed. Test-fixture setup failures were fixed separately from behavioral RED evidence.

Tests exercise real commands, the real edit and icon-selector listeners, real `Dialog.create` and public input/action factories, registered submission/cancel callbacks, and queued server tasks. Paper registry/provider implementations, Bukkit infrastructure, inventory construction and persistence managers are mocked at their boundaries; this does not verify native server serialization, client rendering or live PocketBase persistence.

The clean build retains existing custom-model-data/cast warnings, Gradle deprecation notices and Mockito/JVM instrumentation notices. Independent review is deferred to the parent reviewer before push/PR, as requested; no `[verified]` commit prefix is used.

## In-game checklist — not yet verified

1. Run `/listjob <category> <duration>` from chat and the actual inventory button, both as the player and via a console command targeting that player. Confirm the menu can close after dispatch and the dialog survives the next tick; inspect the provider configuration/source if it schedules a later close. Verify yellow `Job listed.` only on Submit, and verify one screen, Name, multiline Description, Submit and Cancel. Submit formatting tags, ampersand codes, a pipe in each field and actual description line breaks; check the board and persisted record.
2. Repeat Cancel and Escape for job creation, advert creation and prefilled advert editing. Confirm no listing, text mutation or persistence write; normal chat must remain unaffected.
3. Run `/listadvert`; verify original icon navigation and selection, then both text fields together. Submit and confirm selected material/model data and text on the advert board.
4. Open Change Text; confirm both old fields are prefilled, Save applies both together, and the edit menu reopens. Confirm the category control is still absent/disabled and player-inventory slots cannot activate menu actions.
5. Check missing/blank text, title length and line-break limits, multiline description limits, and an all-pipe job name. Invalid submission must not list/save.
6. Open another flow before submitting an old callback, cancel a pending job with `/unlistjob`, disconnect/reconnect and disable/re-enable the plugin. No stale or duplicate submission may list or save.
7. Delete an advert while its edit form is open; Save must not restore/update it. Reach eight adverts while another creation form is open; its Submit must be rejected.
8. Exercise explicit quoted job arguments from a player and both console forms (target player and explicit world/coordinates), plus the console target-player advert command.
