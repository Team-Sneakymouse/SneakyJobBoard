package net.sneakyjobboard.dialog

import com.sneakyutils.util.TextUtility
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.minimessage.MiniMessage
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.advert.Advert
import net.sneakyjobboard.advert.AdvertEditInterface
import net.sneakyjobboard.job.Job
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import java.util.UUID

/** Server-thread-owned drafts. No model is mutated until a validated Submit. */
object TextInputDialogs : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) { cancel(event.player) }

    @EventHandler
    fun onDisable(event: PluginDisableEvent) {
        if (event.plugin === SneakyJobBoard.getInstance()) pending.clear()
    }

    private val pending = mutableMapOf<UUID, Any>()

    fun cancel(player: Player) { pending.remove(player.uniqueId) }

    /** Leave the click event before changing views; superseded flows must not reopen UI. */
    fun fromInventory(player: Player, view: org.bukkit.inventory.InventoryView, open: () -> Unit) {
        val plugin = SneakyJobBoard.getInstance()
        val token = Any()
        pending[player.uniqueId] = token
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (pending[player.uniqueId] !== token) return@Runnable
            pending.remove(player.uniqueId)
            if (!plugin.isEnabled || !player.isOnline || player.openInventory !== view) return@Runnable
            player.closeInventory()
            open()
        })
    }

    fun job(player: Player, job: Job) {
        open(player, "List Job", "Name", "", "", "Submit", true) { title, description ->
            job.name = escape(title.replace("|", ""))
            job.description = escape(description)
            SneakyJobBoard.getJobManager().list(job)
            player.sendMessage(Component.text("Job listed."))
        }
    }

    fun createAdvert(player: Player, advert: Advert) {
        open(player, "List Advertisement", "Title", "", "", "Submit") { title, description ->
            if (advert.player !== player || advert.deleted || SneakyJobBoard.getAdvertManager().getAdvert(advert.uuid) != null) return@open
            if (SneakyJobBoard.getAdvertManager().getAdvertsForPlayer(player).size >= 8) {
                player.sendMessage(Component.text("You can only have up to 8 active advertisements at a time."))
                return@open
            }
            advert.name = title
            advert.description = description
            SneakyJobBoard.getAdvertManager().list(advert)
            player.sendMessage(Component.text("Advert listed successfully!"))
        }
    }

    fun editAdvert(player: Player, advert: Advert) {
        if (advert.player !== player) return
        open(player, "Edit Advertisement", "Title", advert.name, advert.description, "Save") { title, description ->
            if (advert.player !== player || advert.deleted || SneakyJobBoard.getAdvertManager().getAdvert(advert.uuid) !== advert) {
                player.sendMessage(Component.text("This advertisement is no longer available to edit."))
                return@open
            }
            advert.name = title
            advert.description = description
            SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
            AdvertEditInterface.open(player, advert)
        }
    }

    private fun escape(text: String) = MiniMessage.miniMessage().escapeTags(TextUtility.replaceFormatCodes(text))

    private fun open(
        player: Player, heading: String, titleLabel: String, initialTitle: String,
        initialDescription: String, submitLabel: String, sanitizeJobTitle: Boolean = false,
        submit: (String, String) -> Unit
    ) {
        val plugin = SneakyJobBoard.getInstance()
        val token = Any()
        pending[player.uniqueId] = token
        val options = ClickCallback.Options.builder().uses(1).build()
        fun action(cancel: Boolean) = DialogAction.customClick({ response, audience ->
            if (audience !== player || !plugin.isEnabled) return@customClick
            // Read the response now; apply all Bukkit/model operations on the server thread.
            val title = response.getText("title")
            val description = response.getText("description")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!plugin.isEnabled || !player.isOnline || pending[player.uniqueId] !== token) return@Runnable
                pending.remove(player.uniqueId)
                if (cancel) return@Runnable
                val checkedTitle = if (sanitizeJobTitle) title?.replace("|", "") else title
                if (title == null || description == null || checkedTitle.isNullOrBlank() ||
                    description.isBlank() || title.length > 128 || description.length > 4096 ||
                    title.any { it == '\n' || it == '\r' } ||
                    (title + description).any { it.isISOControl() && it != '\n' && it != '\t' }
                ) {
                    player.sendMessage(Component.text("Please enter a title (1–128 characters) and description (1–4096 characters)."))
                    return@Runnable
                }
                submit(title, description)
            })
        }, options)
        val dialog = Dialog.create { factory ->
            factory.empty().base(DialogBase.builder(Component.text(heading))
                .canCloseWithEscape(true).pause(false)
                .inputs(listOf(
                    DialogInput.text("title", Component.text(titleLabel)).initial(initialTitle).maxLength(128).build(),
                    DialogInput.text("description", Component.text("Description")).initial(initialDescription).maxLength(4096)
                        .multiline(TextDialogInput.MultilineOptions.create(null, 100)).build()
                )).build())
                .type(DialogType.confirmation(
                    ActionButton.create(Component.text(submitLabel), null, 100, action(false)),
                    ActionButton.create(Component.text("Cancel"), null, 100, action(true))
                ))
        }
        player.showDialog(dialog)
    }
}
