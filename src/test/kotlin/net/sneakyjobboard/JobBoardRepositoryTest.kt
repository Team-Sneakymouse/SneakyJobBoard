package net.sneakyjobboard

import com.sneakydb.SneakyDBApi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.util.concurrent.CompletableFuture

class JobBoardRepositoryTest {
    private fun <T> equal(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value
    private fun api(): SneakyDBApi = mock(SneakyDBApi::class.java).also {
        `when`(it.whenReady()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(it.query(anyString(), anyList<Any>())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        `when`(it.query(startsWith("INSERT"), anyList<Any>())).thenReturn(CompletableFuture.completedFuture(listOf(mapOf("id" to "abc123abc123abc"))))
        `when`(it.execute(anyString(), anyList<Any>())).thenReturn(CompletableFuture.completedFuture(1))
    }

    @Test fun `readiness gates read-only preflight and all writes`() {
        val api = api()
        val ready = CompletableFuture<Void>()
        `when`(api.whenReady()).thenReturn(ready)
        val store = JobBoardRepository(api, "lom", 50001)
        val write = store.insert("lom2_listed_jobs", "abc123abc123abc", mapOf("name" to "original"))
        verify(api, never()).query(anyString(), anyList<Any>())
        assertFalse(write.isDone)
        ready.complete(null)
        write.join()
        verify(api).query(equal("INSERT INTO \"lom\".\"lom2_listed_jobs\" (\"id\", \"serverPort\", \"name\") VALUES (?, ?, ?) RETURNING id"), equal(listOf("abc123abc123abc", 50001, "original")))
    }

    @Test fun `same-record end waits for insert and snapshots values`() {
        val api = api()
        val pending = CompletableFuture<List<Map<String, Any?>>>()
        `when`(api.query(startsWith("INSERT"), anyList<Any>())).thenReturn(pending)
        val store = JobBoardRepository(api, "lom", 50001)
        val data = mutableMapOf<String, Any>("name" to "original")
        val create = store.insert("lom2_listed_jobs", "abc123abc123abc", data)
        data["name"] = "changed"
        val end = store.update("lom2_listed_jobs", "abc123abc123abc", mapOf("endTime" to 12L, "endReason" to "unlisted"))
        verify(api, never()).execute(anyString(), anyList<Any>())
        pending.complete(listOf(mapOf("id" to "abc123abc123abc")))
        create.join(); end.join()
        verify(api).execute(equal("UPDATE \"lom\".\"lom2_listed_jobs\" SET \"endTime\" = ?, \"endReason\" = ? WHERE id = ? AND \"serverPort\" = ?"), equal(listOf(12L, "unlisted", "abc123abc123abc", 50001)))
    }

    @Test fun `history uses bound player and exact port without SQL interpolation`() {
        val api = api(); val store = JobBoardRepository(api, "lom", 25565)
        store.history("O'Brien").join()
        verify(api).query(equal("SELECT * FROM \"lom\".\"lom2_listed_jobs\" WHERE poster = ? AND \"serverPort\" = ? ORDER BY created DESC, id DESC"), equal(listOf("O'Brien", 25565)))
        store.adverts("poster").join()
        verify(api).query(equal("SELECT * FROM \"lom\".\"lom2_adverts\" WHERE posteruuid = ? AND deleted = false AND \"serverPort\" = ? ORDER BY created DESC, id DESC"), equal(listOf("poster", 25565)))
    }

    @Test fun `failure does not allow a dependent end and close fences deferred work`() {
        val api = api(); val ready = CompletableFuture<Void>()
        `when`(api.whenReady()).thenReturn(ready)
        val store = JobBoardRepository(api, "lom", 50001)
        val create = store.insert("lom2_listed_jobs", "abc123abc123abc", mapOf("name" to "x"))
        val end = store.update("lom2_listed_jobs", "abc123abc123abc", mapOf("endTime" to 1L))
        store.close(); ready.complete(null)
        assertThrows(Exception::class.java) { create.join() }
        assertThrows(Exception::class.java) { end.join() }
        verify(api, never()).execute(anyString(), anyList<Any>())
        verify(api, never()).query(startsWith("INSERT"), anyList<Any>())
    }

    @Test fun `failed insert permanently fences dependent and later updates`() {
        val api = api()
        `when`(api.query(startsWith("INSERT"), anyList<Any>())).thenReturn(CompletableFuture.failedFuture(IllegalStateException("uncertain write")))
        val store = JobBoardRepository(api, "lom", 50001)
        assertThrows(Exception::class.java) { store.insert(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("name" to "x")).join() }
        assertThrows(Exception::class.java) { store.update(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("endTime" to 1L)).join() }
        verify(api, never()).execute(anyString(), anyList<Any>())
    }

    @Test fun `asynchronous icon preparation cannot reorder an immediate end`() {
        val api = api(); val store = JobBoardRepository(api, "lom", 50001)
        val icon = CompletableFuture<String>()
        store.insert(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("posterIconBase64" to "fallback"), icon)
        val end = store.update(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("endTime" to 1L))
        verify(api, never()).query(startsWith("INSERT"), anyList<Any>())
        verify(api, never()).execute(anyString(), anyList<Any>())
        icon.complete("prepared")
        end.join()
        verify(api).query(startsWith("INSERT"), equal(listOf("abc123abc123abc", 50001, "prepared")))
    }

    @Test fun `insert response must confirm the preassigned ID before dependent updates`() {
        val api = api()
        `when`(api.query(startsWith("INSERT"), anyList<Any>())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        val store = JobBoardRepository(api, "lom", 50001)
        assertThrows(Exception::class.java) { store.insert(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("name" to "x")).join() }
        assertThrows(Exception::class.java) { store.update(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("endTime" to 1L)).join() }
        verify(api, never()).execute(anyString(), anyList<Any>())
    }

    @Test fun `readiness failure performs no preflight or writes`() {
        val api = api()
        `when`(api.whenReady()).thenReturn(CompletableFuture.failedFuture(IllegalStateException("not ready")))
        val store = JobBoardRepository(api, "lom", 50001)
        assertThrows(Exception::class.java) { store.history("player").join() }
        verify(api, never()).query(anyString(), anyList<Any>())
    }

    @Test fun `zero-row update is not reported as successful persistence`() {
        val api = api()
        `when`(api.execute(anyString(), anyList<Any>())).thenReturn(CompletableFuture.completedFuture(0))
        val store = JobBoardRepository(api, "lom", 50001)
        assertThrows(Exception::class.java) { store.update(JobBoardRepository.JOBS, "abc123abc123abc", mapOf("endTime" to 1L)).join() }
    }

    @Test fun `schema and columns cannot inject SQL or overwrite Discord ownership`() {
        val api = api()
        assertThrows(IllegalArgumentException::class.java) { JobBoardRepository(api, "lom; DROP SCHEMA lom", 50001) }
        val store = JobBoardRepository(api, "lom", 50001)
        assertThrows(IllegalArgumentException::class.java) { store.update("lom2_listed_jobs", "abc123abc123abc", mapOf("discordMessageId" to "no")) }
        assertThrows(IllegalArgumentException::class.java) { store.insert("outbox", "abc123abc123abc", mapOf("name" to "x")) }
    }
}
