package net.sneakyjobboard.advert

import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobboard.JobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import kotlin.math.floor

/** Manages listed adverts, invitations, and dispatching. */
class AdvertManager {

    val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")
    val INVITATION_IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "invitation_id")

    val adverts = mutableMapOf<String, Advert>()
    private val invitations = mutableMapOf<String, Invitation>()

    /**
     * Lists a new advert, spawning its display and scheduling unlisting.
     * @param advert The advert to be listed.
     */
    fun list(advert: Advert) {
        // TODO: Implement pocketbase manager for adverts
        adverts[advert.uuid] = advert
    }

    /**
     * Gets the collection of currently listed adverts.
     * @return A mutable collection of adverts.
     */
    fun getAdverts(): MutableCollection<Advert> {
        return adverts.values
    }

    /**
     * Creates a new invitation for an advert.
     * @param advert The advert being responded to
     * @param inviter The player creating the invitation
     * @return The created invitation
     */
    fun createInvitation(advert: Advert, inviter: Player): Invitation {
        val invitation = Invitation(
            advert = advert,
            inviter = inviter,
            location = inviter.location
        )
        invitations[invitation.id] = invitation
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
    fun cleanupExpiredInvitations() {
        val now = System.currentTimeMillis()
        val expireDuration = SneakyJobBoard.getInstance().config.getLong("invitation-expire-duration", 300000)
        invitations.values.removeIf { (now - it.startTime) >= expireDuration }
    }

    /**
     * Dispatches a player to the specified advert.
     * @param uuid The UUID of the advert to dispatch to.
     * @param pl The player to be dispatched.
     */
    fun dispatch(uuid: String, pl: Player) {
        val advert = adverts[uuid] ?: return

        if (advert.player.isOnline) {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${pl.name} jobboard-dispatch-self ${floor(advert.location.x)} ${floor(advert.location.y)} ${
                    floor(advert.location.z)
                }"
            )
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${advert.player.name} jobboard-dispatch-other ${pl.name} ${advert.category?.iconMaterial} ${advert.category?.iconCustomModelData}"
            )
        } else {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${pl.name} jobboard-dispatch-self-offline ${floor(advert.location.x)} ${floor(advert.location.y)} ${
                    floor(advert.location.z)
                }"
            )
        }
    }
}

/**
 * Represents an invitation from a player responding to an advert.
 */
data class Invitation(
    val advert: Advert,
    val inviter: Player,
    val location: Location
) {
    val id: String = UUID.randomUUID().toString()
    val startTime: Long = System.currentTimeMillis()

    /**
     * Creates an ItemStack representing this invitation in the UI.
     * @return The ItemStack for this invitation
     */
    fun createDisplayItem(): ItemStack {
        val itemStack = ItemStack(Material.PAPER)
        val meta = itemStack.itemMeta

        meta.displayName(TextUtility.convertToComponent("&aInvitation from ${inviter.name}"))

        val lore = mutableListOf<String>()
        lore.add("&eFor your advert: &f${advert.name}")
        lore.add("&eLocation: &f${location.blockX}, ${location.blockY}, ${location.blockZ}")
        
        // Calculate time remaining
        val now = System.currentTimeMillis()
        val expireDuration = SneakyJobBoard.getInstance().config.getLong("invitation-expire-duration", 300000)
        val progress = (now - startTime).toDouble() / expireDuration.toDouble()
        val barLength = 20
        val filledBars = (barLength * (1.0 - progress)).toInt().coerceIn(0, barLength)
        val progressBar = "&a" + "█".repeat(filledBars) + "&7" + "█".repeat(barLength - filledBars)
        lore.add("&eTime remaining: $progressBar")

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data
        val container = meta.persistentDataContainer
        container.set(
            SneakyJobBoard.getInstance().advertManager.INVITATION_IDKEY,
            PersistentDataType.STRING,
            id
        )

        itemStack.itemMeta = meta
        return itemStack
    }
}

class Advert(
    val category: AdvertCategory?,
    val player: Player
) {
    val uuid = UUID.randomUUID().toString()
    var recordID = ""
    var location = player.location
    var name: String = category?.name ?: ""
    var description: String = category?.description ?: ""
    val posterString = if (SneakyJobBoard.isPapiActive()) PlaceholderAPI.setPlaceholders(player, SneakyJobBoard.getInstance().getConfig().getString("poster-string") ?: "&ePosted by: &b[playerName]").replace("[playerName]", player.name) else (SneakyJobBoard.getInstance().getConfig().getString("poster-string") ?: "&ePosted by: &b[playerName]").replace("[playerName]", player.name)
    var iconMaterial: Material? = null
    var iconCustomModelData: Int? = null

    /**
     * Returns the ItemStack that represents this advert, including metadata.
     * @return The item representing this advert.
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
        persistentData.set(NamespacedKey(SneakyJobBoard.getInstance(), "advert_id"), PersistentDataType.STRING, uuid)

        meta.setEnchantmentGlintOverride(true)

        itemStack.itemMeta = meta
        return itemStack
    }
} 