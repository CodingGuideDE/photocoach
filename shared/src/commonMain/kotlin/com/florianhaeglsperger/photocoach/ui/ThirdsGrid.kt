package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Deckkraft der hellen Linie. Hoch genug, um auf dunklem und mittlerem Grund klar zu stehen;
 * auf hellem Grund uebernimmt der Halo.
 */
private const val LINE_ALPHA = 0.85f

/**
 * Deckkraft des dunklen Halos. Er sitzt als schmaler Saum links/rechts der Linie und macht
 * sie auf Himmel, Schnee oder weisser Wand sichtbar — ohne als eigene schwarze Linie
 * aufzufallen.
 */
private const val HALO_ALPHA = 0.35f

// Alle Masse in dp, nicht in Pixeln: Pixelwerte schrumpfen auf hochaufloesenden Displays
// auf ein Drittel (1,5 px bei 3x-Dichte = 0,5 dp) — genau das machte das Raster zu duenn.
private val LINE_WIDTH = 1.dp
private val HALO_WIDTH = 2.5.dp
private val POINT_RADIUS = 4.dp
private val POINT_RING_WIDTH = 1.5.dp

/**
 * Drittel-Raster ueber dem Sucherbild (Plan 3.4).
 *
 * Zweck ist nicht Dekoration, sondern Nachvollziehbarkeit: Ohne sichtbare Linien kann man
 * einen Hinweis wie "Motiv 12 % links vom Drittel-Punkt" nicht beurteilen — man sieht das
 * Ziel nicht, auf das er sich bezieht. Fuer den Feldtest (Plan 3.5) ist das der Unterschied
 * zwischen "der Hinweis fuehlt sich falsch an" und "der Hinweis *ist* falsch".
 *
 * Duenne helle Linien mit dunklem Halo statt kraeftiger Farbe: Das Raster liegt ueber dem
 * Motiv und soll die Beurteilung der Komposition nicht selbst stoeren, muss aber auf jedem
 * Untergrund lesbar sein — der Zwei-Ton-Aufbau sorgt dafuer, dass immer eine der beiden
 * Farben gegen den Hintergrund kontrastiert.
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
                // Ring statt Vollkreis: markiert den Punkt, verdeckt aber nicht, was dort liegt.
                drawCircle(
                    color = Color.Black.copy(alpha = HALO_ALPHA),
                    radius = POINT_RADIUS.toPx(),
                    center = center,
                    style = Stroke(width = POINT_RING_WIDTH.toPx() + (HALO_WIDTH - LINE_WIDTH).toPx()),
                )
                drawCircle(
                    color = Color.White.copy(alpha = LINE_ALPHA),
                    radius = POINT_RADIUS.toPx(),
                    center = center,
                    style = Stroke(width = POINT_RING_WIDTH.toPx()),
                )
            }
        }
    }
}

/** Helle Linie auf dunklem Halo, damit sie auf jedem Untergrund lesbar bleibt. */
private fun DrawScope.drawGridLine(start: Offset, end: Offset) {
    drawLine(
        color = Color.Black.copy(alpha = HALO_ALPHA),
        start = start,
        end = end,
        strokeWidth = HALO_WIDTH.toPx(),
    )
    drawLine(
        color = Color.White.copy(alpha = LINE_ALPHA),
        start = start,
        end = end,
        strokeWidth = LINE_WIDTH.toPx(),
    )
}
