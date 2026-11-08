package net.sneakyjobboard

import net.sneakyjobboard.jobboard.JobBoard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class JobBoardDepthOffsetTest {
    @Test
    fun `offset is small, non-negative and stable per job`() {
        repeat(1000) {
            val id = UUID.randomUUID().toString()
            val offset = JobBoard.getDepthOffset(id)
            assertTrue(offset >= 0.0 && offset < JobBoard.MAX_DEPTH_OFFSET)
            assertEquals(offset, JobBoard.getDepthOffset(id))
        }
    }

    @Test
    fun `different jobs get different offsets`() {
        val offsets = (1..50).map { JobBoard.getDepthOffset(UUID.randomUUID().toString()) }.toSet()
        assertTrue(offsets.size > 40)
    }
}
