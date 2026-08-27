package com.florianhaeglsperger.photocoach.capture

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNDetectHorizonRequest
import platform.Vision.VNGenerateAttentionBasedSaliencyImageRequest
import platform.Vision.VNImageRequestHandler
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Klaert eine Frage, an der die iOS-Planung haengt: **laeuft das Vision-Framework im
 * Simulator?**
 *
 * Hintergrund: `AVCaptureSession` braucht echte Kamera-Hardware und ist im Simulator nicht
 * testbar. Vision dagegen arbeitet auf *Bildern*, nicht auf einer Kamera — wenn es im
 * Simulator laeuft, laesst sich der komplette `FrameAnalyzer` dort entwickeln und
 * verifizieren, indem man ihm Standbilder statt Kamera-Frames vorlegt.
 *
 * **Was dieser Test zeigt — und was nicht.** Er belegt, dass die drei Requests aus Plan 3.1
 * im Simulator *ausfuehrbar* sind: die Frameworks laden, `performRequests` meldet Erfolg,
 * Beobachtungen kommen zurueck. Damit laesst sich die gesamte Verarbeitungskette dort
 * entwickeln und pruefen.
 *
 * Er belegt ausdruecklich **nicht**, dass die Ergebnisse inhaltlich stimmen. Nachgemessen
 * am 27.08.2026: die Saliency-Modelle liefern im Simulator eine konstante Heatmap, die den
 * Bildinhalt ignoriert (Details in `FrameAnalyzerTest` und shared/src/iosMain/README.md).
 * "Laeuft durch" und "rechnet richtig" sind hier zwei verschiedene Dinge — inhaltliche
 * Korrektheit braucht ein echtes Geraet.
 */
@OptIn(ExperimentalForeignApi::class)
class VisionAvailabilityTest {

    @Test
    fun `Gesichtserkennung laeuft im Simulator`() {
        val observations = runRequest(VNDetectFaceRectanglesRequest())
        // Auf einem einfarbigen Bild ist kein Gesicht — entscheidend ist, dass der Request
        // ohne Fehler durchlaeuft, nicht was er findet.
        assertNotNull(observations, "VNDetectFaceRectanglesRequest lieferte keine Ergebnisliste")
    }

    @Test
    fun `Saliency laeuft im Simulator`() {
        val observations = runRequest(VNGenerateAttentionBasedSaliencyImageRequest())
        assertNotNull(observations, "Saliency-Request lieferte keine Ergebnisliste")
        assertTrue(
            observations.isNotEmpty(),
            "Saliency sollte immer mindestens eine Beobachtung (die Heatmap) liefern",
        )
    }

    @Test
    fun `Horizont-Erkennung laeuft im Simulator`() {
        // Findet auf einem einfarbigen Bild keinen Horizont — dann ist die Liste leer,
        // aber der Request darf nicht scheitern.
        assertNotNull(runRequest(VNDetectHorizonRequest()), "Horizont-Request scheiterte")
    }

    /**
     * Fuehrt einen Vision-Request auf einem synthetischen Bild aus.
     *
     * @return die Beobachtungen, oder `null` wenn Vision einen Fehler gemeldet hat.
     */
    private fun runRequest(request: platform.Vision.VNImageBasedRequest): List<*>? {
        val image = createTestImage() ?: return null
        val handler = VNImageRequestHandler(cGImage = image, options = emptyMap<Any?, Any?>())

        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val success = handler.performRequests(listOf(request), error.ptr)
            if (!success) {
                println("Vision-Fehler: ${error.value?.localizedDescription}")
                null
            } else {
                request.results()
            }
        }
    }

    /** Einfarbiges 640x480-Bild — reicht, um zu pruefen ob die Requests ausfuehrbar sind. */
    private fun createTestImage(): CGImageRef? {
        val context = CGBitmapContextCreate(
            data = null,
            width = 640u,
            height = 480u,
            bitsPerComponent = 8u,
            bytesPerRow = 0u,
            space = CGColorSpaceCreateDeviceRGB(),
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        CGContextSetRGBFillColor(context, 0.4, 0.6, 0.8, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, 640.0, 480.0))
        return CGBitmapContextCreateImage(context)
    }
}
