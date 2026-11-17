package net.sneakyjobboard

import com.sneakydb.SneakyDBApi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/**
 * Applies V001 to an empty `lom` schema and exercises canonical writes. Run with `./gradlew postgresTest`.
 */
@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobBoardMigrationTest {
    private val scriptPattern = Pattern.compile("V0*(\\d+)__[A-Za-z0-9_-]+\\.sql")
    private val postgres = PostgreSQLContainer("postgres:17-alpine")

    private inner class JdbcDb(private val url: String, private val user: String, private val password: String) : SneakyDBApi {
        override fun query(sql: String, parameters: List<*>): CompletableFuture<List<Map<String, Any?>>> =
            CompletableFuture.supplyAsync {
                DriverManager.getConnection(url, user, password).use { c ->
                    c.prepareStatement(sql).use { st ->
                        parameters.forEachIndexed { i, v -> st.setObject(i + 1, v) }
                        st.executeQuery().use { rs ->
                            val rows = ArrayList<Map<String, Any?>>()
                            while (rs.next()) {
                                rows += (1..rs.metaData.columnCount).associate { rs.metaData.getColumnLabel(it) to rs.getObject(it) }
                            }
                            rows
                        }
                    }
                }
            }

        override fun execute(sql: String, parameters: List<*>): CompletableFuture<Int> =
            CompletableFuture.supplyAsync {
                DriverManager.getConnection(url, user, password).use { c ->
                    c.prepareStatement(sql).use { st ->
                        parameters.forEachIndexed { i, v -> st.setObject(i + 1, v) }
                        st.executeUpdate()
                    }
                }
            }

        override fun whenReady(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        override fun migrate(plugin: String, anchor: Class<*>): CompletableFuture<Int> = CompletableFuture.completedFuture(1)
        override fun isAvailable() = true
    }

    private fun migrationVersion(name: String) =
        scriptPattern.matcher(name).let { m ->
            check(m.matches()) { name }
            m.group(1).toInt()
        }

    private fun applyMigrations(url: String, user: String, password: String) {
        val root = Path.of(checkNotNull(JobBoardMigrationTest::class.java.getResource("/db/migrations")).toURI())
        val scripts = Files.list(root).use { stream ->
            stream.filter { path -> scriptPattern.matcher(path.fileName.toString()).matches() }
                .sorted(Comparator.comparingInt { path -> migrationVersion(path.fileName.toString()) })
                .toList()
        }
        check(scripts.single().fileName.toString() == "V001__jobboard.sql")
        DriverManager.getConnection(url, user, password).use { c ->
            c.autoCommit = false
            c.createStatement().use { it.execute("CREATE SCHEMA lom AUTHORIZATION $user") }
            c.commit()
            c.createStatement().use { it.execute(Files.readString(scripts.single())) }
            c.commit()
        }
    }

    @BeforeAll
    fun start() {
        postgres.start()
        applyMigrations(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @AfterAll
    fun stop() {
        postgres.stop()
    }

    @Test
    fun `V001 supports repository insert and capture trigger on live port`() {
        val db = JdbcDb(postgres.jdbcUrl, postgres.username, postgres.password)
        val store = JobBoardRepository(db, "lom", 50001)
        store.whenReady().join()
        val id = JobBoardRepository.newId()
        store.insert(JobBoardRepository.JOBS, id, mapOf("name" to "test", "uuid" to "u", "category" to "c", "poster" to "p")).join()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { c ->
            c.createStatement().use { st ->
                val outbox = st.executeQuery("SELECT kind FROM lom.job_outbox")
                assertTrue(outbox.next())
                assertEquals("create", outbox.getString(1))
            }
        }
    }
}