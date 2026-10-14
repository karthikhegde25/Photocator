package com.Photocator.core

/** One geotag worth of data. Nullable numbers mean "no value". */
data class TagData(
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val speed: Double? = null,
    val placeName: String = "",
    val address: String = "",
    val plusCode: String = "",
    val timeMs: Long = System.currentTimeMillis(),
    val tempC: Double? = null,
    val condition: String = "Unknown",
    val humidity: Int? = null,
    val windKmh: Double? = null,
    val magneticUt: Double? = null,
    val headingDeg: Double? = null,
    val note: String = "",
)

enum class VolumeAction { SHUTTER, ZOOM, OFF }
enum class GridKind { OFF, THIRDS, GOLDEN }
enum class AspectKind(val ratioWH: Float?) { FULL(null), SQUARE(1f), R4_3(3f / 4f), R16_9(9f / 16f) }
enum class QualityKind { STANDARD, HIGH, ULTRA }
enum class FlashKind { OFF, ON, TORCH }
enum class ShootMode { PHOTO, NIGHT, VIDEO }

/** Everything that is persisted as settings. */
data class Prefs(
    val sdCard: Boolean = false,
    val volumeAction: VolumeAction = VolumeAction.SHUTTER,
    val shutterSound: Boolean = true,
    val touchCapture: Boolean = false,
    val autoSave: Boolean = false,
    val grid: GridKind = GridKind.OFF,
    val timerSec: Int = 0,
    val aspect: AspectKind = AspectKind.FULL,
    val quality: QualityKind = QualityKind.HIGH,
    val flash: FlashKind = FlashKind.OFF,
    val tagEnabled: Boolean = true,
    val mapStyleId: String = "sat",
    val showPlus: Boolean = true,
    val watermarkId: String? = null,
    val rememberedWatermarkId: String? = null,
    val tagWidth: Float = 1f,
    val tagStretch: Float = 1f,
    val tagX: Float = 0.5f,
    val tagY: Float = 1f,
    val tagRounded: Boolean = true,
    /** The map strip of the geotag sits on the right (watermark / QR code on the left). */
    val tagMapRight: Boolean = false,
    /** The top-right corner of the geotag shows a QR code of the location instead of a watermark. */
    val qrOn: Boolean = false,
    val qrFg: Int = 0xFF000000.toInt(),
    val qrBg: Int = 0xFFFFFFFF.toInt(),
    val sensorData: Boolean = false,
    val mapZoom: Int = 16,
) {
    fun qr(): QrStyle? = if (qrOn) QrStyle(qrFg, qrBg) else null

    /** True when the geotag size/position are all factory values (for the Settings row). */
    val tagIsDefault: Boolean
        get() = tagWidth >= 0.9999f && tagStretch == 1f && tagX == 0.5f && tagY == 1f && tagRounded && !tagMapRight && !sensorData
}

/** Colours of the location QR code (ARGB). */
data class QrStyle(val fg: Int, val bg: Int)

data class Watermark(val id: String, val name: String, val file: String)

/** Where the geotag is placed on a photo. */
enum class TagPlacement { ON_PHOTO, BELOW_PHOTO }

/** Orientation of the phone: +1 top points right, -1 top points left, 0 portrait. */
typealias Tilt = Int
