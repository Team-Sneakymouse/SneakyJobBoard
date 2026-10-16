package net.sneakyjobboard

import com.danidipp.sneakypocketbase.PocketbaseApi
import com.danidipp.sneakypocketbase.PocketbaseProvider
import com.google.gson.JsonParser
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.lang.management.ManagementFactory
import java.util.concurrent.CompletableFuture

class PocketbaseManagerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = [25565, 25642])
    fun `new job create payload includes the backend listening port as a JSON number`(port: Int) {
        val plugin = mock(SneakyJobBoard::class.java)
        // Identical plugin configuration on every backend: the port comes from Paper.
        val config = YamlConfiguration().apply { set("pocketbase-jobs-collection", "jobs") }
        `when`(plugin.config).thenReturn(config)
        `when`(plugin.logger).thenReturn(java.util.logging.Logger.getAnonymousLogger())
        val instance = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = instance.get(null)
        instance.set(null, plugin)
        try {
            val api = mock(PocketbaseApi::class.java)
            `when`(api.create(eq("jobs"), anyString()))
                .thenReturn(CompletableFuture.completedFuture("""{"id":"new-job"}"""))
            val scheduler = mock(org.bukkit.scheduler.BukkitScheduler::class.java)
            val tasks = mutableListOf<Runnable>()
            `when`(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable::class.java))).thenAnswer {
                tasks.add(it.getArgument(1))
                mock(org.bukkit.scheduler.BukkitTask::class.java)
            }
            val player = mock(org.bukkit.entity.Player::class.java, RETURNS_DEEP_STUBS)
            `when`(player.name).thenReturn("Poster")
            `when`(player.playerProfile.textures.skin).thenReturn(null)
            val category = mock(net.sneakyjobboard.job.JobCategory::class.java)
            `when`(category.name).thenReturn("category")
            `when`(category.description).thenReturn("description")
            `when`(category.discordEmbedIcon).thenReturn("https://example.com/icon.png")
            val job = net.sneakyjobboard.job.Job(
                category, player, org.bukkit.Location(null, 1.0, 2.0, 3.0), 60000L, false
            )
            mockStatic(org.bukkit.Bukkit::class.java).use { bukkit ->
                bukkit.`when`<Int> { org.bukkit.Bukkit.getPort() }.thenReturn(port)
                bukkit.`when`<org.bukkit.scheduler.BukkitScheduler> { org.bukkit.Bukkit.getScheduler() }.thenReturn(scheduler)
                mockStatic(PocketbaseProvider::class.java).use { provider ->
                    provider.`when`<PocketbaseApi> { PocketbaseProvider.getApi() }.thenReturn(api)
                    PocketbaseManager().listJob(job)
                    assertEquals(1, tasks.size)
                    tasks.single().run()

                    val payload = ArgumentCaptor.forClass(String::class.java)
                    verify(api).create(eq("jobs"), payload.capture())
                    val json = JsonParser.parseString(payload.value).asJsonObject
                    assertTrue(json.has("serverPort"), "New jobs must include serverPort")
                    assertTrue(json["serverPort"].asJsonPrimitive.isNumber, "serverPort must be a JSON number")
                    assertEquals(port, json["serverPort"].asInt)
                    assertEquals(job.uuid, json["uuid"].asString)
                    assertEquals("Poster", json["poster"].asString)
                    assertEquals("new-job", job.recordID)
                }
            }
        } finally {
            instance.set(null, previous)
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("25565,25642", "25642,25565")
    fun `foreign ephemeral jobs are never ended even when returned by the query`(port: Int, foreignPort: Int) {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val row = """{"id":"foreign","serverPort":$foreignPort,"persist":false,"startTime":${boot - 1},"durationMillis":3600000}"""
        withReconciliation(listOf(row), port) { fixture ->
            fixture.callback.run()
            verify(fixture.api, never()).update(anyString(), anyString(), anyString())
            assertTrue(fixture.tasks.isEmpty())
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = [25565, 25642])
    fun `query scopes both restart cleanup and persistent restoration to the listening port`(port: Int) {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        withReconciliation(emptyList(), port) { fixture ->
            fixture.callback.run()
            assertEquals("endTime='0' && serverPort=$port && (startTime<$boot || persist=true)", fixture.filter.value)
            verify(fixture.api).getFullList(eq("jobs"), eq(100), eq(""), eq(fixture.filter.value))
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("25565,25642", "25642,25565")
    fun `foreign persistent jobs never restore or update on any reconciliation path`(port: Int, foreignPort: Int) {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val rows = listOf(
            persistentRow("old", boot - 1, port = foreignPort),
            persistentRow("current", boot, port = foreignPort),
            persistentRow("expired", 0, port = foreignPort),
            persistentRow("unknown-category", boot - 1, category = "missing", port = foreignPort),
            persistentRow("invalid-location", boot - 1, location = "invalid", port = foreignPort)
        )
        // Deliberately return foreign rows despite the filter to exercise the defensive fence.
        withReconciliation(rows, port) { fixture ->
            fixture.callback.run()
            fixture.tasks.forEach { it.run() }
            assertTrue(fixture.restored.isEmpty())
            assertTrue(fixture.tasks.isEmpty(), "Foreign jobs must not even queue a restoration")
            verify(fixture.api, never()).update(anyString(), anyString(), anyString())
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = [25565, 25642])
    fun `legacy unowned rows are skipped without preventing owned cleanup`(port: Int) {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val rows = listOf<Int?>(0, null).flatMap { legacyPort ->
            listOf(
                """{${legacyPort?.let { "\"serverPort\":$it," } ?: ""}"id":"legacy-ephemeral-$legacyPort","persist":false,"startTime":0}""",
                persistentRow("legacy-persistent-$legacyPort", boot - 1, port = legacyPort),
                persistentRow("legacy-expired-$legacyPort", 0, port = legacyPort),
                persistentRow("legacy-category-$legacyPort", boot - 1, category = "missing", port = legacyPort),
                persistentRow("legacy-location-$legacyPort", boot - 1, location = "invalid", port = legacyPort)
            )
        } + """{"id":"owned","serverPort":$port,"persist":false,"startTime":0}"""
        withReconciliation(rows, port) { fixture ->
            fixture.callback.run()
            val payload = ArgumentCaptor.forClass(String::class.java)
            verify(fixture.api).update(eq("jobs"), eq("owned"), payload.capture())
            verify(fixture.api, times(1)).update(anyString(), anyString(), anyString())
            assertEquals("restart", JsonParser.parseString(payload.value).asJsonObject["endReason"].asString)
            assertTrue(fixture.tasks.isEmpty())
            assertTrue(fixture.restored.isEmpty())
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = [25565, 25642])
    fun `owned old persistent jobs retain restoration and ending behavior`(port: Int) {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        withReconciliation(listOf(
            persistentRow("valid", boot - 1, port = port),
            persistentRow("expired", 0, port = port),
            persistentRow("unknown-category", boot - 1, category = "missing", port = port),
            persistentRow("invalid-location", boot - 1, location = "invalid", port = port)
        ), port) { fixture ->
            fixture.callback.run()
            fixture.tasks.forEach { it.run() }
            assertEquals(listOf("valid"), fixture.restored.map { it.recordID })
            val ids = ArgumentCaptor.forClass(String::class.java)
            val payloads = ArgumentCaptor.forClass(String::class.java)
            verify(fixture.api, times(3)).update(eq("jobs"), ids.capture(), payloads.capture())
            assertEquals(listOf("expired", "unknown-category", "invalid-location"), ids.allValues)
            assertEquals(listOf("expired", "restart", "restart"), payloads.allValues.map {
                JsonParser.parseString(it).asJsonObject["endReason"].asString
            })
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = [25565, 25642])
    fun `owned ephemeral cleanup retains the strict JVM boot boundary`(port: Int) {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val rows = listOf(boot - 1, boot, boot + 1).mapIndexed { index, start ->
            """{"id":"ephemeral-$index","serverPort":$port,"persist":false,"startTime":$start,"durationMillis":3600000}"""
        }
        withReconciliation(rows, port) { fixture ->
            fixture.callback.run()
            val payload = ArgumentCaptor.forClass(String::class.java)
            verify(fixture.api).update(eq("jobs"), eq("ephemeral-0"), payload.capture())
            verify(fixture.api, times(1)).update(anyString(), anyString(), anyString())
            val json = JsonParser.parseString(payload.value).asJsonObject
            assertEquals("restart", json["endReason"].asString)
            assertTrue(json["endTime"].asLong >= boot)
            assertTrue(fixture.tasks.isEmpty())
        }
    }

    @Test
    fun `a fractional port cannot be truncated into ownership`() {
        val row = """{"id":"foreign","serverPort":25565.5,"persist":false,"startTime":0}"""
        withReconciliation(listOf(row)) { fixture ->
            fixture.callback.run()
            verify(fixture.api, never()).update(anyString(), anyString(), anyString())
            assertTrue(fixture.tasks.isEmpty())
        }
    }

    @Test
    fun `plugin re-enable restores an open persistent job from this boot`() {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        withReconciliation(listOf(persistentRow("current", boot))) { fixture ->
            fixture.callback.run()
            fixture.tasks.forEach { it.run() }

            assertEquals(listOf("current"), fixture.restored.map { it.recordID })
            assertEquals("endTime='0' && serverPort=25565 && (startTime<$boot || persist=true)", fixture.filter.value)
            verify(fixture.api, never()).update(anyString(), anyString(), anyString())
        }
    }

    @Test
    fun `unrestorable current boot persistent jobs are not ended as restart`() {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        withReconciliation(listOf(
            persistentRow("unknown-category", boot, category = "missing"),
            persistentRow("invalid-location", boot + 1, location = "invalid")
        )) { fixture ->
            fixture.callback.run()
            verify(fixture.api, never()).update(anyString(), anyString(), anyString())
            assertTrue(fixture.tasks.isEmpty())
        }
    }

    @Test
    fun `delayed restoration does not replace a persistent job already listed this boot`() {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        withReconciliation(listOf(persistentRow("current", boot))) { fixture ->
            fixture.callback.run()
            val active = mock(net.sneakyjobboard.job.Job::class.java)
            `when`(active.uuid).thenReturn("uuid-current")
            // A listing can become active before the queued main-thread restoration runs,
            // even before its create response has populated recordID.
            fixture.jobs["uuid-current"] = active
            fixture.tasks.forEach { it.run() }
            assertSame(active, fixture.jobs["uuid-current"])
            verify(fixture.api, never()).update(anyString(), anyString(), anyString())
        }
    }

    private fun persistentRow(id: String, start: Long, category: String = "category", location: String = "Location{world=CraftWorld{name=world},x=1.0,y=2.0,z=3.0}", port: Int? = 25565) =
        """{${port?.let { "\"serverPort\":$it," } ?: ""}"id":"$id","uuid":"uuid-$id","persist":true,"startTime":$start,"durationMillis":86400000,"category":"$category","location":"$location"}"""

    private class ReconciliationFixture(
        val api: PocketbaseApi,
        val callback: Runnable,
        val tasks: MutableList<Runnable>,
        val jobs: MutableMap<String, net.sneakyjobboard.job.Job>,
        val filter: ArgumentCaptor<String>
    ) {
        val restored get() = jobs.values.toList()
    }

    private fun withReconciliation(rows: List<String>, port: Int = 25565, test: (ReconciliationFixture) -> Unit) {
        val plugin = mock(SneakyJobBoard::class.java)
        val config = YamlConfiguration().apply { set("pocketbase-jobs-collection", "jobs") }
        `when`(plugin.config).thenReturn(config)
        `when`(plugin.logger).thenReturn(java.util.logging.Logger.getAnonymousLogger())
        val categories = mock(net.sneakyjobboard.job.JobCategoryManager::class.java)
        val category = mock(net.sneakyjobboard.job.JobCategory::class.java)
        `when`(category.name).thenReturn("category")
        `when`(category.description).thenReturn("description")
        `when`(categories.getJobCategories()).thenReturn(mutableMapOf("category" to category))
        `when`(plugin.namespace()).thenReturn("sneakyjobboard")
        val instance = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = instance.get(null)
        instance.set(null, plugin)
        val manager = net.sneakyjobboard.job.JobManager()
        val jobs = manager.jobs
        val boards = mock(net.sneakyjobboard.jobboard.JobBoardManager::class.java)
        `when`(boards.jobBoards).thenReturn(mutableListOf())
        for ((name, value) in listOf("jobCategoryManager" to categories, "jobManager" to manager, "jobBoardManager" to boards)) {
            SneakyJobBoard::class.java.getDeclaredField(name).apply { isAccessible = true }.set(plugin, value)
        }
        val api = mock(PocketbaseApi::class.java)
        val callback = ArgumentCaptor.forClass(Runnable::class.java)
        val filter = ArgumentCaptor.forClass(String::class.java)
        val tasks = mutableListOf<Runnable>()
        val scheduler = mock(org.bukkit.scheduler.BukkitScheduler::class.java)
        `when`(scheduler.runTask(eq(plugin), any(Runnable::class.java))).thenAnswer {
            tasks.add(it.getArgument(1))
            mock(org.bukkit.scheduler.BukkitTask::class.java)
        }
        `when`(api.getFullList(eq("jobs"), eq(100), eq(""), filter.capture()))
            .thenReturn(CompletableFuture.completedFuture(rows))
        `when`(api.update(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("{}"))
        try {
            mockStatic(org.bukkit.Bukkit::class.java).use { bukkit ->
                bukkit.`when`<org.bukkit.scheduler.BukkitScheduler> { org.bukkit.Bukkit.getScheduler() }.thenReturn(scheduler)
                bukkit.`when`<org.bukkit.World> { org.bukkit.Bukkit.getWorld("world") }.thenReturn(mock(org.bukkit.World::class.java))
                bukkit.`when`<Int> { org.bukkit.Bukkit.getPort() }.thenReturn(port)
                mockStatic(PocketbaseProvider::class.java).use { provider ->
                    provider.`when`<PocketbaseApi> { PocketbaseProvider.getApi() }.thenReturn(api)
                    PocketbaseManager()
                    verify(api).whenReady(callback.capture())
                    test(ReconciliationFixture(api, callback.value, tasks, jobs, filter))
                }
            }
        } finally {
            instance.set(null, previous)
        }
    }

    @Test
    fun `shutdown removes ephemeral and persistent jobs without database writes`() {
        val plugin = mock(SneakyJobBoard::class.java)
        `when`(plugin.namespace()).thenReturn("sneakyjobboard")
        val instance = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = instance.get(null)
        instance.set(null, plugin)
        try {
            val manager = net.sneakyjobboard.job.JobManager()
            val ephemeral = mock(net.sneakyjobboard.job.Job::class.java)
            val persistent = mock(net.sneakyjobboard.job.Job::class.java)
            `when`(persistent.persist).thenReturn(true)
            manager.jobs["ephemeral"] = ephemeral
            manager.jobs["persistent"] = persistent

            manager.cleanup()

            verify(ephemeral).unlist("restart", false)
            verify(persistent).unlist("restart", false)
        } finally {
            instance.set(null, previous)
        }
    }

    @Test
    fun `delayed authentication only ends ephemeral jobs from before this server boot`() {
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val plugin = mock(SneakyJobBoard::class.java)
        val config = YamlConfiguration().apply { set("pocketbase-jobs-collection", "jobs") }
        `when`(plugin.config).thenReturn(config)
        val instance = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = instance.get(null)
        instance.set(null, plugin)
        val api = mock(PocketbaseApi::class.java)
        val callback = ArgumentCaptor.forClass(Runnable::class.java)
        val filter = ArgumentCaptor.forClass(String::class.java)
        val updated = mutableListOf<String>()
        val rows = listOf(
            """{"id":"previousboot","serverPort":25565,"persist":false,"startTime":${boot - 1},"durationMillis":3600000}""",
            """{"id":"atboot","serverPort":25565,"persist":false,"startTime":$boot,"durationMillis":3600000}""",
            """{"id":"currentboot","serverPort":25565,"persist":false,"startTime":${boot + 1},"durationMillis":3600000}"""
        )
        `when`(api.getFullList(eq("jobs"), eq(100), eq(""), filter.capture()))
            .thenReturn(CompletableFuture.completedFuture(rows))
        `when`(api.update(eq("jobs"), anyString(), anyString())).thenAnswer {
            updated.add(it.getArgument(1))
            assertEquals("restart", JsonParser.parseString(it.getArgument(2)).asJsonObject["endReason"].asString)
            CompletableFuture.completedFuture("{}")
        }
        try {
            mockStatic(org.bukkit.Bukkit::class.java).use { bukkit ->
                bukkit.`when`<Int> { org.bukkit.Bukkit.getPort() }.thenReturn(25565)
                mockStatic(PocketbaseProvider::class.java).use { provider ->
                    provider.`when`<PocketbaseApi> { PocketbaseProvider.getApi() }.thenReturn(api)
                    PocketbaseManager()
                    verify(api).whenReady(callback.capture())
                    // The API becomes ready after jobs from this boot already exist.
                    callback.value.run()
                    assertEquals(listOf("previousboot"), updated)
                    assertEquals("endTime='0' && serverPort=25565 && (startTime<$boot || persist=true)", filter.value)
                }
            }
        } finally {
            instance.set(null, previous)
        }
    }
}
