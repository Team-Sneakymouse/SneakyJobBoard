package net.sneakyjobboard.advert

import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobboard.JobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.Material
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import kotlin.math.floor

/** Manages listed adverts and dispatching. */
class AdvertManager {

    val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")

    val adverts = mutableMapOf<String, Advert>()

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

	/**
     * Returns the ItemStack that represents this advert, including metadata.
     * @return The item representing this advert.
     */
    fun getIconItem(): ItemStack {
        val itemStack = ItemStack(category?.iconMaterial ?: Material.AIR)
        val customModelData: Int = category?.iconCustomModelData ?: 0

        val meta = itemStack.itemMeta

        // Set custom model data, display name, and lore.
        meta.setCustomModelData(customModelData)
        meta.displayName(TextUtility.convertToComponent("&a${name}"))

        val lore = mutableListOf<String>()

        // Split the description into lines of a maximum length
        val descriptionLines = TextUtility.splitIntoLines(description, 30)

        // Add each line of the description to the lore
        for (line in descriptionLines) {
            lore.add("&e$line")
        }

        lore.add(posterString)

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data.
        val persistentData = meta.persistentDataContainer
        persistentData.set(NamespacedKey(SneakyJobBoard.getInstance(), "advert_id"), PersistentDataType.STRING, uuid)

        meta.setEnchantmentGlintOverride(true)

        itemStack.itemMeta = meta
        return itemStack
    }
} 