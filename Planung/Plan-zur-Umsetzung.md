# Plan zur Umsetzung: App für Bildkomposition & Kamerawinkel

Status: **Review-Entwurf**, Stand 22.09.2026
**Plattform-Fokus: Android. iOS pausiert — siehe Abschnitt 0.**
Basiert auf [Umsetzbarkeit.md](./Umsetzbarkeit.md) (inkl. Feature-Lücken-Analyse Abschnitt 6)
und der Tech-Stack-Entscheidung für Compose Multiplatform.

---

## 0. Entscheidung: iOS pausiert, Android zuerst (22.09.2026)

**Die Entwicklung läuft ab sofort nur auf Android weiter.** Damit ist die offene Frage aus
Abschnitt 12 beantwortet: Phase 1 wird als **Android-MVP** abgeschlossen, nicht als
Zwei-Plattform-MVP.

**Anlass.** Ohne physisches iPhone ist die iOS-Hälfte nicht verifizierbar. Der Simulator
kann die entscheidenden Dinge nicht zeigen (Abschnitt 12), und Code, der sich nicht prüfen
lässt, sammelt unbemerkt Fehler an. Parallel zwei Plattformen zu bauen, von denen eine
blind bleibt, kostet doppelt und liefert einfach.

### Was pausiert

- **3.1 (iOS-`FrameAnalyzer`)** — die drei Vision-Requests sind gebaut, bleiben aber
  unangeschlossen. Keine weitere Arbeit daran.
- **`AVCaptureSession`, Vorschau und Auslöser auf iOS** — nicht begonnen, bleibt so.
- **Der iOS-Anteil jeder Definition of Done** — gilt bis auf Weiteres nur für Android.
- **TestFlight und App-Store-Einreichung** in Phase 8.

### Was ausdrücklich bleibt

- **Die KMP-Struktur und die `expect`/`actual`-Naht.** Sie sind der Grund, warum diese
  Pause billig ist: Die gesamte Logik in `domain/` und die UI in `commonMain` entstehen
  weiterhin plattformneutral. Wenn iOS zurückkommt, fehlt dort nur die Sensorik, nicht
  die App.
- **Der iOS-Code kompiliert weiter, seine Tests laufen mit.** Das ist bewusst so: Ändert
  sich etwas in `commonMain` — ein neues Feld in `FrameAnalysis` etwa —, muss das iOS-
  `actual` nachgezogen werden. Das kostet beim Ändern Minuten. Lässt man es verrotten,
  wird daraus beim Wiederaufnehmen eine Woche Archäologie.
- **`./gradlew :shared:allTests` bleibt der Maßstab vor jedem Commit.** Im Alltag reicht
  `:shared:testDebugUnitTest` (schneller, kein iOS-Compile).

### Was die Pause erleichtert

Die Modell-Entscheidung aus Abschnitt 11 wird dadurch **einfacher**, nicht schwerer: Solange
nur Android gebaut wird, muss die Android-Saliency zu keiner iOS-Semantik passen. Die
Abwägung „Attention- gegen Objekt-Saliency, Plattformen laufen auseinander" aus
[ML-Architektur.md](./ML-Architektur.md) ist damit **vertagt**, nicht gelöst — sie kommt
zurück, sobald iOS wieder dazukommt, und ist dann an der `FrameAnalysis`-Naht immer noch
austauschbar.

### Wann iOS zurückkommt

Zwei Bedingungen, beide nötig:

1. Ein physisches iPhone zum Testen ist verfügbar.
2. Der Android-MVP (Phase 1) ist abgeschlossen und auf echter Hardware bewertet.

Einstiegspunkt ist dann `shared/src/iosMain/README.md` — dort steht, was gebaut ist, was
fehlt und welche Simulator-Fallen bekannt sind.

---

## 1. Tech-Stack & Architektur-Überblick

**Sprache/Framework:** Kotlin Multiplatform (KMP) + Compose Multiplatform.
Targets: **aktiv nur Android**, iOS-Targets bleiben im Build, werden aber nicht
weiterentwickelt (Abschnitt 0). Die Zwei-Plattform-Architektur bleibt bestehen — sie
ist der Grund, warum die Pause reversibel ist.

**Leitprinzip:** UI und Komposition-Logik geteilt, Kamera-/ML-Erfassung pro Plattform nativ.
Grund: Es gibt keine Cross-Platform-Bibliothek, die Apples Vision-Framework
(`VNDetectHorizonRequest`, `VNGenerateAttentionBasedSaliencyImageRequest`,
`VNDetectFaceRectanglesRequest`) und Androids Äquivalente (CameraX, ML Kit, eigenes
Saliency-Modell) einheitlich abstrahiert — das würde nur eine schlechtere gemeinsame
Untermenge beider Systeme ergeben.

### Modul-Struktur

```
photocoach/
├── shared/                          # Kotlin Multiplatform Modul
│   ├── commonMain/
│   │   ├── domain/                  # Reine Kotlin-Logik, keine Plattform-Abhängigkeiten
│   │   │   ├── model/               # FrameAnalysis, SaliencyPoint, FaceRect, Score, ...
│   │   │   ├── rules/               # RuleOfThirdsRule, HorizonRule, DeadSpaceRule, ...
│   │   │   ├── scoring/             # CompositionScorer (kombiniert Regeln zu Gesamt-Score)
│   │   │   └── coaching/            # Textbausteine/Übersetzung Regel-Ergebnis → Nutzer-Hinweis
│   │   ├── capture/
│   │   │   └── FrameAnalyzer.kt     # expect class — Interface für Plattform-Implementierung
│   │   └── ui/                      # Compose-UI: CameraScreen, GalleryScreen, ScoreOverlay, ...
│   ├── iosMain/
│   │   └── capture/
│   │       └── FrameAnalyzer.ios.kt # actual — AVFoundation + Vision-Framework (Swift-Interop)
│   └── androidMain/
│       └── capture/
│           └── FrameAnalyzer.android.kt # actual — CameraX + ML Kit + eigenes TFLite-Saliency-Modell
├── iosApp/                          # Dünner iOS-Host (Xcode-Projekt, startet Compose-UI)
└── androidApp/                      # Dünner Android-Host (startet Compose-UI)
```

**Kern-Interface (in `commonMain`), das die Plattformtrennung sauber hält:**

```kotlin
// commonMain
expect class FrameAnalyzer {
    fun analyze(frame: CameraFrame): FrameAnalysis
}

data class FrameAnalysis(
    val horizonTiltDegrees: Float?,     // null wenn nicht bestimmbar
    val saliencyRegions: List<SaliencyPoint>, // normierte Koordinaten 0.0–1.0
    val faces: List<FaceRect>,
    val timestampMs: Long
)
```

Jede Plattform füllt `FrameAnalysis` mit eigenen Mitteln (iOS: Vision-Requests,
Android: ML Kit + eigenes Modell + Sensor-Fusion für Horizont). Die gesamte
Regel-/Score-/Coaching-Logik in `domain/` kennt nur `FrameAnalysis`, nie die
zugrunde liegende Plattform — dadurch nur **eine** Implementierung der eigentlichen
"was ist gute Komposition"-Logik für beide Plattformen.

---

## 2. Phase 0 — Projekt-Setup (ca. 1 Woche)

- [x] KMP-Projekt aufsetzen (`shared`, `iosApp`, `androidApp`) via Kotlin Multiplatform Wizard
- [x] `FrameAnalyzer`-Interface (expect/actual) mit leeren/dummy `actual`-Implementierungen
      auf beiden Plattformen — Ziel: durchgängiger Daten-Fluss von Kamera bis UI steht,
      auch wenn Analyse noch nichts liefert
- [ ] ⏸️ **PAUSIERT (Abschnitt 0)** — Kamera-Grundfunktion iOS: AVCaptureSession,
      Live-Preview in Compose einbetten (`UIKitView`-Interop). Nicht begonnen.
- [x] Kamera-Grundfunktion Android: CameraX `Preview` + `ImageAnalysis` Use Cases
- [~] Git-Repo steht; CI-Grundgerüst noch offen — CI-Grundgerüst (GitHub Actions: Build für beide Targets bei jedem Push)
- [ ] LiveCapture-Repo (github.com/LiveCompose/LiveCapture) lokal klonen, Architektur als
      Referenz für spätere Phase 4 durchlesen

**Definition of Done (Android): ✅ erfüllt.** App startet, zeigt Kamera-Live-Bild, kann
ein Foto auslösen und in der Galerie speichern.
*iOS-Anteil pausiert (Abschnitt 0): App startet im Simulator und zeigt die geteilte UI,
aber kein Kamerabild.*

---

## 3. Phase 1 — MVP: Regelbasiertes Kompositions-Feedback (ca. 4-5 Wochen)

### 3.1 iOS-Implementierung von `FrameAnalyzer` — ⏸️ PAUSIERT (Abschnitt 0)

Die drei Vision-Requests sind **gebaut und kompilieren**, aber nicht angeschlossen:
es gibt keine Frame-Quelle (`AVCaptureVideoDataOutput` fehlt), `analyze()` wird in der
App nie aufgerufen — nur aus Tests. Inhaltlich unverifiziert, siehe Abschnitt 12.
Keine weitere Arbeit hier, bis die Bedingungen aus Abschnitt 0 erfüllt sind.

- [~] `VNDetectHorizonRequest` — Request implementiert, Drosselung fehlt (mangels
      Frame-Quelle nichts zu drosseln)
- [x] `VNGenerateAttentionBasedSaliencyImageRequest` → Heatmap auf 12×12-Raster,
      normierte Punkte mit Gewicht (inhaltlich unverifiziert, Abschnitt 12)
- [x] `VNDetectFaceRectanglesRequest` für Gesichtspositionen (Koordinaten-Umrechnung
      per Unit-Test abgesichert)
- [~] Mapping in `FrameAnalysis` steht — erreicht `commonMain` aber nie, weil der
      Analyzer nicht aufgerufen wird

### 3.2 Domain-Logik (geteilt, `commonMain`)
- [x] `RuleOfThirdsRule`: Abstand der größten Saliency-Region zu den 4 Schnittpunkten
      berechnen, Score + Richtungshinweis ("Motiv 12% zu weit links von der optimalen
      Position")
- [x] `HorizonRule`: Warnung bei `horizonTiltDegrees` > 2°
- [ ] `DeadSpaceRule` — **blockiert**: braucht Saliency, auf Android noch leer (§11).
      Anteil des Bilds ohne Saliency pro Bildhälfte vergleichen,
      Warnung bei starker Asymmetrie ohne erkennbaren Grund (z. B. Blickrichtung)
- [x] `PortraitFramingRule`: bei erkanntem Gesicht — Kopf nicht zu weit oben/unten
      abgeschnitten, ausreichend Blickraum in Blickrichtung (Blickrichtung grob aus
      Gesichts-Bounding-Box-Position relativ zu Bildmitte geschätzt)
- [~] `CompositionScorer`: **Auswahl eines Hinweises fertig** (`domain/scoring/`:
      `HintSelector` priorisiert nach Reparierbarkeit, `HintStabilizer` dämpft gegen
      Flackern bei 10 Hz). **Der 0-100-Score fehlt** — dafür müssten die Regeln melden,
      *wie stark* sie verletzt sind, nicht nur „Hinweis oder nicht". Eigener Schritt.

#### 3.2.1 `RuleOfThirdsRule` — konkrete Umsetzung

**Ausgangslage (Stand 27.08.2026).** Zwei Dinge bestimmen den Zuschnitt dieser Aufgabe:

1. `saliencyRegions` ist **nur auf iOS** echt (Vision-Attention-Saliency, auf ein 12×12-Raster
   heruntergerechnet). Auf Android ist das Feld weiterhin leer, weil die TFLite-Modell-
   Entscheidung aussteht (§11). Eine Regel, die sich allein auf Saliency stützt, wäre also
   auf Android wirkungslos — und würde die Plattformen inhaltlich auseinanderlaufen lassen.
2. `faces` ist auf beiden Plattformen echt (Android ML Kit, iOS `VNDetectFaceRectangles`).
   Ein erkanntes Gesicht ist ohnehin der verlässlichere Motiv-Anker als generische Saliency —
   die Regel sollte es also *grundsätzlich* bevorzugen, nicht nur als Notlösung.

Daraus folgt der Zuschnitt: Die Regel arbeitet nicht direkt auf `saliencyRegions`, sondern
auf einem daraus abgeleiteten **Motivpunkt**. Damit ist sie heute schon vollständig
testbar und auf beiden Plattformen live wirksam, und sie wird auf Android automatisch
besser, sobald Saliency dazukommt — ohne dass die Regel selbst sich ändert.

---

**Schritt 1 — `FrameAnalysis` um das Seitenverhältnis ergänzen**

- [x] `aspectRatio: Float` (Breite/Höhe des aufrecht gedrehten Analyse-Frames) in
      `FrameAnalysis` aufnehmen, auf Android aus `uprightWidth/uprightHeight` befüllen

Grund: In normierten 0–1-Koordinaten ist ein Versatz von 0,1 in x eine andere physische
Strecke als 0,1 in y. Ohne Korrektur bewertet die Regel im Hochformat vertikale Abweichungen
systematisch zu milde. Das Feld fehlt bisher im Contract aus §1 und muss dort mit ergänzt
werden.

**Schritt 2 — Motivbestimmung (`domain/subject/`)**

```kotlin
data class SubjectPoint(
    val x: Float,               // normiert 0..1
    val y: Float,
    val confidence: Float,      // 0..1
    val source: SubjectSource,  // FACE | SALIENCY
)

fun interface SubjectResolver {
    fun resolve(frame: FrameAnalysis): SubjectPoint?
}
```

- [x] `DefaultSubjectResolver` implementieren, Reihenfolge:
      1. **Gesicht vorhanden** → Mittelpunkt des flächengrößten `FaceRect`, `confidence = 1.0`
      2. **Sonst Saliency** → stärksten `SaliencyPoint` nehmen, dann den gewichteten
         Schwerpunkt aller Punkte im Umkreis von 0,15 um ihn herum bilden
      3. **Sonst** `null`
- [x] Bewusst **kein** Schwerpunkt über alle Saliency-Punkte: bei zwei Motiven links und
      rechts läge der Schwerpunkt genau in der Mitte — die Regel würde ein Motiv melden,
      das es gar nicht gibt. Der Umkreis-Filter verhindert das.

Diese Zwischenschicht ist zugleich die Stelle, an der die unterschiedliche Semantik der
beiden Plattformen (Apples Attention-Saliency vs. ein Segmentierungsmodell auf Android)
auf **eine** definierte Bedeutung gebracht wird: „geschätzter Mittelpunkt des Hauptmotivs".

**Schritt 3 — Geometrie (`domain/geometry/Thirds.kt`)**

- [x] Die vier Schnittpunkte als Konstanten: (⅓,⅓), (⅔,⅓), (⅓,⅔), (⅔,⅔)
- [x] Seitenverhältnis-korrigierte Distanz, gerechnet in Einheiten der **Bildbreite**:

```kotlin
fun distance(ax: Float, ay: Float, bx: Float, by: Float, aspectRatio: Float): Float {
    val dx = bx - ax
    val dy = (by - ay) / aspectRatio   // 1/aspectRatio = Höhe/Breite
    return sqrt(dx * dx + dy * dy)
}
```

**Schritt 4 — Regel-Interface + `RuleOfThirdsRule` (`domain/rules/`)**

```kotlin
sealed interface RuleResult {
    data object NotApplicable : RuleResult          // kein Motiv erkennbar
    data class Evaluated(val score: Float, val detail: RuleDetail) : RuleResult
}
```

- [x] `NotApplicable` ≠ Score 0 — ohne erkanntes Motiv hat die Regel *keine* Meinung.
      Ein 0-Score würde den Gesamtscore fälschlich nach unten ziehen und den Nutzer
      für etwas rügen, das die App schlicht nicht sehen kann.
- [x] Score-Kennlinie: Distanz 0 → 1.0; ab 0,04 beginnt der Abfall; bei 0,167
      (= Abstand Bildmitte ↔ Drittel-Linie) → 0.0. Dazwischen `smoothstep` statt linear,
      damit kleine Wackler nahe am Optimum den Score nicht sichtbar zappeln lassen.
- [x] Die Mitte ist **kein** Fehler: Ein mittig platziertes Motiv landet bei Score ≈ 0,
      aber die Regel bekommt im `CompositionScorer` nur mittleres Gewicht — Symmetrie ist
      eine legitime Bildsprache, keine Regelverletzung.
- [x] `RuleDetail` trägt Motivpunkt, gewählten Zielpunkt und den Versatz mit — Phase 2
      (erklärbares Overlay) und Phase 4 (Richtungspfeil) zeichnen genau daraus.

**Schritt 5 — Zielpunkt stabilisieren**

- [x] Nächstgelegenen der vier Punkte wählen — aber **mit Hysterese**: der bisher gewählte
      Zielpunkt bleibt gültig, solange kein anderer mindestens 15 % näher liegt

Ohne das kippt die Wahl bei einem Motiv nahe der Bildmitte zwischen zwei fast gleich weit
entfernten Punkten hin und her, und der Hinweis springt im Sekundentakt zwischen „links"
und „rechts". Der Zustand gehört **nicht** in die Regel (die bleibt eine reine Funktion),
sondern in einen `ThirdsTargetTracker`, den die aufrufende Schicht über Frames hinweg hält.

**Schritt 6 — Hinweistext (`domain/coaching/`)**

- [x] Formulierung **beschreibend**, nicht anweisend: „Motiv sitzt 12 % links vom
      Drittelpunkt" — Versatz in Prozent der Bildbreite, gerundet
- [x] Unter 5 % Versatz keinen Hinweis ausgeben (das ist innerhalb der Messungenauigkeit
      der Motivbestimmung)

⚠️ **Falle für Phase 4:** Handlungsanweisungen sind *umgekehrt* zum Versatz. Sitzt das Motiv
zu weit links im Bild, muss die Kamera nach **links** geschwenkt werden, damit das Motiv im
Bild nach rechts wandert. In Phase 1 wird deshalb bewusst nur beschrieben, nicht angewiesen —
die Umkehrung gehört zusammen mit dem Pfeil-Indikator in Phase 4 und will dort einmal
sauber durchdacht und getestet werden.

**Testfälle (`commonTest`, Bausteine liegen in `TestFrames.kt` bereit)**

- [x] Motiv exakt auf (⅓,⅓) → Score 1,0
- [x] Motiv exakt in der Bildmitte → Score ≈ 0, kein Absturz, `Evaluated` (nicht `NotApplicable`)
- [x] Kein Gesicht, keine Saliency → `NotApplicable`
- [x] Gesicht **und** Saliency vorhanden, an verschiedenen Stellen → Gesicht gewinnt
- [x] Zwei Saliency-Punkte links und rechts → Motivpunkt landet auf einem der beiden,
      **nicht** in der Mitte
- [x] Gleicher normierter Versatz in Hoch- und Querformat → unterschiedlicher Score
      (belegt, dass `aspectRatio` wirkt)
- [x] Motiv wandert langsam über die Bildmitte → Zielpunkt wechselt **einmal**, nicht mehrfach
- [x] Versatz 3 % → kein Hinweistext; Versatz 12 % → Text nennt „12 %"

**Umgesetzt am 27.08.2026** — 32 Tests in `commonTest`, alle gruen
(`./gradlew :shared:allTests`). Drei bewusste Abweichungen von der obigen Skizze:

1. **Kein `RuleResult`.** Die Codebasis hatte bereits einen `Rule`-Contract
   (`evaluate(FrameAnalysis): Hint?`, siehe `Rule.kt`), dem `HorizonRule` und
   `PortraitFramingRule` folgen. `RuleOfThirdsRule` folgt ihm ebenfalls, statt einen
   zweiten Ergebnistyp einzufuehren. Score und Versatz — die ein `Hint` nicht tragen kann —
   liefert stattdessen `RuleOfThirdsRule.analyse()` als `ThirdsEvaluation`, fertig fuer
   `CompositionScorer` (Score) und die Overlays aus Phase 2/4 (Motivpunkt, Zielpunkt).
2. **Der Hinweis ist anweisend, nicht beschreibend.** `PortraitFramingRule` formuliert
   bereits als Kamera-Aktion und hat die Umkehrung sauber dokumentiert; zwei verschiedene
   Sprachformen nebeneinander waeren fuer den Nutzer schlechter als die Abweichung von
   diesem Plan. Die Umkehrung ist umgesetzt und mit eigenem Test abgesichert — die Warnung
   fuer Phase 4 bleibt trotzdem stehen, sie gilt dort fuer den Pfeil-Indikator.
3. **Kein Abdeckungs-Tor.** Fuer "ist der Saliency-Bereich konzentriert genug" reicht der
   Cluster-Anteil allein: bei gleichmaessiger Verteilung haelt der Klumpen um das Maximum
   nur einen Bruchteil des Gewichts und faellt schon dadurch durch. Ein zweites Tor ueber
   den Anteil belegter Rasterzellen haette zusaetzlich die Rastergroesse (12x12) in
   `commonMain` festgeschrieben — und die gilt nur fuer iOS, nicht fuer Androids kuenftiges
   Modell. Ein Test deckt den Fall "weisse Wand" ab.

Offen geblieben: Der Hinweis wurde noch **nicht live ausgeloest gesehen**. Auf Android
fehlt Saliency (§11) und die Emulator-Szene enthaelt kein Gesicht; auf iOS braeuchte es
Hardware (§12). Die Regel selbst ist ueber Unit-Tests abgedeckt, die Verdrahtung im
`CameraScreen` nur daraufhin, dass sie laeuft und bei fehlendem Motiv korrekt schweigt.

**Reihenfolge & Aufwand**

| Schritt | Aufwand |
|---|---|
| 1 — `aspectRatio` in Contract + Android | 0,5 Tag |
| 2 — `SubjectResolver` + Tests | 1 Tag |
| 3 — Geometrie + Tests | 0,5 Tag |
| 4 — Regel + Score-Kennlinie + Tests | 1 Tag |
| 5 — Zielpunkt-Hysterese + Test | 0,5 Tag |
| 6 — Hinweistext + Anbindung ans UI | 1 Tag |
| **Summe** | **~4,5 Tage** |

Schritte 2–5 laufen vollständig in `commonTest` — ohne Emulator, ohne Kamera, in
Millisekunden. Erst Schritt 6 braucht wieder ein Gerät.

**Bewusst nicht in Phase 1:** Augenhöhe statt Gesichtsmitte als Anker (gehört zu
`PortraitFramingRule`), Goldener Schnitt als alternatives Raster, Motiv-*Fläche* statt
Motiv-*Punkt*, und Blickrichtungs-abhängige Wahl des Zielpunkts (links/rechts je nachdem,
wohin die Person schaut) — Letzteres ist der naheliegendste Ausbau direkt nach Phase 1.

---

### 3.3 Android-Implementierung von `FrameAnalyzer` — **Schwerpunkt**

Seit Abschnitt 0 der einzige aktive Sensorik-Pfad.

- [x] CameraX `ImageAnalysis`-Pipeline für Frame-Zugriff (~10 Hz gedrosselt)
- [x] ML Kit Face Detection für `faces` (on-device, gebündeltes Modell)
- [x] Horizont: `SensorManager` (Schwerkraft-Sensor) statt Bildanalyse —
      deckt den Hauptfall (Gerät schief gehalten) ab, deckt NICHT den Fall "Gerät gerade,
      Horizont im Bild schief" ab (siehe Umsetzbarkeit.md, akzeptierte Einschränkung für v1)
- [ ] **← Engpass Nr. 1.** Saliency: leichtes offenes TFLite-Modell (z. B. U2Net-lite oder vergleichbar,
      < 5 MB, quantisiert) einbinden — **Rechercheaufgabe:** konkretes Modell mit
      passender Lizenz und Mobile-Performance evaluieren, bevor Implementierung startet
- [~] `FrameAnalysis` befüllt bis auf `saliencyRegions`

### 3.4 UI (Compose, geteilt)
- [x] `ScoreOverlay`: Leiste am oberen Rand, Symbol + Text, immer nur ein Hinweis,
      gedämpft gegen Flackern. Kein Overlay über dem Sucherbild.
- [ ] Grid-Overlay (Drittel-Regel) ein-/ausblendbar
- [ ] Ton/Haptik-Trigger bei Score-Sprung (dezent, kein Dauer-Feedback)

### 3.5 Testing
- [ ] 50-100 eigene Testfotos **auf Android**, manuelle Bewertung ob Feedback
      korrekt/sinnvoll (iOS pausiert, Abschnitt 0)
- [ ] Performance-Test: Frame-Analyse-Latenz auf älterem Testgerät (nicht nur Flaggschiff)

**Definition of Done (angepasst, Abschnitt 0): Auf Android** lauffähiger MVP mit
korrektem, nachvollziehbarem Live-Feedback zu Horizont, Drittel-Regel und toter Fläche,
auf echter Hardware bewertet.

*Ursprünglich „auf beiden Plattformen" — die iOS-Hälfte ist ohne Gerät nicht
erreichbar (Abschnitt 12) und bewusst verschoben.*

⚠️ Die iOS-Hälfte dieser DoD ist ohne physisches Gerät nicht überprüfbar — welche Punkte
das betrifft und warum, steht in **§12 (Nur auf echter Hardware verifizierbar)**.

---

## 4. Phase 2 — Erklärbares Overlay & Nach-Aufnahme-Score (ca. 3 Wochen)

Umsetzt Priorität 1 aus Umsetzbarkeit.md Abschnitt 6.4 — geringer Zusatzaufwand,
da Daten aus Phase 1 bereits vorliegen.

- [ ] Saliency-Heatmap direkt als halbtransparentes Overlay visualisieren (nicht nur
      intern für Scoring nutzen) — Compose `Canvas`, Heatmap-Punkte aus `FrameAnalysis`
- [ ] Erkannte Linien/Kanten optional einblendbar (Lerneffekt: "das hier hat die App
      gesehen, deshalb der Vorschlag")
- [ ] Ästhetik-Score nach Aufnahme: Modell-Recherche (NIMA-artig), Konvertierung zu
      CoreML (iOS) **und** LiteRT (Android) — zwei Artefakte aus einer Quelle,
      `coremltools` bzw. TFLite-Converter
- [ ] Galerie-Ansicht mit Score pro Foto, Sortierung/Filter

**Definition of Done:** Nutzer sieht während der Aufnahme *und* danach nicht nur
eine Zahl, sondern nachvollziehbare visuelle Begründung.

---

## 5. Phase 3 — Textbasiertes Coaching (ca. 1-2 Wochen)

Umsetzt Priorität 2 aus Umsetzbarkeit.md Abschnitt 6.1.

- [ ] Neue Ausgabe-Schicht auf denselben `FrameAnalysis`-Daten: statt/zusätzlich zum
      Overlay kurze Text-Kurzanweisungen direkt im Kamera-UI
- [ ] Formulierungslogik für konkrete, handlungsanweisende Hinweise, z. B.
      "Person etwas weiter rechts, näher rankommen", Frequenz begrenzt (nicht bei
      jedem Frame, nur bei relevanter Änderung)
- [ ] UI-Test: Hinweise müssen bei kurzem Blick aufs Display sofort verständlich sein
      (nicht zu lang, keine Fachbegriffe)

**Definition of Done:** Nutzer bekommt während der Aufnahme klare, direkte
Text-Handlungsanweisungen statt nur eines abstrakten Scores.

*Später denkbare Ausbaustufe (nicht Teil dieser Phase):* Dieselben Kurzanweisungen
zusätzlich per Sprache (`AVSpeechSynthesizer`/`TextToSpeech`) oder Haptik
(`CoreHaptics`/`VibrationEffect`) ausgeben, um die App auch für blinde/sehbehinderte
Nutzer zugänglich zu machen — aktuell nicht Teil der Zielgruppe, daher zurückgestellt.

---

## 6. Phase 4 — Aktive Richtungsvorschläge, Heuristik-Version (ca. 3-4 Wochen)

- [ ] Aus Saliency-Position + Ziel-Position (nächster Drittel-Punkt) Richtungsvektor
      berechnen → Text-/Pfeil-Hinweis ("etwas nach links schwenken")
- [ ] Live-Pfeil-Indikator im Sucher (Compose `Canvas`, Richtung + Intensität)
- [ ] Interner A/B-Vergleich: eigene Fotoserien mit/ohne aktivem Hinweis

**Definition of Done:** Erste Version "aktiver Hilfe" ohne eigenes RL-Modell,
Nutzen intern verifiziert.

---

## 7. Phase 5 — Story-/Sequenz-Coach für Content-Creator (ca. 2-3 Wochen)

Umsetzt Priorität 3 aus Umsetzbarkeit.md Abschnitt 6.3.

- [ ] Shot-Type-Klassifikation (weit/mittel/nah) aus Motiv-zu-Bild-Flächenverhältnis
- [ ] Dominante-Farben-Extraktion (klassische Bildverarbeitung, kein ML nötig)
- [ ] Analyse über mehrere ausgewählte Fotos einer geplanten Serie: Abwechslung der
      Einstellungsgrößen, Farbkonsistenz, Hinweis bei zu großer Ähnlichkeit/Bruch

**Definition of Done:** Nutzer kann 3-10 Fotos als "Serie" markieren und bekommt
Feedback zu Abwechslung/Konsistenz für Carousel-/Story-Format.

---

## 8. Phase 6 — Optional: Eigenes ML-Modell für Richtungsvorschläge (offen, Monate)

Nur angehen, wenn Phase 4 zeigt, dass Nutzer/du selbst einen spürbaren Unterschied
zur einfachen Heuristik willst.

- [ ] LiveCapture-Code (MIT) im Detail studieren: Two-Stage-CoreML-Ansatz
      (BBox-Modell + "Actor"-Modell) als Referenz
- [ ] Eigenen Datensatz sammeln oder AVA-Datensatz für Vortraining nutzen
- [ ] Training (Python/PyTorch), Export zu CoreML **und** LiteRT
- [ ] Ausgiebiges Testing — schlechte KI-Vorschläge sind schlimmer als keine

---

## 9. Phase 7 — Sozialer Duell-Modus (optional, Spätphase)

Umsetzt Priorität aus Umsetzbarkeit.md Abschnitt 6.5. Bewusst spät, da Backend-lastig.

- [ ] Backend-Grundgerüst (Accounts, Foto-Upload, Matching zweier Nutzer)
- [ ] Blinde Bewertung (Community-Voting oder App-eigener Ästhetik-Score) zwischen
      zwei Fotos desselben Motivs
- [ ] Moderations-/Report-Mechanismus gegen Missbrauch

---

## 10. Phase 8 — Politur & Veröffentlichung (ca. 2-3 Wochen)

- [ ] Onboarding/Erklärung für Erstnutzer (Android)
- [ ] App-Icon, Store-Screenshots, Beschreibungstexte
- [ ] Play Console interner Test (Android) mit echten Testern
      *(TestFlight-Beta pausiert, Abschnitt 0)*
- [ ] Store-Einreichung Play Store *(App Store pausiert, Abschnitt 0)*

---

## 11. Offene technische Entscheidungen

- [ ] **Engpass Nr. 1 — Saliency auf Android.** Entweder ein Open-Source-Modell
      (U²-Netp, Apache-2.0, ~4,7 MB — Achtung: nur der allgemeine Checkpoint, nicht
      `u2net_portrait`, der ist über APDrawing nicht-kommerziell) oder ein klassisches
      Verfahren ohne Modell (Spectral Residual o. ä., pures Kotlin in `commonMain`).
      Durch Abschnitt 0 **vereinfacht**: Es muss zu keiner iOS-Semantik mehr passen.
      Blockiert `DeadSpaceRule` und macht `RuleOfThirdsRule` auf Android schwächer
      (die fällt ohne Saliency auf reine Gesichtserkennung zurück).
- [ ] Konkretes NIMA-artiges Ästhetik-Modell für Phase 2 finden/nachbauen
- [~] Minimal unterstützte OS-Versionen: Android minSdk 26 / targetSdk 35 / compileSdk 36
      festgelegt. iOS-Seite offen, aber pausiert (Abschnitt 0).
- [x] Datenschutz-Ansatz: **alles on-device**, als Verkaufsargument. Umgesetzt — die App
      fordert ab API 29 nur noch `CAMERA` an; transitiv hereingereichte Berechtigungen
      (`ACCESS_NETWORK_STATE`, Storage) sind im Manifest gezielt entfernt bzw. begrenzt.

## 12. Nur auf echter Hardware verifizierbar

Anlass: Am 27.08.2026 nachgemessen — **Visions Saliency-Modelle werten im iOS-Simulator
den Bildinhalt nicht aus.** Einfarbige Fläche, helles Rechteck und Streifenmuster mit
maximalem Kontrast liefern dieselbe Heatmap (68×68, max=0.36, identische Verteilung),
attention- wie objectness-basiert, über beide Eingabewege und unabhängig vom
IOSurface-Backing. Die Requests melden dabei Erfolg. Details im Kommentar in
`shared/src/iosTest/.../FrameAnalyzerTest.kt`.

Daraus folgt eine Liste von Dingen, die **kein grüner Testlauf jemals belegen kann**,
solange kein physisches Gerät im Spiel ist. Sie steht hier zusammen, weil sie sonst als
verstreute Code-Kommentare unsichtbar bleibt.

- [ ] **Saliency wertet den Bildinhalt aus.** Prüfung: dasselbe Motiv einmal links, einmal
      rechts im Bild — der Saliency-Schwerpunkt muss mitwandern. Bis dahin ist jede Aussage
      über die inhaltliche Richtigkeit von `readHeatmap` unbelegt.
- [ ] **Gesichtserkennung wertet den Bildinhalt aus (iOS).** Noch offen, weil der
      vorhandene Test nur den Negativfall prüft (`einfarbiges Bild → 0 Gesichter`) — den
      würde auch ein Detektor bestehen, der immer eine leere Liste liefert. Erster Schritt
      ist eine Gegenprobe im Simulator mit einem echten Foto als Test-Ressource
      („Foto → ≥1 Gesicht"); nur falls die scheitert, braucht es dafür ein Gerät.
      Solange das offen ist, ist unklar, ob im Simulator nur die Saliency betroffen ist.
- [ ] **Vorzeichen-Konvention des Horizonts auf iOS.** In `FrameAnalysis.kt` als
      „positiv = rechte Seite tiefer" festgelegt und aus der Rotationsrichtung von
      `VNHorizonObservation.angle` hergeleitet, aber nie an einem Gerät nachgesehen. Prüfung:
      Gerät kippen und den Wert beobachten, danach dieselbe Szene gegen Android gegenprüfen.
      Ein Vorzeichenfehler korrigiert den Nutzer exakt falsch herum.
- [ ] **Die drei Motiv-Schwellwerte kalibrieren** (`SALIENCY_THRESHOLD`,
      `MAX_SALIENT_COVERAGE`, `MIN_CLUSTER_SHARE` — siehe 3.2.1). Braucht 30–50 eigene
      Frames mit eigenem Urteil „hier ist ein Motiv / hier nicht". Setzt Punkt 1 voraus:
      ohne inhaltlich arbeitende Saliency gibt es nichts zu kalibrieren.
- [ ] **Latenz und Wärmeentwicklung der Vision-Requests** bei ~10 Hz über mehrere Minuten.
      Im Simulator bedeutungslos, weil dort weder die Neural Engine noch das reale
      Energiebudget beteiligt sind.

**Was CI davon abdecken kann: nichts davon.** CI läuft im Simulator. Abgedeckt sind dort
die Regel-Logik in `commonTest`, die reinen Funktionen (`visionBoxToFaceRect` und, sobald
herausgezogen, die Rasterlogik aus `readHeatmap`) sowie „die Verarbeitungskette hält" —
Buffer rein, `FrameAnalysis` raus, Koordinaten normiert, mehrfach aufrufbar. Eine grüne
Pipeline sagt über die inhaltliche Richtigkeit der iOS-Erkennung nichts aus. Das gehört so
in die CI-Beschreibung, damit später niemand mehr Sicherheit hineinliest, als drinsteckt.

**Entschieden am 22.09.2026 (Abschnitt 0): die iOS-Hälfte wird verschoben, Phase 1
schließt als Android-MVP ab.** Die Liste oben bleibt trotzdem stehen — sie ist die
Aufgabenliste für den Tag, an dem iOS zurückkommt, und jeder Punkt darauf ist ein
Fehlerrisiko, das bis dahin unentdeckt im Code liegt.

Die Punkte gelten weiterhin: Der iOS-Code ist gebaut und kompiliert, aber nichts davon ist
inhaltlich belegt. Wer ihn später anfasst, sollte nicht davon ausgehen, dass er
funktioniert, nur weil er grün durchläuft.

Nicht betroffen und ohne Gerät voll prüfbar: `SubjectResolver` und alle Regeln aus 3.2 —
die arbeiten auf `FrameAnalysis` und nicht auf Vision, und ihr primärer Motiv-Anker ist
ohnehin das Gesicht, nicht die Saliency.

---

## 13. Zeitschätzung gesamt

Angepasst auf Android-only (Abschnitt 0). Die alte Schätzung ging von zwei Plattformen aus;
eine Plattform weniger spart vor allem in Phase 1, 3.5 und 8.

| Phase | Umfang | Dauer | Stand |
|---|---|---|---|
| 0 — Setup | Grundgerüst, Kamera, Aufnahme (Android) | 1 Woche | ✅ fertig |
| 1 — MVP Regelbasiert | Kern-Feedback Android | 3-4 Wochen | 🚧 ~⅔ |
| 2 — Erklärbares Overlay + Score | | 2-3 Wochen | ⬜ |
| 3 — Textbasiertes Coaching | | 1-2 Wochen | ⬜ |
| 4 — Aktive Hinweise (Heuristik) | | 3-4 Wochen | ⬜ |
| 5 — Story-Coach | | 2-3 Wochen | ⬜ |
| 6 — Eigenes ML-Modell | optional | offen (Monate) | ⬜ |
| 7 — Duell-Modus | optional | offen | ⬜ |
| 8 — Politur & Release | nur Play Store | 2 Wochen | ⬜ |
| **Kern-Pfad (0-3+8)** | **differenzierter MVP für Android** | **~11-14 Wochen** | |
| *iOS nachziehen* | *Kamera, Anbindung, Gerätetest, App Store* | *+3-4 Wochen* | *⏸️ pausiert* |

**Was von Phase 1 noch fehlt:** Saliency auf Android (§11), `DeadSpaceRule`, der
0-100-Score, Grid-Overlay und Haptik (3.4), Gerätetest (3.5).

**Empfehlung unverändert:** Nach Phase 1 innehalten und ehrlich bewerten, ob sich
der Aufwand noch lohnt — das ist bereits ein kompletter, vorzeigbarer MVP.
Phase 2 (erklärbares Overlay) und Phase 3 (Textbasiertes Coaching) sind die Stellen, an
denen sich die App von der bestehenden Konkurrenz abhebt — dort lohnt sich
Sorgfalt am meisten.

## Content-Begleitung (unverändert vom letzten Plan)

Jede abgeschlossene Phase = ein Build-in-Public-Post fürs LinkedIn-Ziel, z. B.:
1. "Ich baue eine App mit Kotlin Multiplatform für iOS + Android gleichzeitig"
2. "Wie ich Kompositions-Feedback ganz ohne eigenes ML-Modell gebaut habe"
3. "Warum meine App jetzt auch blinde Nutzer beim Fotografieren unterstützt"
4. usw.
