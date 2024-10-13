package net.sneakyjobboard

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull

class Placeholders : PlaceholderExpansion() {

    override fun getIdentifier(): @NotNull String {
        return SneakyJobBoard.IDENTIFIER
    }

    override fun getAuthor(): @NotNull String {
        return SneakyJobBoard.AUTHORS
    }

    override fun getVersion(): @NotNull String {
        return SneakyJobBoard.VERSION
    }

    override fun persist(): Boolean {
        return true
    }

    override fun onPlaceholderRequest(player: Player, params: String): String? {
        val placeholder = params.lowercase()

        return when (placeholder) {
            "listed_jobs" -> {
                val jobs =
                        SneakyJobBoard.getJobManager()
                                .jobs
                                .values
                                .filter { it.player == player }
                                .map { it.name }

                jobs.takeIf { it.isNotEmpty() }?.joinToString("|") ?: "none"
            }
            else -> null
        }
    }
}
