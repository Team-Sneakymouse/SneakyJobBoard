package net.sneakyjobboard

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull

/**
 * Provides PlaceholderAPI integration for the SneakyJobBoard plugin.
 * Exposes placeholders for job and advertisement information.
 */
class Placeholders : PlaceholderExpansion() {

    /**
     * Returns the identifier used for this placeholder expansion.
     * @return The plugin's identifier
     */
    override fun getIdentifier(): @NotNull String {
        return SneakyJobBoard.IDENTIFIER
    }

    /**
     * Returns the author(s) of this placeholder expansion.
     * @return The plugin's authors
     */
    override fun getAuthor(): @NotNull String {
        return SneakyJobBoard.AUTHORS
    }

    /**
     * Returns the version of this placeholder expansion.
     * @return The plugin's version
     */
    override fun getVersion(): @NotNull String {
        return SneakyJobBoard.VERSION
    }

    /**
     * Indicates that this expansion should persist through reloads.
     * @return true to persist
     */
    override fun persist(): Boolean {
        return true
    }

    /**
     * Processes placeholder requests and returns the appropriate value.
     * Available placeholders:
     * - %sneakyjobboard_listed_jobs% - Returns a list of the player's active jobs
     * - %sneakyjobboard_listed_adverts% - Returns a list of the player's active advertisements
     *
     * @param player The player to get placeholder data for
     * @param params The placeholder identifier being requested
     * @return The requested placeholder value, or null if invalid
     */
    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return null

        val placeholder = params.lowercase()

        return when (placeholder) {
            "listed_jobs" -> {
                val jobs = SneakyJobBoard.getJobManager().jobs.values.filter { it.player == player }.map { it.name }
                jobs.takeIf { it.isNotEmpty() }?.joinToString("|") ?: "none"
            }

            "listed_adverts" -> {
                val adverts =
                    SneakyJobBoard.getAdvertManager().getAdvertsForPlayer(player).filter { it.enabled }.map { it.name }
                adverts.takeIf { it.isNotEmpty() }?.joinToString("|") ?: "none"
            }

            else -> null
        }
    }
}
