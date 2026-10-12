package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MUTED = Color.White.copy(alpha = 0.55f)

/** Mindesthoehe des Inhalts, solange noch geladen wird — damit das Panel nicht erst winzig ist. */
private val LOADING_HEIGHT = 168.dp

/**
 * Aufnahmedaten des angezeigten Fotos — als [ViewerSheet] wie die Loeschen-Rueckfrage: das
 * Foto wird angehoben und steht ganz sichtbar daneben, statt dass ein Kasten darueber liegt.
 *
 * **Gliederung statt Liste.** Frueher elf gleichfoermige Zeilen; jetzt nach dem, was man
 * sucht:
 *  1. *Wann* — Datum als Ueberschrift, darunter Uhrzeit und Kamera.
 *  2. *Wie belichtet* — Blende, Belichtung, ISO, Brennweite als Kacheln nebeneinander, wie in
 *     Kamera-Apps ueblich: auf einen Blick erfassbar.
 *  3. *Was fuer eine Datei* — Aufloesung, Groesse, Weissabgleich, Blitz, zweispaltig.
 *  4. Der Dateiname zuletzt, klein und gedaempft — fuer Fotografen das Unwichtigste.
 *
 * **Blaettern bleibt moeglich:** ein seitlicher Wisch auf dem Foto wechselt das Foto, das
 * Panel bleibt offen und zeigt die Daten des neuen ([onSwipe]). So lassen sich zwei Aufnahmen
 * vergleichen. [position] ("3 / 12") ersetzt den Zaehler oben, der waehrenddessen ausgeblendet
 * ist.
 */
@Composable
internal fun BoxScope.PhotoInfoSheet(
    visible: Boolean,
    sideways: Boolean,
    details: PhotoDetails?,
    loading: Boolean,
    position: String?,
    onSizeChanged: (IntSize) -> Unit,
    onSwipe: (step: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ViewerSheet(
        visible = visible,
        sideways = sideways,
        onSizeChanged = onSizeChanged,
        onDismiss = onDismiss,
        onSwipe = onSwipe,
    ) {
        // Beim Blaettern blenden die Daten weich ueber, statt hart zu wechseln.
        AnimatedContent(
            targetState = if (loading) null else details ?: PhotoDetails(),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "info",
        ) { shown ->
            when {
                shown == null -> Box(
                    modifier = Modifier.fillMaxWidth().height(LOADING_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MUTED, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                else -> InfoContent(shown, position)
            }
        }
    }
}

@Composable
private fun InfoContent(details: PhotoDetails, position: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Wann
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.takenDate ?: "Foto",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                details.subtitle()?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(text = it, color = MUTED, fontSize = 14.sp)
                }
            }
            if (position != null) {
                Text(
                    text = position,
                    color = MUTED,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 3.dp),
                )
            }
        }

        // 2. Wie belichtet
        val tiles = details.exposureTiles()
        if (tiles.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { (value, label) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = value,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(text = label, color = MUTED, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // 3. Was fuer eine Datei — zweispaltig, Bezeichnung klein ueber dem Wert wie bei den
        // Kacheln. Als einspaltige Liste brauchte das fast die Haelfte des Panels, und jeder
        // Millimeter Panel geht dem angehobenen Foto ab.
        val rows = details.detailRows()
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value) ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = label, color = MUTED, fontSize = 12.sp)
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = value,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // Ungerade Anzahl: die rechte Spalte bleibt leer, statt dass die
                        // letzte Angabe die ganze Breite einnimmt.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (tiles.isEmpty() && rows.isEmpty() && details.takenDate == null) {
            Spacer(Modifier.height(8.dp))
            Text(text = "Keine Aufnahmedaten verfügbar", color = MUTED, fontSize = 14.sp)
        }

        // 4. Dateiname
        details.fileName?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}
