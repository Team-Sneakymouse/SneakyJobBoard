package net.sneakyjobboard

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryBuilderFactory
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.data.InlinedRegistryBuilderProvider
import io.papermc.paper.registry.data.dialog.*
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import net.sneakyjobboard.commands.CommandListJob
import net.sneakyjobboard.dialog.TextInputDialogs
import net.sneakyjobboard.job.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Registry
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.util.UUID
import java.util.function.Consumer

class DialogInputTest {
    private val resources = mutableListOf<AutoCloseable>()
    private lateinit var old: Any
    private val instance = SneakyJobBoard::class.java.getDeclaredField("instance").apply { isAccessible = true }
    private lateinit var plugin: SneakyJobBoard
    private lateinit var player: Player
    private lateinit var category: JobCategory
    private lateinit var jobs: JobManager
    private lateinit var bukkit: org.mockito.MockedStatic<Bukkit>
    private lateinit var provider: DialogInstancesProvider
    private val callbacks = mutableListOf<DialogActionCallback>()
    private val tasks = mutableListOf<Runnable>()

    @BeforeEach
    fun setup() {
        old = instance.get(null) ?: Unit
        plugin = mock(SneakyJobBoard::class.java)
        `when`(plugin.config).thenReturn(YamlConfiguration())
        `when`(plugin.isEnabled).thenReturn(true)
        instance.set(null, plugin)
        category = mock(JobCategory::class.java)
        `when`(category.name).thenReturn("work")
        `when`(category.description).thenReturn("description")
        val categories = mock(JobCategoryManager::class.java)
        `when`(categories.getJobCategories()).thenReturn(mutableMapOf("work" to category))
        setManager("jobCategoryManager", categories)
        jobs = mock(JobManager::class.java)
        setManager("jobManager", jobs)
        player = mock(Player::class.java)
        `when`(player.name).thenReturn("Poster")
        `when`(player.uniqueId).thenReturn(UUID.randomUUID())
        `when`(player.isOnline).thenReturn(true)
        `when`(player.location).thenReturn(Location(null, 1.0, 2.0, 3.0))
        bukkit = mockStatic(Bukkit::class.java).also { resources.add(it) }
        bukkit.`when`<PluginManager> { Bukkit.getPluginManager() }.thenReturn(mock(PluginManager::class.java))
        val scheduler = mock(BukkitScheduler::class.java)
        `when`(scheduler.runTask(eq(plugin), any(Runnable::class.java))).thenAnswer { tasks.add(it.getArgument(1)); mock(org.bukkit.scheduler.BukkitTask::class.java) }
        bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
        bukkit.`when`<Boolean> { Bukkit.isPrimaryThread() }.thenReturn(true)
        val access = mockStatic(RegistryAccess::class.java).also { resources.add(it) }
        val registryAccess = mock(RegistryAccess::class.java, RETURNS_DEEP_STUBS)
        access.`when`<RegistryAccess> { RegistryAccess.registryAccess() }.thenReturn(registryAccess)
        @Suppress("UNCHECKED_CAST")
        val registry = mock(Registry::class.java) as Registry<Dialog>
        `when`(registryAccess.getRegistry(RegistryKey.DIALOG)).thenReturn(registry)
        provider = mock(DialogInstancesProvider::class.java, RETURNS_DEEP_STUBS)
        val providers = mockStatic(DialogInstancesProvider::class.java).also { resources.add(it) }
        providers.`when`<DialogInstancesProvider> { DialogInstancesProvider.instance() }.thenReturn(provider)
        `when`(provider.register(any(), any())).thenAnswer {
            callbacks.add(it.getArgument(0)); mock(DialogAction.CustomClickAction::class.java)
        }
        val inline = mockStatic(InlinedRegistryBuilderProvider::class.java).also { resources.add(it) }
        val inlined = mock(InlinedRegistryBuilderProvider::class.java)
        inline.`when`<InlinedRegistryBuilderProvider> { InlinedRegistryBuilderProvider.instance() }.thenReturn(inlined)
        `when`(inlined.createDialog(any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val factory = mock(RegistryBuilderFactory::class.java, RETURNS_DEEP_STUBS) as RegistryBuilderFactory<Dialog, DialogRegistryEntry.Builder>
            val builder = mock(DialogRegistryEntry.Builder::class.java, RETURNS_SELF)
            `when`(factory.empty()).thenReturn(builder)
            it.getArgument<Consumer<RegistryBuilderFactory<Dialog, DialogRegistryEntry.Builder>>>(0).accept(factory)
            mock(Dialog::class.java)
        }
    }

    private fun setManager(name: String, value: Any) {
        SneakyJobBoard::class.java.getDeclaredField(name).apply { isAccessible = true }.set(plugin, value)
    }

    @AfterEach
    fun cleanup() {
        resources.reversed().forEach { it.close() }
        instance.set(null, if (old === Unit) null else old)
    }

    private fun response(title: String? = "Title", description: String? = "Description"): DialogResponseView {
        val response = mock(DialogResponseView::class.java)
        `when`(response.getText("title")).thenReturn(title)
        `when`(response.getText("description")).thenReturn(description)
        return response
    }

    private fun openJob() = CommandListJob().execute(player, "listjob", arrayOf("work", "1000"))

    @Test
    fun `interactive job command constructs two inputs and shows a dialog`() {
        assertTrue(openJob())
        verify(player).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
        verify(provider).textBuilder(eq("title"), any())
        verify(provider).textBuilder(eq("description"), any())
        verify(provider).multilineOptions(null, 100)
        verifyNoInteractions(jobs)
    }

    @Test
    fun `duplicate submit lists a job only once`() {
        openJob()
        val submit = callbacks.first()
        submit.accept(response(), player)
        submit.accept(response(), player)
        tasks.toList().forEach { it.run() }
        assertEquals(1, mockingDetails(jobs).invocations.count { it.method.name == "list" })
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["missing", "blank", "pipe", "long-title", "long-description", "newline-title", "cancel", "offline", "disabled", "replaced", "foreign"])
    fun `unsafe submissions never list a job`(reason: String) {
        openJob()
        val submit = callbacks.first()
        val response = when (reason) {
            "missing" -> response(null)
            "blank" -> response(" ")
            "pipe" -> response("|||")
            "long-title" -> response("x".repeat(129))
            "long-description" -> response(description = "x".repeat(4097))
            "newline-title" -> response("a\nb")
            else -> response()
        }
        when (reason) {
            "cancel" -> callbacks.last().accept(response, player)
            "offline" -> `when`(player.isOnline).thenReturn(false)
            "disabled" -> `when`(plugin.isEnabled).thenReturn(false)
            "replaced" -> openJob()
        }
        submit.accept(response, if (reason == "foreign") mock(Player::class.java) else player)
        tasks.toList().forEach { it.run() }
        verifyNoInteractions(jobs)
    }

    @Test
    fun `submission defers all mutation to a server task and preserves job escaping`() {
        openJob()
        callbacks.first().accept(response("&a<red>|Work", "&b<blue>|Details\nMore"), player)
        verifyNoInteractions(jobs)
        assertEquals(1, tasks.size)
        tasks.single().run()
        val job = mockingDetails(jobs).invocations.single { it.method.name == "list" }.arguments[0] as Job
        assertEquals(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(com.sneakyutils.util.TextUtility.replaceFormatCodes("&a<red>Work")), job.name)
        assertEquals(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(com.sneakyutils.util.TextUtility.replaceFormatCodes("&b<blue>|Details\nMore")), job.description)
    }

    @Test
    fun `advert creation after icon selection submits both raw fields together`() {
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        `when`(adverts.getAdvertsForPlayer(player)).thenReturn(mutableListOf())
        net.sneakyjobboard.commands.CommandListAdvert.startAdvertCreation(player, org.bukkit.Material.DIAMOND, 42, null)
        verify(player).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
        callbacks.first().accept(response("&a<red>|Title", "&b<blue>|Description\nMore"), player)
        assertTrue(mockingDetails(adverts).invocations.none { it.method.name == "list" })
        tasks.toList().forEach { it.run() }
        val advert = mockingDetails(adverts).invocations.single { it.method.name == "list" }.arguments[0] as net.sneakyjobboard.advert.Advert
        assertEquals("&a<red>|Title", advert.name)
        assertEquals("&b<blue>|Description\nMore", advert.description)
        assertEquals(org.bukkit.Material.DIAMOND, advert.iconMaterial)
        assertEquals(42, advert.iconCustomModelData)
    }

    @Test
    fun `advert limit is rechecked when submitting after icon selection`() {
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        `when`(adverts.getAdvertsForPlayer(player)).thenReturn(MutableList(8) { mock(net.sneakyjobboard.advert.Advert::class.java) })
        net.sneakyjobboard.commands.CommandListAdvert.startAdvertCreation(player, org.bukkit.Material.PAPER, 0, null)
        callbacks.first().accept(response(), player)
        tasks.toList().forEach { it.run() }
        assertTrue(mockingDetails(adverts).invocations.none { it.method.name == "list" })
    }

    private fun clickEdit(advert: net.sneakyjobboard.advert.Advert, slot: Int = 6, rawSlot: Int = slot) {
        val holder = mock(net.sneakyjobboard.advert.AdvertEditInterface::class.java)
        `when`(holder.advert).thenReturn(advert)
        val inventory = mock(org.bukkit.inventory.Inventory::class.java)
        `when`(inventory.holder).thenReturn(holder)
        val event = mock(org.bukkit.event.inventory.InventoryClickEvent::class.java)
        `when`(event.inventory).thenReturn(inventory)
        `when`(event.whoClicked).thenReturn(player)
        `when`(event.slot).thenReturn(slot)
        `when`(event.rawSlot).thenReturn(rawSlot)
        val view = mock(org.bukkit.inventory.InventoryView::class.java)
        `when`(event.view).thenReturn(view)
        `when`(player.openInventory).thenReturn(view)
        net.sneakyjobboard.advert.AdvertEditListener().onInventoryClick(event)
        verify(event).setCancelled(true)
    }

    @Test
    fun `edit menu opens prefilled dialog and saves both fields atomically`() {
        val advert = net.sneakyjobboard.advert.Advert(null, player).apply { name = "Old"; description = "Old description" }
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        `when`(adverts.getAdvert(advert.uuid)).thenReturn(advert)
        val pocketbase = mock(PocketbaseManager::class.java)
        setManager("pocketBaseManager", pocketbase)
        clickEdit(advert)
        verify(player, never()).closeInventory()
        verify(player, never()).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
        assertEquals(1, tasks.size)
        tasks.removeAt(0).run()
        verify(player).closeInventory()
        verify(player).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
        verify(provider.textBuilder("title", net.kyori.adventure.text.Component.text("Title"))).initial("Old")
        verify(provider.textBuilder("description", net.kyori.adventure.text.Component.text("Description"))).initial("Old description")
        assertEquals("Old", advert.name)
        callbacks.first().accept(response("New", "New description"), player)
        assertEquals("Old", advert.name)
        mockConstruction(net.sneakyjobboard.advert.AdvertEditInterface::class.java) { holder, _ ->
            `when`(holder.inventory).thenReturn(mock(org.bukkit.inventory.Inventory::class.java))
        }.use { tasks.toList().forEach { it.run() } }
        assertEquals("New", advert.name)
        assertEquals("New description", advert.description)
        verify(pocketbase).updateAdvert(advert)
    }

    private fun clickCreationIcon() {
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        val key = org.bukkit.NamespacedKey("test", "icon")
        `when`(adverts.IDKEY).thenReturn(key)
        val holder = mock(net.sneakyjobboard.advert.AdvertIconSelector::class.java)
        val inventory = mock(org.bukkit.inventory.Inventory::class.java)
        `when`(inventory.holder).thenReturn(holder)
        val view = mock(org.bukkit.inventory.InventoryView::class.java)
        `when`(player.openInventory).thenReturn(view)
        val item = mock(org.bukkit.inventory.ItemStack::class.java, RETURNS_DEEP_STUBS)
        `when`(item.itemMeta.persistentDataContainer.get(key, org.bukkit.persistence.PersistentDataType.STRING)).thenReturn("DIAMOND,42")
        val event = mock(org.bukkit.event.inventory.InventoryClickEvent::class.java)
        `when`(event.inventory).thenReturn(inventory)
        `when`(event.view).thenReturn(view)
        `when`(event.whoClicked).thenReturn(player)
        `when`(event.currentItem).thenReturn(item)
        net.sneakyjobboard.advert.AdvertIconSelectorListener().onInventoryClick(event)
        verify(event).setCancelled(true)
    }

    @Test
    fun `creation icon click defers close and dialog until next tick`() {
        clickCreationIcon()
        verify(player, never()).closeInventory()
        verify(player, never()).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
        assertEquals(1, tasks.size)
        tasks.removeAt(0).run()
        verify(player).closeInventory()
        verify(player).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "edit,view", "edit,offline", "edit,disabled", "edit,replaced", "edit,quit", "edit,disable",
        "create,view", "create,offline", "create,disabled", "create,replaced", "create,quit", "create,disable"
    )
    fun `queued inventory transition rejects stale lifecycle`(flow: String, reason: String) {
        if (flow == "edit") clickEdit(net.sneakyjobboard.advert.Advert(null, player)) else clickCreationIcon()
        when (reason) {
            "view" -> `when`(player.openInventory).thenReturn(mock(org.bukkit.inventory.InventoryView::class.java))
            "offline" -> `when`(player.isOnline).thenReturn(false)
            "disabled" -> `when`(plugin.isEnabled).thenReturn(false)
            "replaced" -> { openJob(); clearInvocations(player) }
            "quit" -> TextInputDialogs.onQuit(mock(org.bukkit.event.player.PlayerQuitEvent::class.java).also { `when`(it.player).thenReturn(player) })
            "disable" -> TextInputDialogs.onDisable(org.bukkit.event.server.PluginDisableEvent(plugin))
        }
        tasks.toList().forEach { it.run() }
        verify(player, never()).closeInventory()
        verify(player, never()).showDialog(any(net.kyori.adventure.dialog.DialogLike::class.java))
    }

    @Test
    fun `disabled category button does not start chat input`() {
        val advert = net.sneakyjobboard.advert.Advert(null, player)
        setManager("advertCategoryManager", mock(net.sneakyjobboard.advert.AdvertCategoryManager::class.java, RETURNS_DEEP_STUBS))
        clickEdit(advert, 2)
        verify(player, never()).closeInventory()
        assertTrue(callbacks.isEmpty())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["deleted", "removed", "replaced", "foreign", "cancel"])
    fun `edit submission rejects stale adverts and cancellation without model mutation`(reason: String) {
        val owner = if (reason == "foreign") mock(Player::class.java).also { `when`(it.name).thenReturn("Other") } else player
        val advert = net.sneakyjobboard.advert.Advert(null, owner).apply { name = "Old"; description = "Old description" }
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        `when`(adverts.getAdvert(advert.uuid)).thenReturn(advert)
        val pocketbase = mock(PocketbaseManager::class.java)
        setManager("pocketBaseManager", pocketbase)
        TextInputDialogs.editAdvert(player, advert)
        if (reason == "foreign") assertTrue(callbacks.isEmpty(), "Foreign players must not see prefilled advert text")
        when (reason) {
            "deleted" -> advert.deleted = true
            "removed" -> `when`(adverts.getAdvert(advert.uuid)).thenReturn(null)
            "replaced" -> {
                val replacement = net.sneakyjobboard.advert.Advert(null, player)
                `when`(adverts.getAdvert(advert.uuid)).thenReturn(replacement)
            }
        }
        if (callbacks.isNotEmpty()) {
            if (reason == "cancel") callbacks.last().accept(response(), player)
            callbacks.first().accept(response(), player)
        }
        mockConstruction(net.sneakyjobboard.advert.AdvertEditInterface::class.java) { holder, _ ->
            `when`(holder.inventory).thenReturn(mock(org.bukkit.inventory.Inventory::class.java))
        }.use { tasks.toList().forEach { it.run() } }
        assertEquals("Old", advert.name)
        assertEquals("Old description", advert.description)
        verifyNoInteractions(pocketbase)
    }

    @Test
    fun `bottom inventory book slot does not open text dialog`() {
        clickEdit(net.sneakyjobboard.advert.Advert(null, player), 6, 15)
        verify(player, never()).closeInventory()
        assertTrue(callbacks.isEmpty())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["quit", "disable"])
    fun `lifecycle cleanup invalidates even queued submissions`(reason: String) {
        openJob()
        callbacks.first().accept(response(), player)
        val event: org.bukkit.event.Event = if (reason == "quit") mock(org.bukkit.event.player.PlayerQuitEvent::class.java).also { `when`(it.player).thenReturn(player) }
            else org.bukkit.event.server.PluginDisableEvent(plugin)
        val handler = TextInputDialogs.javaClass.methods.firstOrNull {
            it.isAnnotationPresent(org.bukkit.event.EventHandler::class.java) && it.parameterTypes.contentEquals(arrayOf(event.javaClass))
        }
        assertTrue(handler != null, "Dialog drafts need a lifecycle handler")
        handler!!.invoke(TextInputDialogs, event)
        tasks.toList().forEach { it.run() }
        verifyNoInteractions(jobs)
    }

    @Test
    fun `unlist cancels an outstanding draft even when no job is listed`() {
        openJob()
        net.sneakyjobboard.commands.CommandUnlistJob().execute(player, "unlistjob", arrayOf("expire"))
        clearInvocations(jobs)
        callbacks.first().accept(response(), player)
        tasks.toList().forEach { it.run() }
        verifyNoInteractions(jobs)
    }

    @Test
    fun `explicit job command preserves raw text and replaces a pending draft`() {
        openJob()
        assertTrue(CommandListJob().execute(player, "listjob", arrayOf("work", "1000", "\"&a<red>|Explicit\"", "\"Raw description\"")))
        callbacks.first().accept(response(), player)
        tasks.toList().forEach { it.run() }
        val listed = mockingDetails(jobs).invocations.filter { it.method.name == "list" }
        assertEquals(1, listed.size)
        assertEquals("&a<red>|Explicit", (listed.single().arguments[0] as Job).name)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["foreign", "deleted", "already-listed", "cancel"])
    fun `advert draft cannot be listed after invalidation`(reason: String) {
        val owner = if (reason == "foreign") mock(Player::class.java).also { `when`(it.name).thenReturn("Other") } else player
        val advert = net.sneakyjobboard.advert.Advert(null, owner)
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        `when`(adverts.getAdvertsForPlayer(player)).thenReturn(mutableListOf())
        TextInputDialogs.createAdvert(player, advert)
        if (reason == "deleted") advert.deleted = true
        if (reason == "already-listed") `when`(adverts.getAdvert(advert.uuid)).thenReturn(advert)
        if (reason == "cancel") callbacks.last().accept(response(), player)
        callbacks.first().accept(response(), player)
        tasks.toList().forEach { it.run() }
        assertTrue(mockingDetails(adverts).invocations.none { it.method.name == "list" })
        assertEquals("", advert.name)
    }

    @Test
    fun `advert command keeps icon selector first and replaces previous text draft`() {
        val adverts = mock(net.sneakyjobboard.advert.AdvertManager::class.java)
        setManager("advertManager", adverts)
        `when`(adverts.getAdvertsForPlayer(player)).thenReturn(mutableListOf())
        openJob()
        val inventory = mock(org.bukkit.inventory.Inventory::class.java)
        mockConstruction(net.sneakyjobboard.advert.AdvertIconSelector::class.java) { holder, _ ->
            net.sneakyjobboard.advert.AdvertIconSelector::class.java.getDeclaredField("inventory").apply { isAccessible = true }.set(holder, inventory)
            `when`(holder.inventory).thenReturn(inventory)
        }.use { selectors ->
            assertTrue(net.sneakyjobboard.commands.CommandListAdvert().execute(player, "listadvert", emptyArray()))
            assertEquals(1, selectors.constructed().size)
            verify(player).openInventory(inventory)
        }
        assertEquals(2, callbacks.size, "No advert text dialog before icon selection")
        callbacks.first().accept(response(), player)
        tasks.toList().forEach { it.run() }
        verifyNoInteractions(jobs)
    }

    @Test
    fun `console explicit job commands retain target-player and location forms`() {
        val console = mock(org.bukkit.command.ConsoleCommandSender::class.java)
        bukkit.`when`<Player> { Bukkit.getPlayer("Poster") }.thenReturn(player)
        assertTrue(CommandListJob().execute(console, "listjob", arrayOf("Poster", "work", "1000", "true", "true", "\"Player job\"", "\"Player description\"")))
        val world = mock(org.bukkit.World::class.java)
        bukkit.`when`<org.bukkit.World> { Bukkit.getWorld("world") }.thenReturn(world)
        assertTrue(CommandListJob().execute(console, "listjob", arrayOf("work", "1000", "true", "\"Location job\"", "\"Location description\"", "world", "1", "2", "3")))
        val listed = mockingDetails(jobs).invocations.filter { it.method.name == "list" }.map { it.arguments[0] as Job }
        assertEquals(2, listed.size)
        assertSame(player, listed[0].player)
        assertTrue(listed[0].tracking && listed[0].persist)
        assertNull(listed[1].player)
        assertTrue(listed[1].persist)
        assertEquals(Location(world, 1.0, 2.0, 3.0), listed[1].location)
        assertTrue(callbacks.isEmpty())
    }

}
