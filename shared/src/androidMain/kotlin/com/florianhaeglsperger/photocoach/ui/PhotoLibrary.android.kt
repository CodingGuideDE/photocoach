package com.florianhaeglsperger.photocoach.ui

import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dateinamens-Praefix aller Aufnahmen. Darueber findet [AndroidPhotoLibrary.latest] die
 * eigenen Fotos wieder — gesetzt in `saveToGallery` (CameraPreview.android.kt).
 */
internal const val PHOTO_NAME_PREFIX = "PhotoCoach_"

@Composable
actual fun rememberPhotoLibrary(): PhotoLibrary {
    val context = LocalContext.current
    // Zwei Contexts: lesen und schreiben ueber den langlebigen Application-Context, den
    // Teilen-Dialog aber aus der Activity heraus starten — sonst landet er in einem eigenen
    // Task und "Zurueck" fuehrt nicht in die App.
    return remember(context) { AndroidPhotoLibrary(context.applicationContext, uiContext = context) }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

/**
 * Liest die eigenen Aufnahmen aus dem MediaStore.
 *
 * **Berechtigungen:** keine zusaetzlichen. Ab API 29 sieht eine App ihre eigenen
 * MediaStore-Eintraege ohne Leseberechtigung — genau die braucht es hier. Auf API 26-28 gilt
 * die ohnehin erteilte (auf maxSdk 28 begrenzte) Speicher-Berechtigung. Fotos aus einer
 * frueheren Installation gehoeren der App nach einer Neuinstallation nicht mehr und tauchen
 * dann nicht auf — das ist gewollt, nicht ein Fehler.
 */
private class AndroidPhotoLibrary(
    private val context: Context,
    private val uiContext: Context,
) : PhotoLibrary {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun latest(): CapturedPhoto? = query(limit = 1).firstOrNull()

    override suspend fun all(): List<CapturedPhoto> = query(limit = Int.MAX_VALUE)

    /**
     * Die eigenen Fotos, neuestes zuerst.
     *
     * Woran "eigene" erkannt wird: am Dateinamen-Praefix — und ab API 29 zusaetzlich am
     * Album-Ordner `Pictures/PhotoCoach`. Ab API 29 liefert der MediaStore einer App ohne
     * Leseberechtigung ohnehin nur ihre eigenen Eintraege; auf API 26-28 (Speicher-
     * Berechtigung erteilt) saehe sie alles, dort grenzt das Praefix ein.
     *
     * Bei gleicher Sekunde in `DATE_ADDED` (Serienaufnahmen) entscheidet die ID — die
     * waechst mit jedem Eintrag, die Reihenfolge bleibt also stabil.
     */
    private suspend fun query(limit: Int): List<CapturedPhoto> = withContext(Dispatchers.IO) {
        val nameFilter = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "$nameFilter AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to
                arrayOf("$PHOTO_NAME_PREFIX%", "Pictures/$ALBUM_NAME%")
        } else {
            nameFilter to arrayOf("$PHOTO_NAME_PREFIX%")
        }
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                buildList {
                    while (size < limit && cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idColumn),
                        )
                        add(CapturedPhoto(uri.toString()))
                    }
                }
            }
        }.getOrNull() ?: emptyList()
    }

    override suspend fun load(photo: CapturedPhoto, maxDimension: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching { decode(Uri.parse(photo.id), maxDimension)?.asImageBitmap() }.getOrNull()
        }

    /**
     * Eigene Aufnahmen darf die App ab API 29 ohne Rueckfrage loeschen, auf API 26-28 gilt
     * die Speicher-Berechtigung. 0 geloeschte Zeilen heisst: der Eintrag war schon weg (z. B.
     * in der Galerie geloescht) — fuer den Aufrufer das gewuenschte Ergebnis. Verweigert das
     * System (`SecurityException`, etwa bei einem Foto, das der App nicht mehr gehoert),
     * bleibt das Foto bestehen und es kommt `false`.
     */
    override suspend fun delete(photo: CapturedPhoto): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.delete(Uri.parse(photo.id), null, null)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun details(photo: CapturedPhoto): PhotoDetails? = withContext(Dispatchers.IO) {
        runCatching { readDetails(Uri.parse(photo.id)) }.getOrNull()
    }

    /**
     * Systemwerte (Name, Groesse, Ordner, Aufnahmezeit) aus dem MediaStore, Kamerawerte aus
     * dem EXIF-Block der Datei. Beides ohne zusaetzliche Berechtigung — es sind die eigenen
     * Eintraege der App.
     */
    private fun readDetails(uri: Uri): PhotoDetails? {
        val projection = buildList {
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()

        var details = resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            fun long(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { cursor.getLong(it) }
            fun string(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { cursor.getString(it) }

            // DATE_TAKEN in ms; fehlt er, tut es der Zeitpunkt des Speicherns (in s) auch —
            // bei einer Kamera-App liegen beide Sekunden auseinander.
            val takenMs = long(MediaStore.Images.Media.DATE_TAKEN)?.takeIf { it > 0 }
                ?: long(MediaStore.Images.Media.DATE_ADDED)?.takeIf { it > 0 }?.times(1000)
            PhotoDetails(
                takenAt = takenMs?.let {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                },
                fileName = string(MediaStore.Images.Media.DISPLAY_NAME),
                album = string(MediaStore.Images.Media.RELATIVE_PATH)?.trimEnd('/'),
                widthPx = long(MediaStore.Images.Media.WIDTH)?.toInt()?.takeIf { it > 0 },
                heightPx = long(MediaStore.Images.Media.HEIGHT)?.toInt()?.takeIf { it > 0 },
                sizeBytes = long(MediaStore.Images.Media.SIZE),
            )
        } ?: return null

        // Der MediaStore kennt die Masse nicht immer (frisch gespeichert, Scanner noch nicht
        // durch) — dann aus dem Dateikopf lesen.
        if (details.widthPx == null || details.heightPx == null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            details = details.copy(
                widthPx = bounds.outWidth.takeIf { it > 0 },
                heightPx = bounds.outHeight.takeIf { it > 0 },
            )
        }

        return resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            fun double(tag: String) = exif.getAttribute(tag)?.let { exif.getAttributeDouble(tag, -1.0) }
                ?.takeIf { it > 0 }
            fun int(tag: String) = exif.getAttribute(tag)?.let { exif.getAttributeInt(tag, -1) }
                ?.takeIf { it >= 0 }

            // Breite/Hoehe sind die der gespeicherten Pixel. Liegt das Foto per EXIF-Tag
            // gedreht in der Datei, zeigt die Ansicht es hochkant — die Info soll dasselbe sagen.
            val quarterTurned = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in
                setOf(
                    ExifInterface.ORIENTATION_ROTATE_90,
                    ExifInterface.ORIENTATION_ROTATE_270,
                    ExifInterface.ORIENTATION_TRANSPOSE,
                    ExifInterface.ORIENTATION_TRANSVERSE,
                )
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() }
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }

            details.copy(
                widthPx = if (quarterTurned) details.heightPx else details.widthPx,
                heightPx = if (quarterTurned) details.widthPx else details.heightPx,
                iso = int(ExifInterface.TAG_ISO_SPEED_RATINGS)?.takeIf { it > 0 },
                exposureTimeSeconds = double(ExifInterface.TAG_EXPOSURE_TIME),
                fNumber = double(ExifInterface.TAG_F_NUMBER),
                focalLengthMm = double(ExifInterface.TAG_FOCAL_LENGTH),
                whiteBalanceManual = int(ExifInterface.TAG_WHITE_BALANCE)?.let { it == 1 },
                // Bit 0 des Flash-Tags: ausgeloest ja/nein. Die uebrigen Bits (Modus, Rueck-
                // meldung) interessieren hier nicht.
                flashFired = int(ExifInterface.TAG_FLASH)?.let { it and 1 == 1 },
                device = when {
                    make == null -> model
                    model == null -> make
                    // "Google" + "Pixel 8" → "Google Pixel 8", aber "Google" + "Google Pixel 8"
                    // nicht doppelt.
                    model.startsWith(make, ignoreCase = true) -> model
                    else -> "$make $model"
                },
            )
        } ?: details
    }

    /**
     * Teilen ueber den System-Dialog. Die empfangende App bekommt nur fuer dieses eine Foto
     * Lesezugriff (`FLAG_GRANT_READ_URI_PERMISSION`), und nur solange sie es braucht. Die
     * `ClipData` traegt dieselbe URI noch einmal: darueber zeigt der Dialog ab Android 10 eine
     * Vorschau, und die Freigabe reicht durch den Chooser hindurch bis zur Ziel-App.
     */
    override fun share(photo: CapturedPhoto): Boolean = runCatching {
        val uri = Uri.parse(photo.id)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Foto teilen").apply {
            if (uiContext !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        uiContext.startActivity(chooser)
    }.isSuccess

    private fun decode(uri: Uri, maxDimension: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder dreht nach dem EXIF-Orientierungs-Tag von selbst. Das ist wichtig:
            // CameraX schreibt die Drehung je nach Geraet nur als Tag in die Datei, statt
            // die Pixel zu drehen — ohne Auswertung laege jedes zweite Foto quer.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val scale = scaleFor(info.size.width, info.size.height, maxDimension)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt(),
                        (info.size.height * scale).roundToInt(),
                    )
                }
            }
        } else {
            decodeLegacy(uri, maxDimension)
        }

    /** API 26-27: BitmapFactory kennt EXIF nicht, die Drehung wird von Hand nachgeholt. */
    private fun decodeLegacy(uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // inSampleSize nur in Zweierpotenzen — die naechstkleinere, damit nie unter
        // maxDimension verkleinert wird.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleFor(width: Int, height: Int, maxDimension: Int): Float =
        (maxDimension.toFloat() / max(width, height)).coerceAtMost(1f)
}
