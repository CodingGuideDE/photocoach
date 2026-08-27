package com.florianhaeglsperger.photocoach.capture

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.COpaquePointer
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferIOSurfacePropertiesKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVReturnSuccess

/**
 * Erzeugt Testbilder als [CVPixelBufferRef] — dasselbe Format, das spaeter
 * `AVCaptureVideoDataOutput` liefert.
 *
 * Das ist der Grund, warum sich der iOS-`FrameAnalyzer` ohne Kamera und ohne iPhone
 * verifizieren laesst: Vision arbeitet auf Bildern, nicht auf einer Kamera. Woher der
 * Buffer kommt, ist ihm egal.
 */
@OptIn(ExperimentalForeignApi::class)
fun createTestFrame(
    width: Int = 640,
    height: Int = 480,
    draw: (CGContextRef?) -> Unit,
): CameraFrame = CameraFrame(createPixelBuffer(width, height, draw), timestampMs = 0L)

@OptIn(ExperimentalForeignApi::class)
private fun createPixelBuffer(
    width: Int,
    height: Int,
    draw: (CGContextRef?) -> Unit,
): CVPixelBufferRef = memScoped {
    val out = alloc<CVPixelBufferRefVar>()
    val status = CVPixelBufferCreate(
        allocator = null,
        width = width.convert(),
        height = height.convert(),
        pixelFormatType = kCVPixelFormatType_32BGRA,
        pixelBufferAttributes = ioSurfaceAttributes(),
        pixelBufferOut = out.ptr,
    )
    check(status == kCVReturnSuccess) { "CVPixelBufferCreate fehlgeschlagen: $status" }
    val buffer = requireNonNull(out.value)

    CVPixelBufferLockBaseAddress(buffer, 0uL)
    try {
        // Kontext direkt ueber den Speicher des Pixel-Buffers legen — dann landet alles
        // Gezeichnete ohne Kopie im Buffer.
        val context = CGBitmapContextCreate(
            data = CVPixelBufferGetBaseAddress(buffer),
            width = width.convert(),
            height = height.convert(),
            bitsPerComponent = 8u,
            bytesPerRow = CVPixelBufferGetBytesPerRow(buffer),
            space = CGColorSpaceCreateDeviceRGB(),
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or
                kCGBitmapByteOrder32Little,
        )
        // Ohne diese Pruefung schlaegt ein Format-Fehler still fehl: CGContextSetRGBFillColor
        // auf einem null-Kontext tut einfach nichts, und der Buffer bliebe uninitialisiert.
        checkNotNull(context) { "CGBitmapContextCreate lieferte null — Format passt nicht" }
        draw(context)
    } finally {
        CVPixelBufferUnlockBaseAddress(buffer, 0uL)
    }
    buffer
}

private fun <T : Any> requireNonNull(value: T?): T =
    requireNotNull(value) { "CVPixelBufferCreate lieferte keinen Buffer" }

/** Fuellt die gesamte Flaeche mit einer Farbe. */
@OptIn(ExperimentalForeignApi::class)
fun CGContextRef?.fill(width: Double, height: Double, gray: Double) {
    CGContextSetRGBFillColor(this, gray, gray, gray, 1.0)
    CGContextFillRect(this, CGRectMake(0.0, 0.0, width, height))
}

/** Zeichnet ein helles Rechteck — dient als "Motiv" fuer die Saliency-Erkennung. */
@OptIn(ExperimentalForeignApi::class)
fun CGContextRef?.drawSubject(rect: CValue<CGRect>, gray: Double = 1.0) {
    CGContextSetRGBFillColor(this, gray, gray, gray, 1.0)
    CGContextFillRect(this, rect)
}

/**
 * Attribute, die den Pixel-Buffer mit einem IOSurface hinterlegen.
 *
 * **Ohne das liest Vision den Buffer nicht** — es meldet keinen Fehler, sondern liefert
 * stumm fuer jedes Bild dasselbe Ergebnis. Genau darauf ist dieser Test-Helfer beim
 * Schreiben hereingefallen: die Buffer unterschieden sich nachweislich, die Saliency nicht.
 *
 * In der Produktion faellt das nicht auf, weil `AVCaptureVideoDataOutput` seine Buffer
 * ohnehin IOSurface-gestuetzt liefert. Nur handgebaute Buffer brauchen die Angabe.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ioSurfaceAttributes() = CFDictionaryCreateMutable(
    allocator = kCFAllocatorDefault,
    capacity = 1,
    keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
    valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
).also { attributes ->
    // Leeres Unter-Dictionary = "IOSurface mit Standardeinstellungen".
    val empty = CFDictionaryCreateMutable(
        allocator = kCFAllocatorDefault,
        capacity = 0,
        keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
        valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
    )
    CFDictionarySetValue(
        theDict = attributes,
        key = kCVPixelBufferIOSurfacePropertiesKey as COpaquePointer?,
        value = empty as COpaquePointer?,
    )
}
