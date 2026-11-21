package net.sneakyjobboard

import net.sneakyjobboard.job.JobCategoryPicker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JobCategoryPickerTest {
    @Test fun `configured slots win and the rest fill free slots around the history button`() {
        val layout = JobCategoryPicker.layout(
            categories = listOf("a", "b", "c", "d"),
            configured = mapOf("b" to 0, "c" to 0, "x" to 5),
            reserved = setOf(1)
        )
        assertEquals(mapOf(0 to "b", 2 to "a", 3 to "c", 4 to "d"), layout)
    }

    @Test fun `out-of-range slots are treated as unconfigured`() {
        assertEquals(mapOf(0 to "a"), JobCategoryPicker.layout(listOf("a"), mapOf("a" to 54), emptySet()))
    }
}
