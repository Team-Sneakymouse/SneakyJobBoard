package net.sneakyjobboard

import net.sneakyjobboard.advert.Advert
import net.sneakyjobboard.advert.AdvertManager
import net.sneakyjobboard.advert.AdvertCategoryManager
import net.sneakyjobboard.job.*
import net.sneakyjobboard.jobboard.JobBoardManager
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.Inventory
import org.bukkit.scheduler.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Suppress("UNCHECKED_CAST")
class DatabaseRegressionTest {
    private fun <T> equal(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value
    private class Fixture(val plugin: SneakyJobBoard, val store: JobBoardRepository, val database: DatabaseManager,
        val player: Player, val tasks: MutableList<Runnable>, val jobs: JobManager, val adverts: AdvertManager) {
        fun drain() { val copy = tasks.toList(); tasks.clear(); copy.forEach { it.run() } }
    }
    private fun fixture(port: Int = 25565, test: (Fixture) -> Unit) {
        val plugin = mock(SneakyJobBoard::class.java)
        `when`(plugin.config).thenReturn(YamlConfiguration())
        `when`(plugin.namespace()).thenReturn("sneakyjobboard")
        `when`(plugin.logger).thenReturn(java.util.logging.Logger.getAnonymousLogger())
        val field = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = field.get(null); field.set(null, plugin)
        val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val playerId = UUID.randomUUID()
        `when`(player.uniqueId).thenReturn(playerId); `when`(player.name).thenReturn("Poster")
        `when`(player.isOnline).thenReturn(true); `when`(player.location).thenReturn(Location(null, 1.0, 2.0, 3.0))
        `when`(player.playerProfile.textures.skin).thenReturn(null)
        val store = mock(JobBoardRepository::class.java)
        `when`(store.port).thenReturn(port)
        `when`(store.whenReady()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        `when`(store.history(anyString())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        `when`(store.adverts(anyString())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        `when`(store.update(anyString(), anyString(), anyMap<String, Any>())).thenReturn(CompletableFuture.completedFuture(1))
        `when`(store.insert(anyString(), anyString(), anyMap<String, Any>(), nullable(CompletableFuture::class.java) as CompletableFuture<String>?)).thenReturn(CompletableFuture.completedFuture(emptyList()))
        val category = mock(JobCategory::class.java)
        `when`(category.name).thenReturn("category"); `when`(category.description).thenReturn("description")
        `when`(category.discordEmbedIcon).thenReturn("https://example.com/icon.png")
        val categories = mock(JobCategoryManager::class.java)
        `when`(categories.getJobCategories()).thenReturn(mutableMapOf("category" to category))
        val boards = mock(JobBoardManager::class.java)
        `when`(boards.jobBoards).thenReturn(mutableListOf())
        val jobs = JobManager(); val adverts = AdvertManager()
        for ((name, value) in listOf("jobCategoryManager" to categories, "jobManager" to jobs, "jobBoardManager" to boards, "advertManager" to adverts, "advertCategoryManager" to mock(AdvertCategoryManager::class.java))) {
            SneakyJobBoard::class.java.getDeclaredField(name).apply { isAccessible = true }.set(plugin, value)
        }
        val tasks = mutableListOf<Runnable>(); val scheduler = mock(BukkitScheduler::class.java)
        `when`(scheduler.runTask(equal(plugin), any(Runnable::class.java))).thenAnswer { tasks.add(it.getArgument(1)); mock(BukkitTask::class.java) }
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<Int> { Bukkit.getPort() }.thenReturn(port)
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
                bukkit.`when`<Player> { Bukkit.getPlayer(playerId) }.thenReturn(player)
                bukkit.`when`<World> { Bukkit.getWorld("world") }.thenReturn(mock(World::class.java))
                bukkit.`when`<Collection<Player>> { Bukkit.getOnlinePlayers() }.thenReturn(emptyList())
                val manager = DatabaseManager(store)
                tasks.clear()
                test(Fixture(plugin, store, manager, player, tasks, jobs, adverts))
            }
        } finally { field.set(null, previous) }
    }
    private fun persistent(id: String, start: Long, port: Int, category: String = "category", location: String = "Location{world=CraftWorld{name=world},x=1.0,y=2.0,z=3.0}") = mapOf<String, Any?>(
        "id" to id, "uuid" to "uuid-$id", "serverPort" to port, "persist" to true, "tracking" to false, "startTime" to start, "durationMillis" to 86400000L, "category" to category, "location" to location, "poster" to "Poster")

    @ParameterizedTest @ValueSource(ints = [25565, 25642])
    fun `persistent restore and owned invalid cleanup remain separate from boot ending`(port: Int) = fixture(port) { f ->
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val rows = listOf(persistent("old", boot - 1, port), persistent("current", boot, port), persistent("expired", 0, port),
            persistent("bad", boot - 1, port, "missing"), persistent("location", boot - 1, port, location = "invalid"),
            persistent("current-bad", boot, port, "missing"), persistent("current-location", boot, port, location = "invalid"))
        `when`(f.store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(rows))
        f.database.reconcileOpenJobs()
        assertTrue(f.jobs.jobs.isEmpty(), "All Bukkit job construction/restoration must wait for main thread")
        f.drain()
        assertEquals(setOf("old", "current"), f.jobs.jobs.values.map { it.recordID }.toSet())
        verify(f.store).update(equal(JobBoardRepository.JOBS), equal("expired"), argThat<Map<String, Any>> { it["endReason"] == "expired" } ?: emptyMap())
        for (id in listOf("bad", "location")) verify(f.store).update(equal(JobBoardRepository.JOBS), equal(id), argThat<Map<String, Any>> { it["endReason"] == "restart" } ?: emptyMap())
        verify(f.store, times(3)).update(anyString(), anyString(), anyMap<String, Any>())
    }

    @ParameterizedTest @ValueSource(ints = [25565, 25642])
    fun `foreign persistent rows do not restore expire or trigger invalid cleanup`(port: Int) = fixture(port) { f ->
        val foreign = if (port == 25565) 25642 else 25565
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val rows = listOf(persistent("old", boot - 1, foreign), persistent("current", boot, foreign), persistent("expired", 0, foreign), persistent("bad", 0, foreign, "missing"), persistent("location", 0, foreign, location = "invalid"))
        `when`(f.store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(rows))
        f.database.reconcileOpenJobs(); f.drain()
        assertTrue(f.jobs.jobs.isEmpty()); verify(f.store, never()).update(anyString(), anyString(), anyMap<String, Any>())
    }

    @Test fun `queued restoration never replaces an already listed UUID`() = fixture { f ->
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        `when`(f.store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(listOf(persistent("current", boot, 25565))))
        f.database.reconcileOpenJobs()
        val active = mock(Job::class.java); `when`(active.uuid).thenReturn("uuid-current")
        f.jobs.jobs["uuid-current"] = active; f.drain()
        assertSame(active, f.jobs.jobs["uuid-current"])
    }

    @ParameterizedTest @ValueSource(ints = [25565, 25642])
    fun `history excludes foreign rows before deduplication and the nine unique limit`(port: Int) = fixture(port) { f ->
        fun row(name: String, owner: Number) = mapOf<String, Any?>("serverPort" to owner, "category" to "category", "name" to name, "description" to "description", "tracking" to true)
        val rows = listOf(mapOf<String, Any?>("serverPort" to 0), emptyMap(), row("shared", 1), row("fractional", port + 0.5)) +
            (1..12).map { row("foreign-$it", 1) } + listOf(row("shared", port), row("shared", port)) + (1..10).map { row("owned-$it", port) }
        `when`(f.store.history("Poster")).thenReturn(CompletableFuture.completedFuture(rows))
        val displayed = mutableListOf<Job>()
        val inventory = mock(Inventory::class.java)
        mockConstruction(JobHistoryInventoryHolder::class.java) { holder, context ->
            @Suppress("UNCHECKED_CAST") displayed.addAll(context.arguments()[0] as List<Job>)
            `when`(holder.inventory).thenReturn(inventory)
        }.use {
            f.database.getJobHistory(f.player, 123456L)
            assertTrue(displayed.isEmpty()); f.drain()
            assertEquals(listOf("shared") + (1..8).map { "owned-$it" }, displayed.map { it.name })
            assertTrue(displayed.all { it.durationMillis == 123456L && it.tracking && !it.persist && it.recordID.isEmpty() })
            verify(f.player).openInventory(inventory)
        }
    }

    @Test fun `history callbacks cannot reopen inventory after logout or shutdown`() = fixture { f ->
        f.database.getJobHistory(f.player, 1L)
        `when`(f.player.isOnline).thenReturn(false)
        f.drain(); verify(f.player, never()).openInventory(any(Inventory::class.java))
        `when`(f.player.isOnline).thenReturn(true)
        f.database.getJobHistory(f.player, 1L); f.database.close(); f.drain()
        verify(f.player, never()).openInventory(any(Inventory::class.java))
    }

    @ParameterizedTest @ValueSource(ints = [25565, 25642])
    fun `adverts retain ownership enabled icon and soft deletion features`(port: Int) = fixture(port) { f ->
        val uuid = f.player.uniqueId.toString()
        fun row(id: String, owner: Number, deleted: Boolean = false) = mapOf<String, Any?>("id" to id, "uuid" to id, "serverPort" to owner, "posteruuid" to uuid, "name" to id, "category" to "", "description" to "description", "posterDisplayString" to "poster", "iconMaterial" to "PAPER", "iconCustomModelData" to java.math.BigDecimal("3.5"), "enabled" to true, "deleted" to deleted)
        val rows = listOf(mapOf<String, Any?>("serverPort" to 0), emptyMap(), row("foreign", 1), row("fractional", port + 0.5), row("deleted", port, true), row("owned", port))
        `when`(f.store.adverts(uuid)).thenReturn(CompletableFuture.completedFuture(rows))
        f.database.getAdvertHistory(uuid)
        assertNull(f.adverts.getAdvert("owned")); f.drain()
        val advert = requireNotNull(f.adverts.getAdvert("owned"))
        assertNull(f.adverts.getAdvert("foreign")); assertNull(f.adverts.getAdvert("deleted"))
        assertEquals(Material.PAPER, advert.iconMaterial); assertEquals(3, advert.iconCustomModelData); assertTrue(advert.enabled)
        advert.deleted = true; advert.enabled = false; f.database.updateAdvert(advert)
        verify(f.store).update(equal(JobBoardRepository.ADVERTS), equal("owned"), argThat<Map<String, Any>> { it["deleted"] == true && it["enabled"] == false && it["posteruuid"] == uuid && it["iconCustomModelData"] == java.math.BigDecimal("3.5") } ?: emptyMap())
    }

    @Test fun `new job ID is assigned before persistence and end captures same ID`() = fixture { f ->
        val category = SneakyJobBoard.getJobCategoryManager().getJobCategories().values.first()
        val job = Job(category, f.player, f.player.location, 60000L, false)
        f.database.listJob(job)
        assertTrue(job.recordID.matches(Regex("[a-z0-9]{15}")))
        val id = job.recordID
        f.database.unlistJob(job, "unlisted")
        verify(f.store).insert(equal(JobBoardRepository.JOBS), equal(id), argThat<Map<String, Any>> { it["poster"] == "Poster" && it["uuid"] == job.uuid } ?: emptyMap(), any(CompletableFuture::class.java) as CompletableFuture<String>?)
        verify(f.store).update(equal(JobBoardRepository.JOBS), equal(id), argThat<Map<String, Any>> { it["endReason"] == "unlisted" } ?: emptyMap())
    }

    @Test fun `an end while reconciliation waits cannot resurrect a stale persistent row`() = fixture { f ->
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        `when`(f.store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(listOf(persistent("current", boot, 25565))))
        f.database.reconcileOpenJobs()
        val job = mock(Job::class.java); `when`(job.recordID).thenReturn("current")
        f.database.unlistJob(job, "unlisted")
        f.drain()
        assertTrue(f.jobs.jobs.isEmpty(), "An ended local job must not be restored from an earlier SELECT snapshot")
    }

    @Test fun `editing one advert while history waits does not drop other persisted adverts`() = fixture { f ->
        val poster = f.player.uniqueId.toString()
        fun row(id: String) = mapOf<String, Any?>("id" to id, "uuid" to id, "serverPort" to 25565, "posteruuid" to poster, "name" to id, "category" to "", "description" to "desc", "enabled" to true, "deleted" to false)
        `when`(f.store.adverts(poster)).thenReturn(CompletableFuture.completedFuture(listOf(row("changed"), row("other"))))
        f.database.getAdvertHistory(poster)
        val changed = Advert(null, f.player).apply { recordID = "changed"; uuid = "changed"; deleted = true }
        f.database.updateAdvert(changed)
        f.drain()
        assertNull(f.adverts.getAdvert("changed"), "Stale history cannot resurrect a locally deleted advert")
        assertNotNull(f.adverts.getAdvert("other"), "Unrelated adverts must still restore")
    }

    @Test fun `shutdown cleanup remains local for ephemeral and persistent jobs`() = fixture { f ->
        val ephemeral = mock(Job::class.java); val persistent = mock(Job::class.java)
        `when`(persistent.persist).thenReturn(true)
        f.jobs.jobs["a"] = ephemeral; f.jobs.jobs["b"] = persistent
        f.jobs.cleanup()
        verify(ephemeral).unlist("restart", false); verify(persistent).unlist("restart", false)
        verify(f.store, never()).update(anyString(), anyString(), anyMap<String, Any>())
    }
}
