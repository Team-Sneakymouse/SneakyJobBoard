package net.sneakyjobboard

import net.sneakyjobboard.jobboard.JobBoardMenu
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JobBoardMenuTest {
    @Test fun `click areas match the old jobboard-menu-default options`() {
        assertEquals("places", JobBoardMenu.actionFor(0))
        assertEquals("places", JobBoardMenu.actionFor(44))
        assertEquals("people", JobBoardMenu.actionFor(1))
        assertEquals("people", JobBoardMenu.actionFor(48))
        assertEquals("close", JobBoardMenu.actionFor(53))
        assertNull(JobBoardMenu.actionFor(4))
        assertNull(JobBoardMenu.actionFor(13))
    }
}
