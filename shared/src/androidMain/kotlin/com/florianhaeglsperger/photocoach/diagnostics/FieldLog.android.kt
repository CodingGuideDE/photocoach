package com.florianhaeglsperger.photocoach.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android: schreibt in eine Textdatei im App-eigenen Verzeichnis.
 *
 * Datei statt Logcat, weil das Geraet beim Feldtest nicht am Rechner haengt und der
 * Logcat-Ringpuffer bei einer laengeren Sitzung ueberlaeuft. Abholen danach mit:
 *
 *     adb pull /sdcard/Android/data/com.florianhaeglsperger.photocoach/files/feldtest.log
 */
actual object FieldLog {

    private const val TAG = "PhotoCoachField"
    private const val FILE_NAME = "feldtest.log"

    @Volatile
    private var file: File? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    /**
     * Legt fest, wohin geschrieben wird. Aus [com.florianhaeglsperger.photocoach.ui.CameraPreview]
     * beim Start aufgerufen — frueher gibt es keinen Context.
     */
    fun attach(context: Context) {
        if (file != null) return
        file = runCatching {
            File(context.getExternalFilesDir(null), FILE_NAME).also { target ->
                // Pro App-Start eine Trennzeile: so bleiben mehrere Test-Sitzungen in
                // derselben Datei unterscheidbar, ohne dass etwas verloren geht.
                target.appendText(
                    "\n=== Sitzung ${SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date())} ===\n",
                )
            }
        }.getOrNull()
    }

    actual fun append(line: String) {
        val target = file ?: return
        // Ein fehlgeschlagenes Protokoll darf die App nicht stoeren — deshalb runCatching
        // und nicht etwa eine Exception nach oben.
        runCatching { target.appendText("${timeFormat.format(Date())}  $line\n") }
            .onFailure { Log.w(TAG, "Protokollzeile nicht geschrieben", it) }
    }

    actual fun location(): String? = file?.absolutePath
}
