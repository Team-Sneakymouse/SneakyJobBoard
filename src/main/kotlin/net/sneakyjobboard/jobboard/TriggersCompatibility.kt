package net.sneakyjobboard.jobboard

import com.sneakyutils.SneakyUtils
import com.sneakyutils.triggers.AreaOptions
import com.sneakyutils.triggers.PlayerTimerOptions

/**
 * Startup check that the installed SneakyUtils has the trigger API this plugin uses. A SneakyUtils older than the
 * compile pin fails here with a linkage error instead of part-way through enabling.
 */
object TriggersCompatibility {
    /** Null when the trigger API is usable, otherwise why not. */
    fun problem(): String? = try {
        SneakyUtils.getTriggers()
        AreaOptions.builder().label("sneakyjobboard:board-near").build()
        PlayerTimerOptions.builder().label("sneakyjobboard:board-look").build()
        null
    } catch (e: LinkageError) {
        "SneakyUtils is too old for SneakyJobBoard (no shared trigger API: $e); install SneakyUtils 2026.11.18-e359806ed982 or newer"
    } catch (e: IllegalStateException) {
        "SneakyUtils triggers are unavailable (${e.message})"
    }
}
