package com.florianhaeglsperger.photocoach.diagnostics

/**
 * Schreibt ein Protokoll fuer den Feldtest (Plan 3.5).
 *
 * **Warum das existiert:** Beim Test draussen entstehen 50-100 Fotos. Welcher Hinweis bei
 * welchem Foto anlag, laesst sich hinterher aus dem Gedaechtnis nicht rekonstruieren — und
 * genau das ist die Information, um die es bei dem Test geht. Das Protokoll haelt jede
 * Hinweis-Aenderung mit Zeitstempel fest; ueber den Zeitstempel laesst es sich mit den
 * Fotos in der Galerie zusammenbringen.
 *
 * **Temporaer.** Faellt weg, sobald der Feldtest ausgewertet ist. Kein Teil des Produkts.
 */
expect object FieldLog {

    /** Haengt eine Zeile an das Protokoll an. Darf nie werfen — ein Protokoll ist kein Feature. */
    fun append(line: String)

    /** Pfad des Protokolls zur Anzeige, oder `null` wenn noch keines angelegt wurde. */
    fun location(): String?
}
