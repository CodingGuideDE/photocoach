# Plan: Fotos nach der Aufnahme ansehen, bewerten und bearbeiten

Stand: 06.10.2026. Ergänzt `Plan-zur-Umsetzung.md`. Gilt wie dort zunächst nur für Android
(Abschnitt 0); Logik und UI liegen trotzdem in `commonMain`.

## 1. Ausgangslage

Die Vollbild-Ansicht steht: Vorschau links vom Auslöser, Öffnen aus dem Kreis heraus,
Zoomen, Wegwischen, Blättern durch alle App-Fotos. **Stufe A ist fertig** (06.10.2026):
Löschen, Teilen, Info. Bearbeiten gibt es noch nicht. Die Aufgabenliste mit Haken steht im
Hauptplan, `Plan-zur-Umsetzung.md` Abschnitt 4a.

## 2. Leitidee: Vorschläge statt Werkzeugkasten

Eine weitere Bearbeitungs-App mit zwanzig Reglern braucht niemand. Google Fotos und
Snapseed können das besser, als wir es je bauen würden. Unser Vorteil ist etwas anderes:
**Wir wissen, was mit der Komposition nicht stimmt**, und zwar mit denselben Regeln, die
schon im Sucher laufen. Die Bearbeitung setzt also das Coaching nach der Aufnahme fort:

> Foto öffnen → „Horizont 2,3° geneigt — **begradigen**?“ · „Zuschnitt verbessern:
> Score 61 → 78 — **ansehen**?“

Jedes Werkzeug kommt zuerst als **konkreter, einzeln annehmbarer Vorschlag** mit
Begründung. Von Hand nachjustieren bleibt möglich, ist aber nicht der Einstieg.
Damit wird auch Phase 2 des Hauptplans eingelöst: Score nach der Aufnahme und
nachvollziehbare Begründung.

## 3. Features nach Stufen

Die Reihenfolge folgt dem Verhältnis von Nutzen zu Aufwand. Jede Stufe ist für sich
auslieferbar. Die Aufwände sind grobe Schätzungen in Arbeitstagen.

### Stufe A — Grundfunktionen im Viewer ✅ (06.10.2026)

| Feature | Inhalt | Stand |
|---|---|---|
| **Löschen** | Papierkorb oben rechts, Bestätigung, danach nächstes Foto | ✅ |
| **Teilen** | System-Teilen-Dialog (`Intent.ACTION_SEND` mit der MediaStore-URI) | ✅ keine Berechtigung nötig |
| **Info** | Zeit, Auflösung, Dateigröße, Belichtungszeit, Blende, ISO, Brennweite, Weißabgleich, Blitz, Kamera, Datei | ✅ aus EXIF + MediaStore |

Unten sitzt die **Aktionsleiste**, derzeit mit `Teilen · Info`. „Analyse“ und
„Bearbeiten“ kommen mit Stufe B und C dazu, nicht vorher als ausgegraute Platzhalter. Oben:
links das X, in der Mitte der Zähler, rechts der Papierkorb.

**Was der Info-Ansicht noch fehlt:** Objektiv, Zoomstufe, Pro-Einstellungen im Sinne von
„was war eingestellt“ und der Score zur Aufnahmezeit. Nichts davon steht im EXIF, es braucht
die Aufnahme-Metadaten aus 4.3. Die Kamerawerte, die tatsächlich verwendet wurden (ISO,
Belichtungszeit, Weißabgleich), zeigt die Info dagegen schon, auch bei Automatik.

### Stufe B — Kompositions-Analyse des fertigen Fotos (~4 Tage)

Das ist die Voraussetzung für alle Vorschläge und für sich schon ein Feature.

- Das gespeicherte Foto einmal durch die Analyse schicken: Gesichter (ML Kit auf einem
  Bitmap statt einem Kamera-Frame), Saliency (`SpectralResidualSaliency`, läuft schon in
  `commonMain`) und Neigung. Ergebnis ist eine normale `FrameAnalysis`.
- Diese `FrameAnalysis` geht durch den bestehenden `CompositionScorer`. So entstehen Score
  und Hinweis für das Foto mit **genau derselben Logik wie im Sucher**.
- Umschaltbare Overlays: Drittel-Raster, Saliency-Heatmap, erkannte Gesichter. Das ist
  das „erklärbare Overlay“ aus Phase 2, hier ohne Live-Druck.

**Definition of Done:** Score und Hinweis im Viewer stimmen mit dem überein, was der
Sucher kurz vor der Aufnahme gezeigt hat (Gegenprobe über das `FieldLog`).

### Stufe C — Kompositions-Bearbeitung (Kern, ~8 Tage)

| Feature | Inhalt | Vorschlag kommt aus |
|---|---|---|
| **Begradigen** | Drehen um ±15°, automatisch auf das größte Rechteck ohne schwarze Ecken beschnitten | Neigung zur Aufnahmezeit (Sensor, 4.3) oder Bildanalyse |
| **Zuschneiden** | frei oder mit festem Format: Original 4:3, 1:1, 4:5 (Instagram-Feed), 9:16 (Story), 3:2, 16:9; Drittel-Raster im Rahmen | — |
| **Smart-Crop** | „Komposition verbessern“: bis zu drei Ausschnitte mit Score vorher/nachher | Suche über `CompositionScorer`, siehe 4.2 |
| **Drehen / Spiegeln** | 90°-Schritte, horizontal spiegeln | Spiegeln löst nebenbei die offene Selfie-Frage aus CLAUDE.md |

**Smart-Crop pro Format** ist der eigentliche Mehrwert: „Mach mir daraus einen guten
4:5-Ausschnitt für den Feed“ ist eine Frage, die heute kaum ein Werkzeug gut beantwortet.
Das baut eine Brücke zum Story-Coach in Phase 5.

**Definition of Done:** Bei einem absichtlich schief und mittig komponierten Testfoto
schlägt die App Begradigen und einen Drittel-Ausschnitt vor. Nach dem Übernehmen steigt der
Score, und das gespeicherte Ergebnis entspricht der Vorschau pixelgenau im Ausschnitt.

### Stufe D — Licht & Farbe (~5 Tage)

- **Regler:** Belichtung, Kontrast, Sättigung, Wärme (Blau↔Gelb), Tönung (Grün↔Magenta).
  Alle fünf sind lineare Farboperationen, also eine einzige 4×5-`ColorMatrix`. Die läuft
  in Compose plattformneutral und in Echtzeit, auch auf schwachen Geräten.
- **Auto:** ein einzelner „Auto“-Knopf aus dem Histogramm, der Belichtung und Kontrast so
  spreizt, dass weder Lichter noch Tiefen abgeschnitten werden. Auch das ist ein Vorschlag
  mit Begründung („Foto 0,7 Blenden zu dunkel“).
- **Erst später:** Lichter/Tiefen, Dynamik, Schärfe, Vignette. Sie sind nicht linear und
  brauchen eine Tonkurve pro Pixel (AGSL-Shader ab API 33 oder CPU-Lookup-Table). Das ist
  deutlich mehr Aufwand für weniger Bezug zum Kern der App.

### Stufe E — Bedienkomfort (~3 Tage, wächst mit C und D)

- **Vorher/Nachher:** Solange der Finger auf dem Bild liegt, ist das Original zu sehen.
- **Rückgängig/Wiederholen** und „Alles zurücksetzen“. Mit dem Bearbeitungsrezept aus 4.1
  ist das nur ein Stapel von Rezepten.
- **Speichern als Kopie** (Standard) oder Original ersetzen, siehe 5.1.
- **Erneut bearbeiten:** Das Rezept wird mitgespeichert, Regler starten wieder bei den
  letzten Werten statt bei null.

### Bewusst nicht (oder erst viel später)

- **Filter-Presets, Text, Sticker, Rahmen:** kein Bezug zur Komposition, bei anderen Apps
  besser aufgehoben.
- **Retusche / Objekte entfernen:** braucht generative Modelle, Größenordnung Monate, und
  verwässert den Fokus.
- **KI-Zuschnitt-Modell** (GAICD/VPN, `ML-Architektur.md` S6): erst, wenn die Heuristik aus
  Stufe C nachweislich nicht reicht. Gleiche Logik wie Phase 6 im Hauptplan.
- **Serien-Angleichung** (Farbe über mehrere Fotos angleichen): gehört zu Phase 5 und
  kommt dort.

## 4. Architektur

### 4.1 Bearbeitung als Rezept, nicht als Pixel

```kotlin
// commonMain, domain/edit/
data class EditRecipe(
    val rotationQuarterTurns: Int = 0,      // 90°-Schritte
    val straightenDegrees: Float = 0f,      // Feindrehung
    val mirrored: Boolean = false,
    val crop: NormalizedRect = NormalizedRect.FULL,  // nach dem Drehen, 0..1
    val exposureEv: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
)
```

- **Pure Daten**, ohne Plattform-API. Die ganze Geometrie (größtes Rechteck ohne schwarze
  Ecken nach Drehung, Format-Einrasten, Zuschnitt-Kandidaten) und die `ColorMatrix` werden
  daraus berechnet und sind **per Unit-Test prüfbar** wie die Regeln in `domain/rules/`.
- **Vorschau:** Compose zeichnet das geladene Bild mit Transformation und
  `ColorFilter.colorMatrix`. Das liegt in `commonMain`, läuft in Echtzeit, und es wird
  nichts neu gerechnet.
- **Export:** dasselbe Rezept auf das Bild in voller Auflösung angewendet, gezeichnet in
  ein `ImageBitmap` über Compose `Canvas` (ebenfalls plattformneutral). Nur das Kodieren
  zu JPEG und das Speichern sind plattformspezifisch, als neue Methode an der bestehenden
  Naht: `PhotoLibrary.save(bitmap, basedOn: CapturedPhoto): CapturedPhoto?`.
- **EXIF beim Export:** Aufnahmedatum und Kameradaten aus dem Original übernehmen
  (`ExifInterface`), die Orientierung auf „normal“ setzen. Das Bild ist dann ja schon
  gedreht.

### 4.2 Smart-Crop ohne Modell: Analyse einmal, Bewertung hundertfach

Naiv würde man für jeden Ausschnitt-Kandidaten die Bildanalyse neu laufen lassen, das sind
Sekunden pro Kandidat. Das ist unnötig: Gesichter und Saliency-Punkte liegen als
**normierte Koordinaten** vor. Für einen Ausschnitt rechnet man sie einfach in dessen
Koordinatensystem um (`(x − links) / breite`); was herausfällt, ist abgeschnitten. Die
Neigung verringert sich um den Begradigungswinkel. Daraus entsteht eine neue
`FrameAnalysis`, die direkt in den `CompositionScorer` geht.

```
FrameAnalysis (einmal, ~200 ms)  →  für jeden Kandidaten: transformieren + bewerten (µs)
```

- **Kandidaten:** pro Format ein Raster aus Größen (100 %, 90 %, 80 %, 70 % der maximal
  möglichen Fläche) × Positionen (Schrittweite ~5 %). Das ergibt einige hundert, in
  Millisekunden bewertet.
- **Nebenbedingungen**, damit kein absurder Ausschnitt gewinnt: mindestens 50 % der
  Originalfläche, kein Gesicht angeschnitten (dafür gibt es schon die
  `PortraitFramingRule`).
- **Ausgabe:** die besten drei, die sich deutlich voneinander unterscheiden (Überlappung
  unter ~80 %), und nur, wenn der Score um mindestens ~8 Punkte steigt. Sonst lautet die
  ehrliche Antwort „Komposition passt schon“.

Dieselbe Transformation ist auch der Test: „Gesicht bei x = 0,5 im Original, Ausschnitt
von 0,2 bis 0,8 → x = 0,5“ ist ein reiner Unit-Test ohne Bild.

### 4.3 Aufnahme-Metadaten

Mehrere Features brauchen Daten, die nur im Moment der Aufnahme vorliegen: die Neigung
laut Sensor (genauer als jede Bildanalyse), Objektiv, Zoom, Pro-Einstellungen, Score und
Hinweis zu dem Zeitpunkt. Heute landen sie nur im `FieldLog`.

```kotlin
data class CaptureMeta(
    val tiltDegrees: Float?, val lens: LensFacing, val zoom: Float,
    val manual: ManualSettings, val score: Int?, val hint: String?,
    val recipe: EditRecipe? = null,   // ab Stufe E
)
```

Wo das gespeichert wird, ist eine offene Entscheidung (5.2). In beiden Fällen ist der
Schlüssel die MediaStore-ID, und das Schreiben passiert in `saveToGallery`.

### 4.4 Wo es im Code landet

```
commonMain/
  domain/edit/        EditRecipe, CropGeometry, ColorAdjust, CropSuggester   ← Unit-Tests
  domain/model/       CaptureMeta
  ui/PhotoViewer.kt   Aktionsleiste, Vorschlags-Chips, Overlays
  ui/PhotoEditor.kt   Bearbeiten-Modus (Zuschnitt-Rahmen, Regler, Vorher/Nachher)
  ui/PhotoLibrary.kt  + delete, save, meta
  capture/            + Standbild-Analyse (FrameAnalyzer bekommt einen Einstieg für Bitmaps)
androidMain/
  ui/PhotoLibrary.android.kt   MediaStore: löschen, als Kopie speichern, EXIF übernehmen
  capture/FrameAnalyzer.android.kt   ML Kit mit InputImage.fromBitmap
iosMain/                       Stubs, damit es kompiliert (Abschnitt 0)
```

## 5. Offene Entscheidungen

1. **Speichern: Kopie oder Original ersetzen?** Empfehlung: **als Kopie**
   (`PhotoCoach_…_bearbeitet.jpg`), das Original bleibt. „Original ersetzen“ ist eine
   zweite, bewusste Option. Eine misslungene Bearbeitung kostet dann nie ein Foto.
2. **Metadaten: app-intern oder in der Datei (XMP)?** App-intern (kleine Datenbank oder
   JSON pro Foto im App-Ordner) ist einfach und bleibt privat. Dafür geht es bei einer
   Deinstallation verloren und reist nicht mit beim Teilen. XMP in der JPEG-Datei reist mit,
   ist aber mehr Aufwand und landet auch bei jedem, mit dem man das Foto teilt.
   Empfehlung: **app-intern**. Das passt zum On-Device-Versprechen, und geteilte Fotos
   sollen keine Coaching-Daten mitschleppen.
3. **Bearbeiten als eigener Bildschirm oder als Modus im Viewer?** Empfehlung: **Modus im
   Viewer**. Das Bild bleibt, wo es ist, und unten wechselt die Aktionsleiste zu den
   Werkzeugen. Kein Bildschirmwechsel, keine zweite Navigation.
4. **Licht & Farbe nur linear (Stufe D) oder gleich mit Lichter/Tiefen?** Empfehlung:
   **nur linear**, Lichter/Tiefen erst bei konkretem Bedarf.

## 6. Reihenfolge und Abhängigkeiten

```
A Grundfunktionen ──┐
                    ├─→ B Analyse ─→ C Komposition ─→ E Komfort
4.3 Metadaten ──────┘                       └────────→ D Licht & Farbe
```

- 4.3 lohnt sich **sofort**: Jede Aufnahme ohne Metadaten ist später ein Foto ohne
  Sensor-Neigung und ohne Aufnahme-Score.
- B vor C: Ohne Analyse gibt es keine Vorschläge, und ohne Vorschläge ist C nur ein
  gewöhnlicher Zuschnitt.
- Gesamt grob **4–5 Wochen** für A–E, davon C als größter Block.

## 7. Nur auf echter Hardware prüfbar

- Export in voller Auflösung bei 50-MP-Sensoren: Speicherbedarf (~200 MB als Bitmap).
  Eventuell braucht es eine Obergrenze wie `FULL_MAX_DIMENSION` oder Kacheln.
- Ob die Neigung zur Aufnahmezeit wirklich zum Bild passt (gleiche offene
  Vorzeichen-Frage wie beim Horizont-Hinweis in CLAUDE.md).
- Echtzeit-Vorschau der Regler auf einem Mittelklasse-Gerät.
