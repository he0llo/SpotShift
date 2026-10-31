package he.spotshift.llo.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

data class TileKey(val x: Int, val y: Int, val z: Int) {
    val parent: TileKey? get() = if (z > AmapTiles.MIN_ZOOM) TileKey(x / 2, y / 2, z - 1) else null
}

object AmapTiles {
    const val MIN_ZOOM = 3
    const val MAX_ZOOM = 18
    const val TILE_SIZE = 256f

    /** Gaode raster tiles (XYZ, GCJ-02). Style 7 is the standard street map. */
    fun url(key: TileKey): String {
        val server = (key.x + key.y) % 4 + 1
        return "https://webst0$server.is.autonavi.com/appmaptile?style=7&x=${key.x}&y=${key.y}&z=${key.z}"
    }
}

/**
 * Two-level cache for Gaode tiles:
 * - memory LruCache, so panning back is instant;
 * - disk cache in cacheDir, so zooming/relaunching does not hit the network again.
 * Requests are de-duplicated per tile, failed tiles cool down before retrying,
 * and callers can prefetch neighbouring zoom levels while a gesture settles.
 */
class TileSource private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(4)
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(16 shl 20, 64 shl 20)
    private val memory = object : LruCache<TileKey, ImageBitmap>(maxMemory) {
        override fun sizeOf(key: TileKey, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val inFlight = ConcurrentHashMap<TileKey, Job>()
    private val coolingDown = ConcurrentHashMap<TileKey, Long>()
    private val diskDir = File(appContext.cacheDir, "amap_tiles")

    /** Bumped whenever a tile becomes available; read inside the canvas draw phase. */
    val revision = mutableIntStateOf(0)

    init {
        scope.launch(io) { pruneDisk() }
    }

    fun get(key: TileKey): ImageBitmap? = memory.get(key)

    fun ensure(keys: Collection<TileKey>) {
        keys.forEach(::load)
    }

    fun prefetch(keys: Collection<TileKey>) {
        keys.forEach(::load)
    }

    /** Clear the failure cooldown for tiles still missing from the current viewport. */
    fun retry(keys: Collection<TileKey>) {
        keys.forEach { key ->
            if (memory.get(key) == null) {
                coolingDown.remove(key)
                load(key)
            }
        }
    }

    private fun load(key: TileKey) {
        if (memory.get(key) != null) return
        val failedAt = coolingDown[key]
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_COOLDOWN_MS) return

        val job = scope.launch(io, start = CoroutineStart.LAZY) {
            try {
                val loaded = readDisk(key) ?: download(key)?.also { tile ->
                    tile.bytes?.let { writeDisk(key, it) }
                }
                if (loaded != null) {
                    memory.put(key, loaded.bitmap)
                    coolingDown.remove(key)
                    scope.launch { revision.intValue++ }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coolingDown[key] = System.currentTimeMillis()
            } finally {
                inFlight.remove(key)
            }
        }
        if (inFlight.putIfAbsent(key, job) != null) {
            job.cancel()
        } else {
            job.start()
        }
    }

    private fun readDisk(key: TileKey): LoadedTile? {
        val file = diskFile(key)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return LoadedTile(bitmap.asImageBitmap(), null)
    }

    private fun download(key: TileKey): LoadedTile? {
        val request = Request.Builder()
            .url(AmapTiles.url(key))
            .header("User-Agent", "SpotShift/1.0 (Android)")
            .header("Referer", "https://www.amap.com/")
            .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty tile body")
            val bytes = body.bytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Unable to decode tile")
            return LoadedTile(bitmap.asImageBitmap(), bytes)
        }
    }

    private fun writeDisk(key: TileKey, bytes: ByteArray) {
        runCatching {
            val file = diskFile(key)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun diskFile(key: TileKey): File =
        File(File(File(diskDir, key.z.toString()), key.x.toString()), "${key.y}.img")

    /** Keep the disk cache bounded without scanning it on every tile. */
    private fun pruneDisk() {
        val files = diskDir.walkTopDown().filter { it.isFile }.toList()
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_FILES)
            .forEach { it.delete() }
    }

    private class LoadedTile(val bitmap: ImageBitmap, val bytes: ByteArray?)

    companion object {
        private const val MAX_DISK_FILES = 1_500
        private const val RETRY_COOLDOWN_MS = 20_000L

        @Volatile
        private var instance: TileSource? = null

        fun get(context: Context): TileSource =
            instance ?: synchronized(this) {
                instance ?: TileSource(context).also { instance = it }
            }
    }
}

/** Web-Mercator helpers shared by drawing and gesture handling. */
object Mercator {
    const val MAX_LAT = 85.05112878

    fun worldX(lng: Double, zoom: Float): Double = (lng + 180.0) / 360.0 * 2.0.pow(zoom.toDouble())

    fun worldY(lat: Double, zoom: Float): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val rad = Math.toRadians(clamped)
        return (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / Math.PI) / 2.0 * 2.0.pow(zoom.toDouble())
    }

    fun lngOf(worldX: Double, zoom: Float): Double {
        val raw = worldX / 2.0.pow(zoom.toDouble()) * 360.0 - 180.0
        return ((raw + 180.0).mod(360.0)) - 180.0
    }

    fun latOf(worldY: Double, zoom: Float): Double {
        val n = Math.PI - 2.0 * Math.PI * worldY / 2.0.pow(zoom.toDouble())
        return Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n))).coerceIn(-MAX_LAT, MAX_LAT)
    }

    /** Integer tile range covering a viewport, with wrapped x and clamped y. */
    fun visibleKeys(
        centerLat: Double,
        centerLng: Double,
        zoom: Float,
        width: Float,
        height: Float,
    ): List<TileKey> {
        if (width <= 0f || height <= 0f) return emptyList()
        val z = floor(zoom).toInt().coerceIn(AmapTiles.MIN_ZOOM, AmapTiles.MAX_ZOOM)
        val scale = 2.0.pow((zoom - z).toDouble()).toFloat()
        val worldTiles = 1 shl z
        val centerX = worldX(centerLng, z.toFloat())
        val centerY = worldY(centerLat, z.toFloat())
        val halfW = width / (2 * AmapTiles.TILE_SIZE * scale)
        val halfH = height / (2 * AmapTiles.TILE_SIZE * scale)

        val minX = floor(centerX - halfW).toInt() - 1
        val maxX = ceil(centerX + halfW).toInt() + 1
        val minY = floor(centerY - halfH).toInt() - 1
        val maxY = ceil(centerY + halfH).toInt() + 1

        val keys = ArrayList<TileKey>((maxX - minX + 1) * (maxY - minY + 1))
        for (y in minY..maxY) {
            if (y !in 0 until worldTiles) continue
            for (x in minX..maxX) {
                val wrapped = ((x % worldTiles) + worldTiles) % worldTiles
                keys += TileKey(wrapped, y, z)
            }
        }
        return keys
    }

    /** A small ring around the viewport at the neighbouring zoom levels. */
    fun neighbourKeys(
        centerLat: Double,
        centerLng: Double,
        zoom: Float,
        width: Float,
        height: Float,
    ): List<TileKey> {
        val z = floor(zoom).toInt().coerceIn(AmapTiles.MIN_ZOOM, AmapTiles.MAX_ZOOM)
        val worldTiles = 1 shl z
        val cx = floor(worldX(centerLng, z.toFloat())).toInt()
        val cy = floor(worldY(centerLat, z.toFloat())).toInt()
        val result = LinkedHashSet<TileKey>()
        listOf(z - 1, z + 1).forEach { level ->
            if (level !in AmapTiles.MIN_ZOOM..AmapTiles.MAX_ZOOM) return@forEach
            val factor = if (level < z) 0.5 else 2.0
            val nx = floor(cx * factor).toInt()
            val ny = floor(cy * factor).toInt()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val x = nx + dx
                    val y = ny + dy
                    if (y !in 0 until (1 shl level)) continue
                    result += TileKey(((x % (1 shl level)) + (1 shl level)) % (1 shl level), y, level)
                }
            }
        }
        val visible = visibleKeys(centerLat, centerLng, zoom, width, height)
        return result.filterNot { it in visible }.take(min(20, result.size))
    }
}
