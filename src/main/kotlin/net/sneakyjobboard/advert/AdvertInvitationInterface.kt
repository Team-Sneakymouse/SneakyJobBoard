package net.sneakyjobboard.advert

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Manages the UI for viewing and interacting with advert invitations.
 */
class AdvertInvitationInterface(private val player: Player) : InventoryHolder {
    private val invitations = SneakyJobBoard.getAdvertManager().getActiveInvitationsForPlayer(player)
    private val inventory: Inventory = createInventory()

    init {
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Creates an inventory sized appropriately for the number of invitations.
     */
    private fun createInventory(): Inventory {
        // Calculate rows needed (9 slots per row)
        val rows = ((invitations.size + 8) / 9).coerceIn(1, 6)
        return Bukkit.createInventory(this, rows * 9, TextUtility.convertToComponent("&6Your Invitations"))
    }

    /**
     * Updates the inventory with current invitations.
     */
    private fun updateInventory() {
        inventory.clear()

        // Add invitations to the inventory
        invitations.forEachIndexed { index, invitation ->
            if (index < inventory.size) {
                inventory.setItem(index, invitation.createDisplayItem())
            }
        }
    }

    companion object {
        fun open(player: Player) {
            val ui = AdvertInvitationInterface(player)
            player.openInventory(ui.inventory)
        }
    }
}

/**
 * Listener for handling invitation UI interactions.
 */
class AdvertInvitationListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is AdvertInvitationInterface) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        val invitationId = clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyJobBoard.getAdvertManager().INVITATION_IDKEY,
            PersistentDataType.STRING
        ) ?: return

        val invitation = SneakyJobBoard.getAdvertManager().getInvitation(invitationId) ?: return

        // Teleport the player to the invitation location
        player.teleport(invitation.location)
        player.sendMessage(TextUtility.convertToComponent("&aTeleported to invitation location!"))
        player.closeInventory()
    }
	
} 