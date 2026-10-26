package net.sneakyjobboard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PostgresContractTest {
    @Test
    fun `runtime uses only pinned SneakyDB and requires joined classpath`() {
        val build = Files.readString(Path.of("build.gradle.kts"))
        val descriptor = Files.readString(Path.of("src/resources/paper-plugin.yml"))
        assertTrue(build.contains("sneakydb-api:2026.10.26-fd4e29e90ad0"))
        assertFalse(build.contains("sneakypocketbase"))
        assertTrue(descriptor.contains("SneakyDB:\n      load: BEFORE\n      required: true\n      join-classpath: true"))
    }
}
