package net.sneakyjobboard.advert

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.persistence.PersistentDataType

/**
 * Interface for viewing and accepting advertisement invitations.
 * Shows active invitations with their remaining time and location.
 *
 * @property player The player viewing their invitations
 */
class AdvertInvitationInterface(private val player: Player) : InventoryHolder {
    private val invitations = SneakyJobBoard.getAdvertManager().getActiveInvitationsForPlayer(player)
    private val inventory: Inventory = createInventory()

    init {
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Creates the base inventory for invitation display.
     * Sizes the inventory based on the number of active invitations.
     * @return A new inventory with appropriate size and title
     */
    private fun createInventory(): Inventory {
        // Calculate rows needed (9 slots per row)
        val rows = ((invitations.size + 8) / 9).coerceIn(1, 6)
        return Bukkit.createInventory(this, rows * 9, TextUtility.convertToComponent("&6Your Invitations"))
    }

    /**
     * Updates the inventory with current invitations.
     * Each invitation shows sender, location, and remaining time.
     */
    private fun updateInventory() {
        inventory.clear()
        invitations.forEachIndexed { index, invitation ->
            if (index < inventory.size) {
                inventory.setItem(index, invitation.createDisplayItem())
            }
        }
    }

    companion object {
        /**
         * Opens the invitation interface for a player.
         * @param player The player to show invitations to
         */
        fun open(player: Player) {
            val ui = AdvertInvitationInterface(player)
            player.openInventory(ui.inventory)
        }
    }
}

/**
 * Handles inventory interaction events for the invitation interface.
 * Processes invitation acceptance and prevents inventory manipulation.
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
            SneakyJobBoard.getAdvertManager().INVITATION_IDKEY, PersistentDataType.STRING
        ) ?: return

        val invitation = SneakyJobBoard.getAdvertManager().getInvitation(invitationId) ?: return

        player.closeInventory()

        SneakyJobBoard.getAdvertManager().dispatch(invitation, player)
    }

} 