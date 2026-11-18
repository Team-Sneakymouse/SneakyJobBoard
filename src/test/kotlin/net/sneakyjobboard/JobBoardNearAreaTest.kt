package net.sneakyjobboard

import net.sneakyjobboard.jobboard.JobBoardUpdater
import org.bukkit.Location
import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID
import kotlin.math.floor
import kotlin.random.Random

class JobBoardNearAreaTest {
    private val radius = JobBoardUpdater.NEAR_RADIUS

    private fun world(): World = mock(World::class.java).also { `when`(it.uid).thenReturn(UUID.randomUUID()) }

    /** Squared distance from the centre to the nearest point of the block holding the position. */
    private fun blockGapSquared(x: Double, y: Double, z: Double, c: DoubleArray): Double {
        fun gap(p: Double, cc: Double): Double {
            val b = floor(p)
            return if (cc < b) b - cc else if (cc > b + 1) cc - (b + 1) else 0.0
        }
        val dx = gap(x, c[0]); val dy = gap(y, c[1]); val dz = gap(z, c[2])
        return dx * dx + dy * dy + dz * dz
    }

    @Test
    fun `every position the old distance check counted is inside the area`() {
        val random = Random(1)
        for (centre in listOf(doubleArrayOf(100.0, 64.0, -20.0), doubleArrayOf(-3.5, 70.25, 12.75))) {
            val world = world()
            val area = JobBoardUpdater.nearArea(world, Location(world, centre[0], centre[1], centre[2]))
            repeat(20_000) {
                val x = centre[0] + random.nextDouble(-radius, radius)
                val y = centre[1] + random.nextDouble(-radius, radius)
                val z = centre[2] + random.nextDouble(-radius, radius)
                val dSq = (x - centre[0]).let { it * it } + (y - centre[1]).let { it * it } + (z - centre[2]).let { it * it }
                if (dSq <= radius * radius) assertTrue(area.contains(x, y, z), "($x, $y, $z) is within $radius")
            }
            // Exactly on the sphere along each axis.
            assertTrue(area.contains(centre[0] + radius, centre[1], centre[2]))
            assertTrue(area.contains(centre[0], centre[1] - radius, centre[2]))
            assertTrue(area.contains(centre[0], centre[1], centre[2] - radius))
        }
    }

    @Test
    fun `blocks with no point within the radius are outside`() {
        val random = Random(2)
        val centre = doubleArrayOf(100.0, 64.0, -20.0)
        val world = world()
        val area = JobBoardUpdater.nearArea(world, Location(world, centre[0], centre[1], centre[2]))
        repeat(20_000) {
            val x = centre[0] + random.nextDouble(-14.0, 14.0)
            val y = centre[1] + random.nextDouble(-14.0, 14.0)
            val z = centre[2] + random.nextDouble(-14.0, 14.0)
            // A block whose span only touches the sphere from below (gap exactly the radius) holds no point within it,
            // since a block is half-open; skip that boundary case.
            val gapSq = blockGapSquared(x, y, z, centre)
            if (gapSq < radius * radius) assertTrue(area.contains(x, y, z), "($x, $y, $z)")
            if (gapSq > radius * radius) assertFalse(area.contains(x, y, z), "($x, $y, $z)")
        }
        // A box corner block far outside the sphere.
        assertFalse(area.contains(centre[0] + 9.5, centre[1] + 9.5, centre[2] + 9.5))
    }

    @Test
    fun `memberships stay in config order whatever order the areas are entered in`() {
        val boards = List(5) { Any() }
        val order = { b: Any -> boards.indexOfFirst { it === b } }
        val list = mutableListOf<Any>()
        for (i in listOf(3, 0, 4, 1, 3, 2)) JobBoardUpdater.insertInOrder(list, boards[i], order)
        assertTrue(list.size == 5 && list.indices.all { list[it] === boards[it] }, "got $list")
        list.removeAt(2)
        JobBoardUpdater.insertInOrder(list, boards[2], order)
        assertTrue(list.indices.all { list[it] === boards[it] })
    }
}
