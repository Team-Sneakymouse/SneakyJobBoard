package net.sneakyjobboard.util

import com.google.gson.JsonParser
import net.sneakyjobboard.SneakyJobBoard
import org.bukkit.Material
import org.bukkit.NamespacedKey
import java.io.Reader
import java.math.BigDecimal

/**
 * Read-only lookup from the legacy (carrier material, integer custom model data) pair to the item model that replaced it.
 *
 * Data is the bundled `legacy-item-models.json`, generated from craftengine-resources'
 * `legacy-item-models.json` by `tools/generate-legacy-item-models.py`. It exists only so integers that were stored
 * or configured before item models were introduced keep resolving; nothing here produces integer model data.
 */
class LegacyItemModels private constructor(private val carriers: Map<String, List<Route>>) {

    private class Route(val min: Int, val maxExclusive: Int?, val model: NamespacedKey)

    /** Resolves the item model for a carrier and integer value, or null when the pack defines none. */
    fun resolve(carrier: Material, value: Number): NamespacedKey? = resolve(carrier.name, value)

    fun resolve(carrier: String, value: Number): NamespacedKey? {
        val integer = (value as? BigDecimal ?: BigDecimal(value.toString())).toInt()
        if (integer <= 0) return null
        return carriers[carrier.lowercase().removePrefix("minecraft:")]
            ?.firstOrNull { integer >= it.min && (it.maxExclusive == null || integer < it.maxExclusive) }
            ?.model
    }

    /** Resolves every integer in `start..end` (inclusive), skipping integers that have no model. */
    fun resolveRange(carrier: Material, start: Int, end: Int): List<NamespacedKey> =
        (start..end).mapNotNull { resolve(carrier, it) }.distinct()

    companion object {
        /** Lazily parsed bundled mapping; empty (with a log line) if the resource is missing or malformed. */
        val bundled: LegacyItemModels by lazy {
            val stream = LegacyItemModels::class.java.classLoader.getResourceAsStream(RESOURCE)
            if (stream == null) {
                SneakyJobBoard.log("Missing bundled $RESOURCE; legacy integer icons cannot be converted")
                EMPTY
            } else {
                runCatching { stream.reader(Charsets.UTF_8).use(::parse) }.getOrElse {
                    SneakyJobBoard.log("Invalid bundled $RESOURCE: ${it.message}")
                    EMPTY
                }
            }
        }

        private const val RESOURCE = "legacy-item-models.json"
        private val EMPTY = LegacyItemModels(emptyMap())

        fun parse(reader: Reader): LegacyItemModels {
            val carriers = JsonParser.parseReader(reader).asJsonObject.getAsJsonObject("carriers")
            return LegacyItemModels(carriers.entrySet().associate { (carrier, routes) ->
                carrier to routes.asJsonArray.map { route ->
                    val entry = route.asJsonArray
                    Route(
                        entry[0].asInt,
                        entry[1].takeUnless { it.isJsonNull }?.asInt,
                        requireNotNull(NamespacedKey.fromString(entry[2].asString)) { "Invalid item model ${entry[2]}" }
                    )
                }.sortedBy { it.min }
            })
        }
    }
}
