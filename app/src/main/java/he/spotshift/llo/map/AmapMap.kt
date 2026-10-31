package he.spotshift.llo.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import he.spotshift.llo.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import he.spotshift.llo.data.CoordConverter
import he.spotshift.llo.data.CoordSystem
import he.spotshift.llo.data.LatLng
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

private data class Camera(val center: LatLng, val zoom: Float)

/**
 * Lightweight Gaode raster map. Tiles come from a warm memory/disk cache, parents
 * are stretched while children load, and neighbouring zooms are prefetched once
 * the gesture settles — so zooming does not restart from blank tiles.
 */
@Composable
fun AmapMap(
    modifier: Modifier = Modifier,
    center: LatLng,
    zoom: Float = 15f,
    marker: LatLng? = center,
    interactive: Boolean = true,
    resetKey: Any? = center,
    onTap: ((LatLng) -> Unit)? = null,
) {
    val context = LocalContext.current
    val tiles = remember(context) { TileSource.get(context) }
    // Gaode raster tiles are GCJ-02; the app speaks WGS-84 everywhere else.
    val mapCenter = remember(center) { CoordConverter.convert(center, CoordSystem.WGS84, CoordSystem.GCJ02) }
    val mapMarker = remember(marker) {
        marker?.let { CoordConverter.convert(it, CoordSystem.WGS84, CoordSystem.GCJ02) }
    }
    // resetKey deliberately ignores centre changes while it stays stable, so tapping
    // the map in the editor moves the pin without snapping the camera back.
    var camera by remember(resetKey) { mutableStateOf(Camera(mapCenter, zoom)) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentTap by rememberUpdatedState(onTap)
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val markerShadow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    LaunchedEffect(camera, viewport) {
        if (viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        val width = viewport.width.toFloat()
        val height = viewport.height.toFloat()
        val keys = Mercator.visibleKeys(camera.center.lat, camera.center.lng, camera.zoom, width, height)
        tiles.ensure(keys)
        delay(350)
        tiles.prefetch(
            Mercator.neighbourKeys(camera.center.lat, camera.center.lng, camera.zoom, width, height)
        )
        // A short network hiccup must not leave a permanent hole until the user drags.
        for (wait in longArrayOf(1_200L, 2_500L, 4_000L, 8_000L, 15_000L)) {
            delay(wait)
            val missing = keys.filter { tiles.get(it) == null }
            if (missing.isEmpty()) return@LaunchedEffect
            tiles.retry(missing)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(placeholder)
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentTap?.let { callback ->
                        val gcj = screenToLatLng(camera.center, camera.zoom, offset, size.toSize())
                        callback(CoordConverter.convert(gcj, CoordSystem.GCJ02, CoordSystem.WGS84))
                    }
                }
            }
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    camera = camera.transformed(pan, gestureZoom, centroid, size.toSize())
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reading this snapshot state inside the draw phase redraws as tiles land
            // without recomposing the whole map.
            @Suppress("UNUSED_VARIABLE")
            val revision = tiles.revision.intValue
            drawTiles(camera, viewport, tiles, placeholder)
            mapMarker?.let { drawMarker(it, camera, markerColor, markerShadow) }
        }

        if (interactive) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            ) {
                MapButton(
                    enabled = camera.zoom < AmapTiles.MAX_ZOOM,
                    onClick = {
                        camera = camera.copy(
                            zoom = (camera.zoom + 1f).coerceAtMost(AmapTiles.MAX_ZOOM.toFloat())
                        )
                    },
                ) { Icon(Icons.Filled.Add, contentDescription = "放大") }
                Spacer(modifier = Modifier.height(8.dp))
                MapButton(
                    enabled = camera.zoom > AmapTiles.MIN_ZOOM,
                    onClick = {
                        camera = camera.copy(
                            zoom = (camera.zoom - 1f).coerceAtLeast(AmapTiles.MIN_ZOOM.toFloat())
                        )
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_minus),
                        contentDescription = "缩小",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Text(
            text = "© 高德地图",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MapButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private fun DrawScope.drawTiles(
    camera: Camera,
    viewport: IntSize,
    tiles: TileSource,
    placeholder: Color,
) {
    if (viewport.width == 0 || viewport.height == 0) return
    val z = floor(camera.zoom).toInt().coerceIn(AmapTiles.MIN_ZOOM, AmapTiles.MAX_ZOOM)
    val scale = 2.0.pow((camera.zoom - z).toDouble()).toFloat()
    val tilePx = AmapTiles.TILE_SIZE * scale
    val worldTiles = 1 shl z
    val centerX = Mercator.worldX(camera.center.lng, z.toFloat())
    val centerY = Mercator.worldY(camera.center.lat, z.toFloat())
    val halfW = size.width / (2 * tilePx)
    val halfH = size.height / (2 * tilePx)

    val minX = floor(centerX - halfW).toInt() - 1
    val maxX = ceil(centerX + halfW).toInt() + 1
    val minY = floor(centerY - halfH).toInt() - 1
    val maxY = ceil(centerY + halfH).toInt() + 1

    for (ty in minY..maxY) {
        if (ty !in 0 until worldTiles) continue
        for (tx in minX..maxX) {
            val left = ((tx - centerX) * tilePx + size.width / 2).toFloat()
            val top = ((ty - centerY) * tilePx + size.height / 2).toFloat()
            if (left > size.width || top > size.height || left + tilePx < 0 || top + tilePx < 0) continue

            val wrapped = ((tx % worldTiles) + worldTiles) % worldTiles
            val key = TileKey(wrapped, ty, z)
            val leftI = floor(left).toInt()
            val topI = floor(top).toInt()
            val dstW = ceil(tilePx).toInt() + 1
            val dstH = ceil(tilePx).toInt() + 1

            val image = tiles.get(key)
            if (image != null) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(leftI, topI),
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = FilterQuality.Low,
                )
                continue
            }

            drawRect(
                color = placeholder,
                topLeft = Offset(leftI.toFloat(), topI.toFloat()),
                size = Size(dstW.toFloat(), dstH.toFloat()),
            )
            drawParentSlice(key, tiles, leftI, topI, dstW, dstH)
        }
    }
}

/** Stretch the already cached parent tile into the empty slot to avoid white flashes. */
private fun DrawScope.drawParentSlice(
    key: TileKey,
    tiles: TileSource,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
) {
    val parentKey = key.parent ?: return
    val parent = tiles.get(parentKey) ?: return
    val halfW = parent.width / 2
    val halfH = parent.height / 2
    val srcX = (key.x % 2) * halfW
    val srcY = (key.y % 2) * halfH
    drawImage(
        image = parent,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(halfW, halfH),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.drawMarker(
    marker: LatLng,
    camera: Camera,
    color: Color,
    shadow: Color,
) {
    val wx = Mercator.worldX(marker.lng, camera.zoom)
    val wy = Mercator.worldY(marker.lat, camera.zoom)
    val cx = Mercator.worldX(camera.center.lng, camera.zoom)
    val cy = Mercator.worldY(camera.center.lat, camera.zoom)
    val x = ((wx - cx) * AmapTiles.TILE_SIZE + size.width / 2).toFloat()
    val y = ((wy - cy) * AmapTiles.TILE_SIZE + size.height / 2).toFloat()
    if (x < -40 || y < -40 || x > size.width + 40 || y > size.height + 40) return

    val center = Offset(x, y)
    val halo = 18.dp.toPx()
    drawCircle(color = color.copy(alpha = 0.14f), radius = halo, center = center)
    drawCircle(color = shadow, radius = 9.dp.toPx(), center = center + Offset(0f, 1.5.dp.toPx()))
    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = center)
    drawCircle(color = color, radius = 5.5.dp.toPx(), center = center)
}

private fun Camera.transformed(
    pan: Offset,
    gestureZoom: Float,
    centroid: Offset,
    viewport: Size,
): Camera {
    if (viewport.width <= 0f || viewport.height <= 0f) return this
    val nextZoom = (zoom + log2(gestureZoom.coerceAtLeast(0.01f)))
        .coerceIn(AmapTiles.MIN_ZOOM.toFloat(), AmapTiles.MAX_ZOOM.toFloat())
    val pannedX = Mercator.worldX(center.lng, zoom) - pan.x / AmapTiles.TILE_SIZE
    val pannedY = Mercator.worldY(center.lat, zoom) - pan.y / AmapTiles.TILE_SIZE
    val focusX = centroid.x - viewport.width / 2
    val focusY = centroid.y - viewport.height / 2
    // Keep the geographic point under the pinch centroid pinned to that spot.
    val focusWorldX = (pannedX + focusX / AmapTiles.TILE_SIZE) * 2.0.pow((nextZoom - zoom).toDouble())
    val focusWorldY = (pannedY + focusY / AmapTiles.TILE_SIZE) * 2.0.pow((nextZoom - zoom).toDouble())
    val movedX = focusWorldX - focusX / AmapTiles.TILE_SIZE
    val movedY = focusWorldY - focusY / AmapTiles.TILE_SIZE
    return Camera(
        center = LatLng(
            lat = Mercator.latOf(movedY, nextZoom),
            lng = Mercator.lngOf(movedX, nextZoom),
        ),
        zoom = nextZoom,
    )
}

private fun screenToLatLng(center: LatLng, zoom: Float, offset: Offset, viewport: Size): LatLng {
    val centerX = Mercator.worldX(center.lng, zoom)
    val centerY = Mercator.worldY(center.lat, zoom)
    val x = centerX + (offset.x - viewport.width / 2) / AmapTiles.TILE_SIZE
    val y = centerY + (offset.y - viewport.height / 2) / AmapTiles.TILE_SIZE
    return LatLng(Mercator.latOf(y, zoom), Mercator.lngOf(x, zoom))
}
