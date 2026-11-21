package net.sneakyjobboard

import com.sneakyutils.util.TextUtility
import com.sneakyutils.util.Toasts
import com.sneakyutils.util.Waypoints
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.SoundCategory
import org.bukkit.entity.Player

/** The icon a toast shows: [material] carrying the optional [itemModel]. */
data class ToastIcon(val material: Material, val itemModel: NamespacedKey?)

/**
 * What a player sees and hears at each job board step, from `effects.<step>` in config.yml. These were the MagicSpells
 * spells SneakyJobBoard force-cast (`jobboard-listed`, `jobboard-dispatch-self`, `jobboard-dispatch-self-offline`,
 * `jobboard-dispatch-other`, `jobboard-invitation-received`, `advert-dispatch-other`, and the open sound of
 * `jobboard-menu-default`). A step can have a chat `message`, a `toast` with the job or advert icon, `sounds` only that
 * player hears, and a `waypoint` (a path style such as `paladin`) pointing them at the job through SneakyUtils'
 * [Waypoints]. The text gets the `%placeholder%` values passed in. Server thread only.
 */
object JobBoardEffects {

    const val JOB_LISTED = "job-listed"
    const val DISPATCHED_SELF = "dispatched-self"
    const val DISPATCHED_SELF_OFFLINE = "dispatched-self-offline"
    const val JOB_DISPATCHED_OTHER = "job-dispatched-other"
    const val INVITATION_RECEIVED = "invitation-received"
    const val ADVERT_DISPATCHED_OTHER = "advert-dispatched-other"
    const val MENU_OPEN = "menu-open"

    /**
     * Plays [step] to [player]. [icon] is the toast icon (paper when null); [target] is where the waypoint points.
     * Unknown or empty steps do nothing.
     */
    fun play(
        step: String,
        player: Player,
        vars: Map<String, String> = emptyMap(),
        icon: ToastIcon? = null,
        target: Location? = null
    ) {
        if (!player.isOnline) return
        val section = SneakyJobBoard.getInstance().config.getConfigurationSection("effects.$step") ?: return
        val all = vars + ("player" to player.name)
        fun fill(text: String): String = all.entries.fold(text) { acc, (k, v) -> acc.replace("%$k%", v) }

        section.getString("message")?.takeIf { it.isNotBlank() }?.let {
            player.sendMessage(TextUtility.convertToComponent(fill(it)))
        }
        section.getString("toast")?.takeIf { it.isNotBlank() }?.let {
            Toasts.show(player, fill(it), icon?.material ?: Material.PAPER, icon?.itemModel)
        }
        for (sound in section.getMapList("sounds")) {
            val key = sound["sound"]?.toString()?.takeIf { it.isNotBlank() } ?: continue
            val volume = (sound["volume"] as? Number)?.toFloat() ?: 1f
            val pitch = (sound["pitch"] as? Number)?.toFloat() ?: 1f
            player.playSound(player.location, key, SoundCategory.MASTER, volume, pitch)
        }
        val waypoint = section.getString("waypoint")
        if (target != null && !waypoint.isNullOrBlank()) {
            Waypoints.guide(player, target, waypoint)
        }
    }
}
