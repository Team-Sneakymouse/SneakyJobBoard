package net.sneakyjobboard

import com.sneakydb.SneakyDBApi
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture

/** SQL-only canonical persistence. SneakyDB owns connections; operators own DDL and delivery triggers. */
class JobBoardRepository(private val api: SneakyDBApi, schema: String, val port: Int) : AutoCloseable {
    private val schemaName = schema.also { require(it.matches(Regex("[a-z_][a-z0-9_]{0,62}"))) { "Invalid database-schema" } }
    @Volatile private var closed = false
    private val tails = mutableMapOf<String, CompletableFuture<*>>()
    private val jobColumns = setOf("uuid", "category", "poster", "posterDisplayString", "posterIconBase64", "location", "locationDisplayString", "startTime", "durationMillis", "tracking", "persist", "name", "description", "discordEmbedIcon", "endTime", "endReason")
    private val advertColumns = setOf("uuid", "category", "posteruuid", "posterDisplayString", "name", "description", "iconMaterial", "iconCustomModelData", "enabled", "deleted")
    /**
     * Additive advert column holding the stored item-model id. Operator-provisioned like every other column, so it is
     * probed read-only at startup: when absent the plugin keeps working and simply cannot persist new advert icons.
     */
    private val itemModelColumns = setOf(ADVERT_ITEM_MODEL)
    @Volatile private var advertItemModelColumn = false
    private val readiness: CompletableFuture<Void>

    init {
        require(port in 1..65535) { "A positive backend listening port is required" }
        readiness = api.whenReady().thenCompose {
            checkOpen()
            api.query("SELECT " + (listOf("id", "serverPort", "discordMessageId", "created", "updated") + jobColumns).joinToString(", ") { quote(it) } + " FROM ${table(JOBS)} WHERE false", emptyList<Any>())
        }.thenCompose {
            checkOpen()
            api.query("SELECT " + (listOf("id", "serverPort", "created", "updated") + advertColumns).joinToString(", ") { quote(it) } + " FROM ${table(ADVERTS)} WHERE false", emptyList<Any>())
        }.thenCompose {
            checkOpen()
            api.query("SELECT ${quote(ADVERT_ITEM_MODEL)} FROM ${table(ADVERTS)} WHERE false", emptyList<Any>())
                .handle { _, error -> advertItemModelColumn = error == null }
        }.thenApply { null }
    }

    /** Whether the operator has added the advert item-model column. Meaningful once [whenReady] has completed. */
    fun hasAdvertItemModelColumn(): Boolean = advertItemModelColumn

    fun whenReady(): CompletableFuture<Void> = readiness.copy()
    private fun checkOpen() = check(!closed) { "Job board persistence is closed" }
    private fun quote(value: String) = "\"$value\""
    private fun table(name: String): String {
        require(name == JOBS || name == ADVERTS) { "Unknown canonical table" }
        return "${quote(schemaName)}.${quote(name)}"
    }
    private fun columns(name: String) = when (name) { JOBS -> jobColumns; ADVERTS -> advertColumns + itemModelColumns; else -> throw IllegalArgumentException("Unknown canonical table") }
    /** Drops the optional item-model column from a write when the operator schema does not have it yet. */
    private fun writable(name: String, data: Map<String, Any>): Map<String, Any> =
        if (name == ADVERTS && !advertItemModelColumn) data.filterKeys { it !in itemModelColumns } else data
    private fun read(sql: String, values: List<*>): CompletableFuture<List<Map<String, Any?>>> = readiness.thenCompose {
        checkOpen(); api.query(sql, values)
    }
    fun reconcile(boot: Long) = read("SELECT * FROM ${table(JOBS)} WHERE \"endTime\" = 0 AND \"serverPort\" = ? AND (\"startTime\" < ? OR persist = true)", listOf(port, boot))
    fun history(player: String) = read("SELECT * FROM ${table(JOBS)} WHERE poster = ? AND \"serverPort\" = ? ORDER BY created DESC, id DESC", listOf(player, port))
    fun adverts(player: String) = read("SELECT * FROM ${table(ADVERTS)} WHERE posteruuid = ? AND deleted = false AND \"serverPort\" = ? ORDER BY created DESC, id DESC", listOf(player, port))

    /** Failed writes remain fenced: never retry uncertain INSERTs or permit dependent updates past failure. */
    @Synchronized private fun <T> ordered(key: String, operation: () -> CompletableFuture<T>): CompletableFuture<T> {
        val previous = tails[key] ?: readiness
        val next = previous.thenCompose { checkOpen(); operation() }
        tails[key] = next
        next.whenComplete { _, error ->
            synchronized(this) { if (error == null && tails[key] === next) tails.remove(key) }
        }
        return next
    }

    fun insert(name: String, id: String, data: Map<String, Any>, icon: CompletableFuture<String>? = null): CompletableFuture<List<Map<String, Any?>>> {
        require(id.matches(Regex("[a-z0-9]{15}"))) { "Invalid record ID" }
        require(data.keys.all { it in columns(name) }) { "Invalid canonical insert column" }
        val requested = data.toMap()
        return ordered("$name/$id") {
            (icon ?: CompletableFuture.completedFuture(null)).thenCompose { preparedIcon ->
                checkOpen()
                val snapshot = linkedMapOf<String, Any>("id" to id, "serverPort" to port).apply { putAll(writable(name, requested)) }
                if (preparedIcon != null) snapshot["posterIconBase64"] = preparedIcon
                val sql = "INSERT INTO ${table(name)} (${snapshot.keys.joinToString(", ") { quote(it) }}) VALUES (${snapshot.keys.joinToString(", ") { "?" }}) RETURNING id"
                api.query(sql, snapshot.values.toList()).thenApply { rows ->
                    check(rows.size == 1 && rows.single()["id"] == id) { "Insert did not confirm the preassigned record ID" }
                    rows
                }
            }
        }
    }
    fun update(name: String, id: String, data: Map<String, Any>): CompletableFuture<Int> {
        require(data.isNotEmpty() && data.keys.all { it in columns(name) }) { "Invalid canonical update column" }
        val requested = data.toMap()
        return ordered("$name/$id") {
            val snapshot = writable(name, requested)
            // Nothing the schema can store (e.g. only the item-model column on an unmigrated schema): no-op, not a failure.
            if (snapshot.isEmpty()) return@ordered CompletableFuture.completedFuture(1)
            val sql = "UPDATE ${table(name)} SET ${snapshot.keys.joinToString(", ") { "${quote(it)} = ?" }} WHERE id = ? AND \"serverPort\" = ?"
            api.execute(sql, snapshot.values.toList() + listOf(id, port)).thenApply { count ->
                check(count == 1) { "Update did not match exactly one owned record" }
                count
            }
        }
    }
    override fun close() { closed = true; synchronized(this) { tails.clear() } }

    companion object {
        const val JOBS = "lom2_listed_jobs"
        const val ADVERTS = "lom2_adverts"
        /** Advert item-model id (`namespace:path`), empty when unset. Replaces the numeric `iconCustomModelData`. */
        const val ADVERT_ITEM_MODEL = "iconItemModel"
        private val random = SecureRandom()
        private const val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        fun newId(): String = (1..15).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}
