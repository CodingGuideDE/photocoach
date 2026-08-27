package com.florianhaeglsperger.photocoach.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Liefert die seitliche Neigung des Geraets in Grad, aus dem Schwerkraft-Vektor.
 *
 * **Was das misst und was nicht:** erkannt wird, ob das *Geraet* schief gehalten wird.
 * NICHT erkannt wird der Fall "Geraet gerade, aber der Horizont im Bild ist schief"
 * (z.B. eine abschuessige Landschaft) — dafuer braeuchte es Bildanalyse. Das ist die in
 * Planung/Plan-zur-Umsetzung.md 3.3 bewusst akzeptierte Einschraenkung fuer v1; iOS bekommt
 * ueber `VNDetectHorizonRequest` spaeter beides.
 *
 * Vorzeichen: positiv = Geraet nach rechts geneigt (rechte Kante tiefer), negativ = links.
 * 0 = aufrecht.
 */
internal class HorizonSensor(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // TYPE_GRAVITY ist ein fusionierter Sensor (schon entrauscht) und deshalb die bessere
    // Wahl — er ist aber nicht auf jedem Geraet vorhanden, dann faellt es auf den rohen
    // Beschleunigungssensor zurueck, den es garantiert gibt.
    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var tiltDegrees: Float? = null

    /**
     * Bildschirm-Rotation, gegen die normalisiert wird.
     *
     * Ohne das wuerde ein bewusst quer gehaltenes Geraet konstant 90 Grad Neigung melden,
     * obwohl es fuer den Nutzer gerade ist. Wird von aussen gesetzt, weil nur der
     * UI-Layer die aktuelle Display-Rotation kennt.
     */
    @Volatile
    var displayRotation: Int = Surface.ROTATION_0

    /** Aktuelle Neigung, oder null solange noch kein Sensorwert da ist. */
    fun currentTiltDegrees(): Float? = tiltDegrees

    fun start() {
        val manager = sensorManager ?: return
        val target = sensor ?: return
        // SENSOR_DELAY_UI (~60 ms) reicht: die Analyse laeuft ohnehin nur mit ~10 Hz, und
        // ein schnellerer Sensor wuerde nur Strom kosten.
        manager.registerListener(this, target, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        tiltDegrees = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        tiltDegrees = tiltFromGravity(
            gravityX = event.values[0],
            gravityY = event.values[1],
            displayRotation = displayRotation,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Rechnet den Schwerkraft-Vektor in eine seitliche Neigung in Grad um.
 *
 * Bewusst als freie, pure Funktion aus [HorizonSensor] herausgezogen: das ist der einzige
 * Teil mit echter Mathematik (Vorzeichen, Rotations-Normalisierung, Grenzfaelle) und damit
 * der einzige, der falsch sein kann — so laesst er sich ohne Geraet testen, siehe
 * `HorizonSensorTest`.
 *
 * @return Neigung in Grad, positiv = nach rechts geneigt. `null`, wenn das Geraet zu flach
 *  liegt, um eine seitliche Neigung sinnvoll zu bestimmen.
 */
internal fun tiltFromGravity(
    gravityX: Float,
    gravityY: Float,
    displayRotation: Int,
): Float? {
    // Zeigt das Geraet fast senkrecht nach oben oder unten (flach auf dem Tisch), ist die
    // seitliche Neigung mathematisch instabil — atan2 auf zwei fast-Null-Werten springt
    // wild. In dem Fall lieber gar keinen Wert melden als einen falschen.
    if (hypot(gravityX, gravityY) < MIN_HORIZONTAL_GRAVITY) return null

    val rawDegrees = Math.toDegrees(atan2(gravityX.toDouble(), gravityY.toDouble())).toFloat()
    return normalizeDegrees(rawDegrees - displayRotation.toOffsetDegrees())
}

/**
 * Untergrenze fuer den waagerechten Anteil der Schwerkraft (m/s^2), ab der die Neigung
 * sinnvoll bestimmbar ist. ~2 entspricht gut 10 Grad Aufrichtung.
 */
private const val MIN_HORIZONTAL_GRAVITY = 2.0f

private fun Int.toOffsetDegrees(): Float = when (this) {
    Surface.ROTATION_90 -> -90f
    Surface.ROTATION_180 -> 180f
    Surface.ROTATION_270 -> 90f
    else -> 0f
}

/** Faltet einen Winkel auf den Bereich -180..180 zurueck. */
private fun normalizeDegrees(degrees: Float): Float {
    var value = degrees
    while (value > 180f) value -= 360f
    while (value < -180f) value += 360f
    // Auf eine Nachkommastelle runden: der Sensor rauscht ohnehin staerker, und ein
    // zappelnder Wert wuerde die UI unnoetig neu zeichnen lassen.
    return (value * 10f).roundToInt() / 10f
}
