package com.viatrail.app

import android.content.Context
import com.viatrail.data.BuildConfig
import com.viatrail.domain.Point
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** Download a SMALL, bounded offline map region. No offline routing is implied. */
object OfflineRegionDownloader {
    fun download(context: Context, center: Point, onProgress: (String) -> Unit) {
        val key = BuildConfig.GEOAPIFY_KEY
        if (key.isBlank()) { onProgress("Укажи GEOAPIFY_KEY"); return }
        MapLibre.getInstance(context.applicationContext)
        val lat = center.lat.coerceIn(-84.0, 84.0)
        val lon = center.lon.coerceIn(-179.0, 179.0)
        // About 10 km around the selected point. Enforced size prevents huge tile downloads.
        val bounds = LatLngBounds.Builder().includes(listOf(
            LatLng(lat - 0.045, lon - 0.045), LatLng(lat + 0.045, lon + 0.045)
        )).build()
        val style = "https://maps.geoapify.com/v1/styles/osm-carto/style.json?apiKey=$key"
        val definition = OfflineTilePyramidRegionDefinition(style, bounds, 10.0, 14.0, 1f)
        val metadata = "ViaTrail small offline map ${lat},${lon}".toByteArray()
        onProgress("Начинаем загрузку карты…")
        OfflineManager.getInstance(context.applicationContext).createOfflineRegion(
            definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                onProgress("Небольшой участок карты сохранен на устройстве")
                            } else {
                                onProgress("Загружено ресурсов: ${status.completedResourceCount}")
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            onProgress("Ошибка загрузки: ${error.message}")
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        }
                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            onProgress("Превышен лимит плиток ($limit)")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
                override fun onError(error: String) { onProgress("Ошибка: $error") }
            }
        )
    }
}
