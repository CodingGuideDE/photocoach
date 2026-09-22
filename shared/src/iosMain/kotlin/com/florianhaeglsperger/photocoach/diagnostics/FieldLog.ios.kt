package com.florianhaeglsperger.photocoach.diagnostics

/**
 * iOS: bewusst wirkungslos. Der Feldtest laeuft auf Android (Plan-zur-Umsetzung.md
 * Abschnitt 0), und ein Protokoll ohne Kamera protokolliert nichts.
 */
actual object FieldLog {
    actual fun append(line: String) = Unit
    actual fun location(): String? = null
}
