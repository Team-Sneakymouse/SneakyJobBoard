package net.sneakyjobboard.advert

import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*
import kotlin.math.floor

/**
 * Manages the lifecycle and operations of advertisements in the job board system.
 * This class handles listing, unlisting, and dispatching of advertisements, as well as
 * managing invitations between players.
 */
class AdvertManager : Listener {

    val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")
    val INVITATION_IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "invitation_id")

    private val adverts = mutableMapOf<String, Advert>()
    private val invitations = mutableMapOf<String, Invitation>()

    /**
     * Lists a new advertisement in the system.
     * @param advert The advertisement to be listed
     * @param sendToPocketbase Whether to persist the advertisement to PocketBase storage
     */
    fun list(advert: Advert, sendToPocketbase: Boolean = true) {
        adverts[advert.uuid] = advert
        if (sendToPocketbase) SneakyJobBoard.getPocketbaseManager().listAdvert(advert)
    }

    /**
     * Unlists an advertisement from the system.
     * @param advert The advertisement to unlist
     */
    fun unlist(advert: Advert) {
        advert.deleted = true
        SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
        adverts.remove(advert.uuid)
    }

    /**
     * Gets an advertisement by its UUID.
     * @param uuid The UUID of the advertisement.
     * @return The advertisement if found, null otherwise.
     */
    fun getAdvert(uuid: String): Advert? = adverts[uuid]

    /**
     * Gets the collection of currently listed advertisements from online players.
     * @return A mutable collection of advertisements.
     */
    fun getAdverts(): MutableCollection<Advert> {
        return adverts.values.filter { it.player.isOnline && it.enabled }.toMutableList()
    }

    /**
     * Gets the advertisements for a specific player.
     * @param player The player to get advertisements for.
     * @return A mutable collection of advertisements.
     */
    fun getAdvertsForPlayer(player: Player): MutableCollection<Advert> {
        return adverts.values.filter { it.player == player }.toMutableList()
    }

    /**
     * Creates a new invitation for an advertisement.
     * @param advert The advertisement being responded to
     * @param inviter The player creating the invitation
     * @return The created invitation
     */
    fun createInvitation(advert: Advert, inviter: Player): Invitation {
        val invitation = Invitation(
            advert = advert, inviter = inviter, location = inviter.location
        )
        invitations[invitation.id] = invitation

        Bukkit.getServer().dispatchCommand(
            Bukkit.getServer().consoleSender,
            "cast forcecast ${invitation.inviter.name} jobboard-invitation-received ${invitation.advert.iconMaterial} ${invitation.advert.iconCustomModelData}"
        )

        return invitation
    }

    /**
     * Gets an invitation by its ID.
     * @param id The ID of the invitation
     * @return The invitation if found, null otherwise
     */
    fun getInvitation(id: String): Invitation? = invitations[id]

    /**
     * Gets all active invitations for a player.
     * @param player The player to get invitations for
     * @return List of active invitations
     */
    fun getActiveInvitationsForPlayer(player: Player): List<Invitation> {
        cleanupExpiredInvitations()
        return invitations.values.filter {
            it.advert.player == player
        }
    }

    /**
     * Removes expired invitations.
     */
    private fun cleanupExpiredInvitations() {
        val now = System.currentTimeMillis()
        val expireDuration = SneakyJobBoard.getInstance().config.getLong("invitation-expire-duration", 300000)
        invitations.values.removeIf { (now - it.startTime) >= expireDuration }
    }

    /**
     * Removes all advertisements for a specific player.
     * @param player The player whose advertisements should be removed
     */
    private fun removePlayerAdverts(player: Player) {
        adverts.values.removeIf { it.player == player }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Load player's advertisement history from PocketBase
        SneakyJobBoard.getPocketbaseManager().getAdvertHistory(event.player.uniqueId.toString())
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Clean up player's advertisements
        removePlayerAdverts(event.player)
    }

    /**
     * Dispatches a player to the specified advertisement.
     * @param invitation The invitation to dispatch to.
     * @param pl The player to be dispatched.
     */
    fun dispatch(invitation: Invitation) {
        if (invitation.advert.player.isOnline) {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${invitation.advert.player.name} jobboard-dispatch-self ${floor(invitation.location.x)} ${floor(invitation.location.y)} ${
                    floor(invitation.location.z)
                }"
            )
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${invitation.inviter.name} advert-dispatch-other ${invitation.advert.player.name} ${invitation.advert.iconMaterial} ${invitation.advert.iconCustomModelData}"
            )
        } else {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${invitation.advert.player.name} jobboard-dispatch-self-offline ${floor(invitation.location.x)} ${floor(invitation.location.y)} ${
                    floor(invitation.location.z)
                }"
            )
        }
    }
}

/**
 * Represents an invitation from a player responding to an advertisement.
 */
data class Invitation(
    val advert: Advert, val inviter: Player, val location: Location
) {
    val id: String = UUID.randomUUID().toString()
    val startTime: Long = System.currentTimeMillis()
    private val posterString = if (SneakyJobBoard.isPapiActive()) PlaceholderAPI.setPlaceholders(
        inviter,
        SneakyJobBoard.getInstance().getConfig().getString("poster-string") ?: "&eFrom: &b[playerName]"
    ).replace("[playerName]", inviter.name) else (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
        ?: "&eFrom: &b[playerName]").replace("[playerName]", inviter.name)
    private var displayStringLocation =
        (SneakyJobBoard.getInstance().getConfig().getString("pocketbase-location") ?: "[x],[y],[z]").replace(
            "[x]", location.blockX.toString()
        ).replace("[y]", location.blockY.toString()).replace("[z]", location.blockZ.toString())

    /**
     * Creates an ItemStack representing this invitation in the UI.
     * @return The ItemStack for this invitation
     */
    fun createDisplayItem(): ItemStack {
        val itemStack = ItemStack(advert.iconMaterial ?: advert.category?.iconMaterial ?: Material.PAPER)
        val meta = itemStack.itemMeta

        // Set custom model data if available
        advert.iconCustomModelData?.let { meta.setCustomModelData(it) }
            ?: advert.category?.iconCustomModelData?.let { meta.setCustomModelData(it) }

        meta.displayName(TextUtility.convertToComponent("&a${advert.name}"))

        val lore = mutableListOf<String>()
        lore.add(posterString)
        lore.add("&eLocation: &b${displayStringLocation}")

        // Calculate time remaining
        val now = System.currentTimeMillis()
        val expireDuration = SneakyJobBoard.getInstance().config.getLong("invitation-expire-duration", 300000)
        val progress = (now - startTime).toDouble() / expireDuration.toDouble()
        val barLength = 10
        val filledBars = (barLength * (1.0 - progress)).toInt().coerceIn(0, barLength)
        val progressBar = "&a" + "█".repeat(filledBars) + "&7" + "█".repeat(barLength - filledBars)
        lore.add("&eTime remaining:")
        lore.add(progressBar)

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data
        val container = meta.persistentDataContainer
        container.set(
            SneakyJobBoard.getAdvertManager().INVITATION_IDKEY, PersistentDataType.STRING, id
        )

        itemStack.itemMeta = meta
        return itemStack
    }
}

class Advert(
    var category: AdvertCategory?, val player: Player
) {
    var uuid: String = UUID.randomUUID().toString()
    var recordID = ""
    var name: String = category?.name ?: ""
    var description: String = category?.description ?: ""
    var posterString: String = if (SneakyJobBoard.isPapiActive()) PlaceholderAPI.setPlaceholders(
        player,
        SneakyJobBoard.getInstance().getConfig().getString("poster-string") ?: "&eFrom: &b[playerName]"
    ).replace("[playerName]", player.name) else (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
        ?: "&eFrom: &b[playerName]").replace("[playerName]", player.name)
    var iconMaterial: Material? = null
    var iconCustomModelData: Int? = null
    var enabled: Boolean = true
    var deleted: Boolean = false

    /**
     * Returns the ItemStack that represents this advertisement, including metadata.
     * @return The item representing this advertisement.
     */
    fun getIconItem(): ItemStack {
        val itemStack = ItemStack(iconMaterial ?: category?.iconMaterial ?: Material.BARRIER)
        val meta = itemStack.itemMeta ?: return itemStack

        // Set custom model data, display name, and lore.
        iconCustomModelData?.let { meta.setCustomModelData(it) }
            ?: category?.iconCustomModelData?.let { meta.setCustomModelData(it) }

        meta.displayName(TextUtility.convertToComponent("&a${name}"))

        val lore = mutableListOf<String>()

        // Split the description into lines of a maximum length
        val descriptionLines = TextUtility.splitIntoLines(description, 30)

        // Add each line of the description to the lore
        for (line in descriptionLines) {
            lore.add("&e$line")
        }

        lore.add(posterString)
        lore.add("&7Click to send an invitation!")

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data.
        val persistentData = meta.persistentDataContainer
        persistentData.set(SneakyJobBoard.getAdvertManager().IDKEY, PersistentDataType.STRING, uuid)

        itemStack.itemMeta = meta
        return itemStack
    }
} 