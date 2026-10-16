package net.sneakyjobboard

import com.danidipp.sneakypocketbase.PocketbaseApi
import com.danidipp.sneakypocketbase.PocketbaseProvider
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.job.JobCategory
import net.sneakyjobboard.job.JobCategoryManager
import net.sneakyjobboard.job.JobHistoryInventoryHolder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.util.concurrent.CompletableFuture

class JobHistoryPortTest {
    @ParameterizedTest
    @CsvSource("25565,25642", "25642,25565")
    fun `history excludes foreign rows before deduplication and the nine unique limit`(port: Int, foreignPort: Int) {
        val rows = listOf(
            """{"serverPort":$foreignPort}""", // Foreign fields must not even be parsed.
            """{"serverPort":0}""",
            """{"serverPort":null}""",
            "{}",
            historyRow("shared", foreignPort.toString())
        ) +
            (1..12).map { historyRow("foreign-$it", foreignPort.toString()) } +
            listOf(
                historyRow("fractional-owned", "$port.5"),
                historyRow("fractional-zero", "0.5"),
                historyRow("shared", "$port.0"),
                historyRow("legacy", "0"),
                historyRow("shared", port.toString()),
                historyRow("legacy", "0")
            ) + (1..10).map { historyRow("owned-$it", port.toString()) }
        withHistory(rows, port) { jobs, filter ->
            assertEquals(listOf("shared") + (1..8).map { "owned-$it" }, jobs.map { it.name })
            assertEquals("poster='Poster' && serverPort=$port", filter)
            assertTrue(jobs.all { it.durationMillis == 123456L && it.tracking })
            assertTrue(jobs.all { it.recordID.isEmpty() && !it.persist }, "History creates fresh relisting templates")
        }
    }

    private fun historyRow(name: String, port: String) =
        """{"serverPort":$port,"category":"category","name":"$name","tracking":true,"description":"description"}"""

    private fun withHistory(rows: List<String>, port: Int, test: (List<Job>, String) -> Unit) {
        val plugin = mock(SneakyJobBoard::class.java)
        `when`(plugin.config).thenReturn(YamlConfiguration().apply { set("pocketbase-jobs-collection", "jobs") })
        `when`(plugin.logger).thenReturn(java.util.logging.Logger.getAnonymousLogger())
        val category = mock(JobCategory::class.java)
        `when`(category.name).thenReturn("category")
        `when`(category.description).thenReturn("description")
        val categories = mock(JobCategoryManager::class.java)
        `when`(categories.getJobCategories()).thenReturn(mutableMapOf("category" to category))
        SneakyJobBoard::class.java.getDeclaredField("jobCategoryManager").apply { isAccessible = true }.set(plugin, categories)
        val instance = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = instance.get(null)
        instance.set(null, plugin)
        try {
            val player = mock(Player::class.java)
            `when`(player.name).thenReturn("Poster")
            `when`(player.location).thenReturn(Location(null, 1.0, 2.0, 3.0))
            val api = mock(PocketbaseApi::class.java)
            val filter = ArgumentCaptor.forClass(String::class.java)
            `when`(api.getFullList(eq("jobs"), eq(100), eq("-created"), filter.capture()))
                .thenReturn(CompletableFuture.completedFuture(rows))
            val scheduler = mock(BukkitScheduler::class.java)
            val asyncTasks = mutableListOf<Runnable>()
            val mainTasks = mutableListOf<Runnable>()
            `when`(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable::class.java))).thenAnswer {
                asyncTasks.add(it.getArgument(1)); mock(BukkitTask::class.java)
            }
            `when`(scheduler.runTask(eq(plugin), any(Runnable::class.java))).thenAnswer {
                mainTasks.add(it.getArgument(1)); mock(BukkitTask::class.java)
            }
            val displayed = mutableListOf<Job>()
            val inventory = mock(Inventory::class.java)
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<Int> { Bukkit.getPort() }.thenReturn(port)
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
                mockStatic(PocketbaseProvider::class.java).use { provider ->
                    provider.`when`<PocketbaseApi> { PocketbaseProvider.getApi() }.thenReturn(api)
                    mockConstruction(JobHistoryInventoryHolder::class.java) { holder, context ->
                        @Suppress("UNCHECKED_CAST")
                        displayed.addAll(context.arguments()[0] as List<Job>)
                        `when`(holder.inventory).thenReturn(inventory)
                    }.use {
                        PocketbaseManager().getJobHistory(player, 123456L)
                        assertEquals(1, asyncTasks.size)
                        asyncTasks.single().run()
                        assertEquals(1, mainTasks.size, "History must reach the display task")
                        mainTasks.single().run()
                        verify(player).openInventory(inventory)
                        verify(api, never()).create(anyString(), anyString())
                        verify(api, never()).update(anyString(), anyString(), anyString())
                        test(displayed, filter.value)
                    }
                }
            }
        } finally {
            instance.set(null, previous)
        }
    }
}
