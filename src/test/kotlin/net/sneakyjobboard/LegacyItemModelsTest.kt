package net.sneakyjobboard

import net.sneakyjobboard.advert.AdvertIconSelector
import net.sneakyjobboard.job.JobCategory
import net.sneakyjobboard.util.ConfiguredCustomModelData
import net.sneakyjobboard.util.ItemModelDefinition
import net.sneakyjobboard.util.ItemModelUtility
import net.sneakyjobboard.util.LegacyItemModels
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class LegacyItemModelsTest {
    private fun key(id: String) = NamespacedKey.fromString(id)!!

    @Test fun `bundled mapping resolves carrier and integer like the pack`() {
        val legacy = LegacyItemModels.bundled
        assertEquals(key("lom:icons/grayscale/1111"), legacy.resolve(Material.JIGSAW, 1))
        assertEquals(key("lom:icons/grayscale/aerialdodge"), legacy.resolve("minecraft:jigsaw", 2.9f))
        assertEquals(key("lom:icons/jobboard/bluefist"), legacy.resolve(Material.RED_STAINED_GLASS_PANE, 2001))
        assertEquals(key("lom:icons/jobboard/yellowswords"), legacy.resolve(Material.RED_STAINED_GLASS_PANE, 2024))
        // Open-ended final route: every integer at or above the last threshold keeps rendering that model.
        assertEquals(key("lom:tools/manastone5"), legacy.resolve(Material.AMETHYST_CLUSTER, 500))
        assertNull(legacy.resolve(Material.JIGSAW, 0))
        assertNull(legacy.resolve(Material.JIGSAW, -3))
        assertNull(legacy.resolve(Material.STONE, 1))
    }

    @Test fun `bundled mapping is the live advert-icons list for the live carrier`() {
        val bundled = YamlConfiguration()
        bundled.load(File("src/resources/config.yml"))
        val configured = bundled.getStringList("advert-icons.red_stained_glass_pane").map(::key)
        assertEquals(24, configured.size)
        assertEquals(configured, LegacyItemModels.bundled.resolveRange(Material.RED_STAINED_GLASS_PANE, 2001, 2024))
    }

    @Test fun `bundled defaults carry no integer custom model data for advert keys`() {
        val config = YamlConfiguration().apply { load(File("src/resources/config.yml")) }
        for (key in config.getConfigurationSection("advert-categories")!!.getKeys(false))
            assertFalse(config.contains("advert-categories.$key.icon-custom-model-data"))
        for (key in config.getConfigurationSection("advert-icons")!!.getKeys(false))
            config.getStringList("advert-icons.$key").forEach { assertTrue(it.contains(":"), it) }
        assertFalse(File("src/resources/config.yml").readText().lines().any { Regex("""icon-custom-model-data:\s*\d""").containsMatchIn(it) })
    }

    @Test fun `legacy integer category definition is swapped for its item model`() {
        val legacy = ItemModelDefinition(null, listOf(ConfiguredCustomModelData.Number(1f), ConfiguredCustomModelData.String("keep")))
        val migrated = ItemModelUtility.migrateLegacyInteger(legacy, Material.JIGSAW)
        assertEquals(key("lom:icons/grayscale/1111"), migrated.itemModel)
        assertEquals(listOf<ConfiguredCustomModelData>(ConfiguredCustomModelData.String("keep")), migrated.customModelData)
    }

    @Test fun `category definitions that already name a model or have no mapping are untouched`() {
        val named = ItemModelDefinition(key("lom:x"), listOf(ConfiguredCustomModelData.Number(1f)))
        assertSame(named, ItemModelUtility.migrateLegacyInteger(named, Material.JIGSAW))
        val unmapped = ItemModelDefinition(null, listOf(ConfiguredCustomModelData.Number(1f)))
        assertSame(unmapped, ItemModelUtility.migrateLegacyInteger(unmapped, Material.STONE))
        val empty = ItemModelDefinition(null, emptyList())
        assertSame(empty, ItemModelUtility.migrateLegacyInteger(empty, Material.JIGSAW))
    }

    @Test fun `toast icon arguments pass the item model and drop the legacy integer`() {
        assertEquals("\"JIGSAW\" \"lom:icons/grayscale/1111\"", ItemModelUtility.toastIconArguments(Material.JIGSAW, key("lom:icons/grayscale/1111")))
        assertEquals("\"JIGSAW\" \"\"", ItemModelUtility.toastIconArguments(Material.JIGSAW, null))
        fun category(model: ItemModelDefinition) = JobCategory("n", "d", Material.JIGSAW, model, Display.Brightness(0, 0),
            Transformation(Vector3f(), Quaternionf(), Vector3f(1f), Quaternionf()), "", "")
        assertEquals("\"JIGSAW\" \"lom:x\"", category(ItemModelDefinition(key("lom:x"), emptyList())).spellIconArguments)
        assertEquals("\"JIGSAW\" \"lom:icons/grayscale/1111\"", category(ItemModelDefinition(null, listOf(ConfiguredCustomModelData.Number(1f)))).spellIconArguments)
        assertEquals("\"JIGSAW\" \"\"", category(ItemModelDefinition(null, emptyList())).spellIconArguments)
    }

    @Test fun `advert-icons reads item-model lists and still converts legacy integer ranges`() {
        val section = YamlConfiguration().apply {
            loadFromString("""
                advert-icons:
                    red_stained_glass_pane:
                        - "lom:icons/jobboard/bluefist"
                        - "2002-2003"
                    jigsaw:
                        - 1
                        - "lom:icons/grayscale/airtime"
                    not_a_material:
                        - "lom:ignored"
            """.trimIndent())
        }.getConfigurationSection("advert-icons")!!
        val icons = AdvertIconSelector.readIcons(section)
        assertEquals(
            listOf(
                AdvertIconSelector.IconData(Material.RED_STAINED_GLASS_PANE, key("lom:icons/jobboard/bluefist")),
                AdvertIconSelector.IconData(Material.RED_STAINED_GLASS_PANE, key("lom:icons/jobboard/blueharp")),
                AdvertIconSelector.IconData(Material.RED_STAINED_GLASS_PANE, key("lom:icons/jobboard/bluemagic")),
                AdvertIconSelector.IconData(Material.JIGSAW, key("lom:icons/grayscale/1111")),
                AdvertIconSelector.IconData(Material.JIGSAW, key("lom:icons/grayscale/airtime"))
            ),
            icons
        )
    }
}
