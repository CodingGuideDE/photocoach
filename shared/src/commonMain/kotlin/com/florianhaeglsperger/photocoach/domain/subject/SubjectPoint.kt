package com.florianhaeglsperger.photocoach.domain.subject

/**
 * Geschaetzter Mittelpunkt des Hauptmotivs, normiert auf 0..1 (Plan 3.2.1, Schritt 2).
 *
 * Diese Zwischenschicht existiert, damit die Regeln in `domain/rules/` nicht direkt auf
 * `saliencyRegions` rechnen muessen. Zwei Gruende:
 *
 *  - **Verfuegbarkeit.** Saliency ist derzeit nur auf iOS echt; auf Android ist das Feld
 *    leer, bis die TFLite-Modell-Entscheidung faellt (Plan 11). Eine Regel, die allein auf
 *    Saliency rechnet, waere auf Android wirkungslos.
 *  - **Bedeutung.** Apples Attention-Saliency ("wohin schaut ein Mensch zuerst") und ein
 *    Segmentierungsmodell ("wo ist das Vordergrund-Objekt") sind verschiedene Groessen.
 *    Wuerden beide ungefiltert dasselbe Feld fuellen, gaeben die Regeln bei gleichem Bild
 *    auf beiden Plattformen unterschiedliche Hinweise. Hier werden sie auf *eine*
 *    definierte Bedeutung gebracht: geschaetzter Mittelpunkt des Hauptmotivs.
 */
data class SubjectPoint(
    val x: Float,
    val y: Float,
    /**
     * Wie verlaesslich der Punkt ist (0..1).
     *
     * Traegt die Abstufung, die [SubjectResolver] als Ja/Nein-Entscheidung nicht abbilden
     * kann: ein erkanntes Gesicht ist ein sicherer Anker, ein schwach ausgepraegter
     * Saliency-Klumpen ein wackliger. Der kuenftige `CompositionScorer` kann eine Regel
     * damit schwaecher gewichten, statt sie gleich ganz verstummen zu lassen.
     */
    val confidence: Float,
    val source: SubjectSource,
)

/** Woraus der Motivpunkt abgeleitet wurde — fuer Debug-Anzeige und spaetere Gewichtung. */
enum class SubjectSource {
    /** Aus einem erkannten Gesicht. Der verlaesslichere Fall. */
    FACE,

    /** Aus einem Klumpen von Saliency-Punkten. */
    SALIENCY,
}
