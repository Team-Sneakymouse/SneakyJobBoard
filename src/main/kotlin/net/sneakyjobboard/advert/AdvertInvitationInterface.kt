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
    private val inventory: Inventory = Bukkit.createInventory(this, 27, TextUtility.convertToComponent("&6Your Invitations"))

    init {
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Updates the inventory with current invitations.
     */
    private fun updateInventory() {
        inventory.clear()

        // Get active invitations for the player
        val invitations = SneakyJobBoard.getAdvertManager().getActiveInvitationsForPlayer(player)

        // Add invitations to the inventory
        invitations.forEachIndexed { index, invitation ->
            if (index < inventory.size) {
                inventory.setItem(index, invitation.createDisplayItem())
            }
        }

        // Fill empty slots with glass panes
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) {
                val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                val meta = filler.itemMeta
                meta.displayName(TextUtility.convertToComponent(""))
                filler.itemMeta = meta
                inventory.setItem(i, filler)
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
        if (holder !is AdvertInvitationUI) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        val invitationId = clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyJobBoard.getInstance().advertManager.INVITATION_IDKEY,
            PersistentDataType.STRING
        ) ?: return

        val invitation = SneakyJobBoard.getAdvertManager().getInvitation(invitationId) ?: return

        // Teleport the player to the invitation location
        player.teleport(invitation.location)
        player.sendMessage(TextUtility.convertToComponent("&aTeleported to invitation location!"))
        player.closeInventory()
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is AdvertInvitationUI) return
        // Additional cleanup if needed
    }
} 