package com.florianhaeglsperger.photocoach.capture

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import java.io.Closeable
import java.nio.ByteOrder

/** Dateiname des Modells in den Assets (kommt aus models/, siehe scripts/download-models.sh). */
private const val MODEL_ASSET = "interactive_segmentation.task"

/** Ergebnis einer Erkennung: das Objekt (oder `null` = nichts unter dem Punkt) plus Dauer. */
internal class SegmentResult(val obj: SegmentedObject?, val inferenceMs: Long)

/**
 * Tipp-Segmentierung mit MediaPipe (MagicTouch-Modell, Apache 2.0): aus einem Bild und einem
 * Punkt die Flaeche des Objekts unter dem Punkt.
 *
 * Das Modell besteht aus zwei Teilen (siehe `.task`-Inhalt): ein grosser **Encoder** (28 MB),
 * der in [InteractiveSegmenter.setImage] das Bild verarbeitet — der teure Schritt, laut
 * Google ~200 ms auf einem Pixel 10 —, und ein kleiner **Decoder** fuer den Punkt in
 * [InteractiveSegmenter.segment]. Jedes neue Kamerabild kostet also den vollen Encoder.
 *
 * **Laden erst bei Bedarf:** 30 MB Modell sollen nicht jeden App-Start bremsen, sondern erst
 * den ersten Tipp. Nicht thread-sicher — nur von einem Thread aus benutzen.
 */
internal class ObjectSegmenter(private val context: Context) : Closeable {

    private var segmenter: InteractiveSegmenter? = null

    /** Warum das Laden gescheitert ist, oder `null`. */
    var unavailableReason: String? = null
        private set

    private fun ensureLoaded(): InteractiveSegmenter? {
        segmenter?.let { return it }
        if (unavailableReason != null) return null
        return try {
            InteractiveSegmenter.createFromOptions(
                context,
                InteractiveSegmenterOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            // CPU: laut Google auf dem Pixel 10 deutlich schneller (208 ms) als
                            // die GPU (580 ms) — und auf jedem Geraet verfuegbar.
                            .setDelegate(Delegate.CPU)
                            .build(),
                    )
                    .build(),
            ).also { segmenter = it }
        } catch (e: Exception) {
            // Typisch: Modell nicht im APK, weil models/ beim Bauen leer war.
            unavailableReason = if (e.message.orEmpty().contains(MODEL_ASSET)) {
                "Modell fehlt (scripts/download-models.sh ausführen)"
            } else {
                "Segmentierung nicht verfügbar: ${e.message ?: e::class.simpleName}"
            }
            null
        }
    }

    /**
     * Segmentiert [bitmap] (aufrecht, so wie in der Vorschau) am Punkt [x], [y] (normiert).
     * `null`, wenn das Modell nicht geladen werden kann — dann steht der Grund in
     * [unavailableReason].
     */
    fun segment(bitmap: Bitmap, x: Float, y: Float): SegmentResult? {
        val model = ensureLoaded() ?: return null
        val start = System.nanoTime()
        val image = BitmapImageBuilder(bitmap).build()
        model.setImage(image)
        val mask = model.segment(
            listOf(
                Stroke.builder()
                    .setBrushMode(Stroke.BrushMode.POSITIVE)
                    .setPoints(listOf(NormalizedKeypoint.create(x, y)))
                    .setCompleted(true)
                    .build(),
            ),
        )
        val values = mask.toFloats()
        val obj = regionAt(values, mask.width, mask.height, x, y)
        mask.close()
        image.close()
        return SegmentResult(obj, (System.nanoTime() - start) / 1_000_000)
    }

    override fun close() {
        segmenter?.close()
        segmenter = null
    }
}

/**
 * Die Maske als Konfidenzwerte 0..1. MediaPipe liefert sie als 32-Bit-Float (VEC32F1);
 * falls eine Version stattdessen 8-Bit-Werte liefert (ALPHA), werden die umgerechnet.
 */
private fun MPImage.toFloats(): FloatArray {
    val format = containedImageProperties.firstOrNull()?.imageFormat
    val pixels = width * height
    return if (format == MPImage.IMAGE_FORMAT_ALPHA) {
        val buffer = ByteBufferExtractor.extract(this, MPImage.IMAGE_FORMAT_ALPHA)
        FloatArray(pixels) { (buffer.get(it).toInt() and 0xFF) / 255f }
    } else {
        val buffer = ByteBufferExtractor.extract(this, MPImage.IMAGE_FORMAT_VEC32F1)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        FloatArray(pixels).also { buffer.get(it) }
    }
}
