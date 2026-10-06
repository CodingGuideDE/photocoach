package com.florianhaeglsperger.photocoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Ein aufgenommenes Foto, plattformneutral referenziert.
 *
 * [id] ist ein undurchsichtiger Schluessel, den nur die [PhotoLibrary] der eigenen
 * Plattform deuten kann (Android: die `content://`-URI aus dem MediaStore). Die UI in
 * commonMain reicht ihn nur weiter — genau wie [CaptureResult.Success.location] nur
 * angezeigt und nicht interpretiert wird.
 */
data class CapturedPhoto(val id: String)

/**
 * Zugriff auf die Fotos, die die App selbst aufgenommen hat — fuer die Vorschau neben dem
 * Ausloeser und die Vollbild-Ansicht.
 *
 * **Nur die eigenen Aufnahmen**, nie die Fotobibliothek des Nutzers. Das haelt auch die
 * Berechtigungen klein (on-device-Versprechen, siehe CLAUDE.md): ab Android 10 darf eine App
 * ihre eigenen MediaStore-Eintraege ohne jede Leseberechtigung oeffnen.
 */
interface PhotoLibrary {

    /** Das zuletzt von der App gespeicherte Foto, oder `null`, wenn es keines (mehr) gibt. */
    suspend fun latest(): CapturedPhoto?

    /**
     * Alle Fotos der App, **neuestes zuerst** — die Reihenfolge, in der die Vollbild-Ansicht
     * nach links zu den aelteren blaettert. Leer, wenn es keine gibt.
     */
    suspend fun all(): List<CapturedPhoto>

    /**
     * Laedt das Foto aufrecht gedreht, die laengere Kante hoechstens [maxDimension] Pixel.
     *
     * Fuer die Vollbild-Ansicht wird mit grossem [maxDimension] praktisch die volle
     * Aufloesung geladen; die Grenze schuetzt nur davor, bei 50-MP-Sensoren ein Bitmap zu
     * bauen, das Android nicht mehr zeichnen kann. `null`, wenn das Foto nicht lesbar ist
     * (inzwischen in der Galerie geloescht, o. ae.).
     */
    suspend fun load(photo: CapturedPhoto, maxDimension: Int): ImageBitmap?

    /**
     * Loescht das Foto endgueltig — auch aus der Galerie des Nutzers, denn dort liegt es.
     *
     * @return `true`, wenn das Foto danach weg ist (auch wenn es schon vorher weg war);
     *  `false`, wenn das System das Loeschen verweigert hat. Die Rueckfrage an den Nutzer
     *  ist Sache der UI, hier wird ohne Nachfrage geloescht.
     */
    suspend fun delete(photo: CapturedPhoto): Boolean

    /**
     * Aufnahmedaten fuer die Info-Ansicht — aus dem, was in der Datei selbst steht (EXIF)
     * und was das System dazu weiss (Groesse, Ordner). `null`, wenn das Foto nicht lesbar ist.
     *
     * Was hier fehlt, weil es nicht in der Datei steht: Objektiv, Zoomstufe und der Score zum
     * Zeitpunkt der Aufnahme. Dafuer muessten die Aufnahme-Daten mitgespeichert werden
     * (Planung/Foto-Bearbeitung.md 4.3).
     */
    suspend fun details(photo: CapturedPhoto): PhotoDetails?

    /**
     * Oeffnet den Teilen-Dialog des Systems fuer das Foto. Geteilt wird die Datei so, wie sie
     * in der Galerie liegt — die App schickt selbst nichts irgendwohin.
     *
     * @return `false`, wenn kein Teilen-Dialog geoeffnet werden konnte.
     */
    fun share(photo: CapturedPhoto): Boolean
}

/** Die [PhotoLibrary] der laufenden Plattform. */
@Composable
expect fun rememberPhotoLibrary(): PhotoLibrary

/**
 * Systemweite Zurueck-Geste/-Taste abfangen, solange [enabled] ist.
 *
 * Als eigene Naht statt der Compose-Multiplatform-Variante (`ui-backhandler`), weil die eine
 * zusaetzliche Abhaengigkeit fuer den iOS-Build bedeutet haette — und die iOS-Seite ist
 * pausiert (CLAUDE.md). Android nutzt den `BackHandler` aus activity-compose, das ohnehin
 * schon eingebunden ist.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
