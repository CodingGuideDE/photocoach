package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Deckkraft der Linien — sichtbar auf hellen wie dunklen Motiven, ohne das Bild zu dominieren. */
private const val LINE_ALPHA = 0.55f

/** Deckkraft des Schattens darunter, damit die Linien auf hellem Grund nicht verschwinden. */
private const val SHADOW_ALPHA = 0.30f

/**
 * Drittel-Raster ueber dem Sucherbild (Plan 3.4).
 *
 * Zweck ist nicht Dekoration, sondern Nachvollziehbarkeit: Ohne sichtbare Linien kann man
 * einen Hinweis wie "Motiv 12 % links vom Drittel-Punkt" nicht beurteilen — man sieht das
 * Ziel nicht, auf das er sich bezieht. Fuer den Feldtest (Plan 3.5) ist das der Unterschied
 * zwischen "der Hinweis fuehlt sich falsch an" und "der Hinweis *ist* falsch".
 *
 * Bewusst duenne Linien mit Schatten statt kraeftiger Farbe: Das Raster liegt ueber dem
 * Motiv und soll die Beurteilung der Komposition nicht selbst stoeren.
 *
 * Die Schnittpunkte sind bewusst hervorgehoben — sie sind die eigentlichen Zielpunkte der
 * `RuleOfThirdsRule`, die Linien nur ihre Konstruktion.
 */
@Composable
fun ThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val thirds = listOf(1f / 3f, 2f / 3f)

        thirds.forEach { fraction ->
            drawGridLine(
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
            )
            drawGridLine(
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
            )
        }

        // Die vier Schnittpunkte
        thirds.forEach { x ->
            thirds.forEach { y ->
                val center = Offset(size.width * x, size.height * y)
                drawCircle(Color.Black.copy(alpha = SHADOW_ALPHA), radius = 5f, center = center)
                drawCircle(Color.White.copy(alpha = LINE_ALPHA), radius = 3.5f, center = center)
            }
        }
    }
}

/** Linie mit leichtem Schatten dahinter, damit sie auf jedem Untergrund lesbar bleibt. */
private fun DrawScope.drawGridLine(start: Offset, end: Offset) {
    drawLine(
        color = Color.Black.copy(alpha = SHADOW_ALPHA),
        start = start,
        end = end,
        strokeWidth = 3f,
    )
    drawLine(
        color = Color.White.copy(alpha = LINE_ALPHA),
        start = start,
        end = end,
        strokeWidth = 1.5f,
    )
}
