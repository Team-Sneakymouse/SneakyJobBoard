package net.sneakyjobboard

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.lang.management.ManagementFactory
import java.util.concurrent.CompletableFuture

class DatabaseManagerTest {
    private fun <T> equal(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value
    private fun fixture(block: (DatabaseManager, JobBoardRepository, MutableList<Runnable>) -> Unit) {
        val plugin = mock(SneakyJobBoard::class.java)
        `when`(plugin.config).thenReturn(YamlConfiguration())
        `when`(plugin.logger).thenReturn(java.util.logging.Logger.getAnonymousLogger())
        val field = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previous = field.get(null); field.set(null, plugin)
        val repository = mock(JobBoardRepository::class.java)
        `when`(repository.port).thenReturn(25565)
        `when`(repository.whenReady()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(repository.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        `when`(repository.update(anyString(), anyString(), anyMap<String, Any>())).thenReturn(CompletableFuture.completedFuture(1))
        val tasks = mutableListOf<Runnable>()
        val scheduler = mock(BukkitScheduler::class.java)
        `when`(scheduler.runTask(equal(plugin), any(Runnable::class.java))).thenAnswer { tasks.add(it.getArgument(1)); mock(BukkitTask::class.java) }
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
                val manager = DatabaseManager(repository)
                tasks.clear()
                block(manager, repository, tasks)
            }
        } finally { field.set(null, previous) }
    }

    @Test fun `reconciliation isolates foreign zero missing and fractional ports before parsing`() = fixture { manager, store, tasks ->
        val rows = listOf<Map<String, Any?>>(mapOf("serverPort" to 25642), mapOf("serverPort" to 0), emptyMap(), mapOf("serverPort" to 25565.5), mapOf("serverPort" to 25565, "id" to "owned", "persist" to false, "startTime" to 0L))
        `when`(store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(rows))
        manager.reconcileOpenJobs(); tasks.toList().forEach { it.run() }
        verify(store).update(equal(JobBoardRepository.JOBS), equal("owned"), argThat<Map<String, Any>> { it["endReason"] == "restart" } ?: emptyMap())
        verify(store, times(1)).update(anyString(), anyString(), anyMap<String, Any>())
    }

    @Test fun `strict boot boundary retains current ephemeral jobs`() = fixture { manager, store, tasks ->
        val boot = ManagementFactory.getRuntimeMXBean().startTime
        val rows = listOf(boot - 1, boot, boot + 1).mapIndexed { i, start -> mapOf<String, Any?>("serverPort" to 25565, "id" to "row$i", "persist" to false, "startTime" to start) }
        `when`(store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(rows))
        manager.reconcileOpenJobs(); tasks.toList().forEach { it.run() }
        verify(store).update(equal(JobBoardRepository.JOBS), equal("row0"), anyMap<String, Any>())
        verify(store, times(1)).update(anyString(), anyString(), anyMap<String, Any>())
    }

    @Test fun `shutdown fences an already queued reconciliation callback`() = fixture { manager, store, tasks ->
        `when`(store.reconcile(anyLong())).thenReturn(CompletableFuture.completedFuture(listOf(mapOf("serverPort" to 25565, "id" to "old", "startTime" to 0L, "persist" to false))))
        manager.reconcileOpenJobs(); manager.close(); tasks.toList().forEach { it.run() }
        verify(store, never()).update(anyString(), anyString(), anyMap<String, Any>())
        verify(store).close()
    }
}
