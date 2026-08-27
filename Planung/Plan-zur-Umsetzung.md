# Plan zur Umsetzung: App für Bildkomposition & Kamerawinkel

Status: **Review-Entwurf**, Stand 22.08.2026
Basiert auf [Umsetzbarkeit.md](./Umsetzbarkeit.md) (inkl. Feature-Lücken-Analyse Abschnitt 6)
und der Tech-Stack-Entscheidung für Compose Multiplatform.

---

## 1. Tech-Stack & Architektur-Überblick

**Sprache/Framework:** Kotlin Multiplatform (KMP) + Compose Multiplatform, Targets: iOS + Android
(CMP für iOS seit Version 1.8.0 / Mai 2025 stabil & production-ready).

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

- [ ] KMP-Projekt aufsetzen (`shared`, `iosApp`, `androidApp`) via Kotlin Multiplatform Wizard
- [ ] `FrameAnalyzer`-Interface (expect/actual) mit leeren/dummy `actual`-Implementierungen
      auf beiden Plattformen — Ziel: durchgängiger Daten-Fluss von Kamera bis UI steht,
      auch wenn Analyse noch nichts liefert
- [ ] Kamera-Grundfunktion iOS: AVCaptureSession, Live-Preview in Compose einbetten
      (`UIKitView`-Interop)
- [ ] Kamera-Grundfunktion Android: CameraX `Preview` + `ImageAnalysis` Use Cases
- [ ] Git-Repo (privat), CI-Grundgerüst (GitHub Actions: Build für beide Targets bei jedem Push)
- [ ] LiveCapture-Repo (github.com/LiveCompose/LiveCapture) lokal klonen, Architektur als
      Referenz für spätere Phase 4 durchlesen

**Definition of Done:** App startet auf beiden Plattformen, zeigt Kamera-Live-Bild,
kann ein Foto auslösen und lokal speichern.

---

## 3. Phase 1 — MVP: Regelbasiertes Kompositions-Feedback (ca. 4-5 Wochen)

### 3.1 iOS-Implementierung von `FrameAnalyzer`
- [ ] `VNDetectHorizonRequest` pro Frame (throttled auf ca. 5-10 Hz, nicht jeden Frame —
      Akku-/Performance-Grund)
- [ ] `VNGenerateAttentionBasedSaliencyImageRequest` → Saliency-Heatmap in normierte
      Punkte/Bounding-Boxes umwandeln
- [ ] `VNDetectFaceRectanglesRequest` für Gesichtspositionen
- [ ] Ergebnisse in `FrameAnalysis`-Datenklasse mappen, an `commonMain` zurückgeben

### 3.2 Domain-Logik (geteilt, `commonMain`)
- [ ] `RuleOfThirdsRule`: Abstand der größten Saliency-Region zu den 4 Schnittpunkten
      berechnen, Score + Richtungshinweis ("Motiv 12% zu weit links von der optimalen
      Position")
- [x] `HorizonRule`: Warnung bei `horizonTiltDegrees` > 2°
- [ ] `DeadSpaceRule`: Anteil des Bilds ohne Saliency pro Bildhälfte vergleichen,
      Warnung bei starker Asymmetrie ohne erkennbaren Grund (z. B. Blickrichtung)
- [ ] `PortraitFramingRule`: bei erkanntem Gesicht — Kopf nicht zu weit oben/unten
      abgeschnitten, ausreichend Blickraum in Blickrichtung (Blickrichtung grob aus
      Gesichts-Bounding-Box-Position relativ zu Bildmitte geschätzt)
- [ ] `CompositionScorer`: kombiniert alle Regel-Ergebnisse zu einem einzigen
      0-100-Live-Score + priorisierter Liste an Hinweisen (nur 1 Hinweis gleichzeitig
      anzeigen, wichtigsten zuerst — sonst Overload)

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

- [ ] `aspectRatio: Float` (Breite/Höhe des aufrecht gedrehten Analyse-Frames) in
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

- [ ] `DefaultSubjectResolver` implementieren, Reihenfolge:
      1. **Gesicht vorhanden** → Mittelpunkt des flächengrößten `FaceRect`, `confidence = 1.0`
      2. **Sonst Saliency** → stärksten `SaliencyPoint` nehmen, dann den gewichteten
         Schwerpunkt aller Punkte im Umkreis von 0,15 um ihn herum bilden
      3. **Sonst** `null`
- [ ] Bewusst **kein** Schwerpunkt über alle Saliency-Punkte: bei zwei Motiven links und
      rechts läge der Schwerpunkt genau in der Mitte — die Regel würde ein Motiv melden,
      das es gar nicht gibt. Der Umkreis-Filter verhindert das.

Diese Zwischenschicht ist zugleich die Stelle, an der die unterschiedliche Semantik der
beiden Plattformen (Apples Attention-Saliency vs. ein Segmentierungsmodell auf Android)
auf **eine** definierte Bedeutung gebracht wird: „geschätzter Mittelpunkt des Hauptmotivs".

**Schritt 3 — Geometrie (`domain/geometry/Thirds.kt`)**

- [ ] Die vier Schnittpunkte als Konstanten: (⅓,⅓), (⅔,⅓), (⅓,⅔), (⅔,⅔)
- [ ] Seitenverhältnis-korrigierte Distanz, gerechnet in Einheiten der **Bildbreite**:

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

- [ ] `NotApplicable` ≠ Score 0 — ohne erkanntes Motiv hat die Regel *keine* Meinung.
      Ein 0-Score würde den Gesamtscore fälschlich nach unten ziehen und den Nutzer
      für etwas rügen, das die App schlicht nicht sehen kann.
- [ ] Score-Kennlinie: Distanz 0 → 1.0; ab 0,04 beginnt der Abfall; bei 0,167
      (= Abstand Bildmitte ↔ Drittel-Linie) → 0.0. Dazwischen `smoothstep` statt linear,
      damit kleine Wackler nahe am Optimum den Score nicht sichtbar zappeln lassen.
- [ ] Die Mitte ist **kein** Fehler: Ein mittig platziertes Motiv landet bei Score ≈ 0,
      aber die Regel bekommt im `CompositionScorer` nur mittleres Gewicht — Symmetrie ist
      eine legitime Bildsprache, keine Regelverletzung.
- [ ] `RuleDetail` trägt Motivpunkt, gewählten Zielpunkt und den Versatz mit — Phase 2
      (erklärbares Overlay) und Phase 4 (Richtungspfeil) zeichnen genau daraus.

**Schritt 5 — Zielpunkt stabilisieren**

- [ ] Nächstgelegenen der vier Punkte wählen — aber **mit Hysterese**: der bisher gewählte
      Zielpunkt bleibt gültig, solange kein anderer mindestens 15 % näher liegt

Ohne das kippt die Wahl bei einem Motiv nahe der Bildmitte zwischen zwei fast gleich weit
entfernten Punkten hin und her, und der Hinweis springt im Sekundentakt zwischen „links"
und „rechts". Der Zustand gehört **nicht** in die Regel (die bleibt eine reine Funktion),
sondern in einen `ThirdsTargetTracker`, den die aufrufende Schicht über Frames hinweg hält.

**Schritt 6 — Hinweistext (`domain/coaching/`)**

- [ ] Formulierung **beschreibend**, nicht anweisend: „Motiv sitzt 12 % links vom
      Drittelpunkt" — Versatz in Prozent der Bildbreite, gerundet
- [ ] Unter 5 % Versatz keinen Hinweis ausgeben (das ist innerhalb der Messungenauigkeit
      der Motivbestimmung)

⚠️ **Falle für Phase 4:** Handlungsanweisungen sind *umgekehrt* zum Versatz. Sitzt das Motiv
zu weit links im Bild, muss die Kamera nach **links** geschwenkt werden, damit das Motiv im
Bild nach rechts wandert. In Phase 1 wird deshalb bewusst nur beschrieben, nicht angewiesen —
die Umkehrung gehört zusammen mit dem Pfeil-Indikator in Phase 4 und will dort einmal
sauber durchdacht und getestet werden.

**Testfälle (`commonTest`, Bausteine liegen in `TestFrames.kt` bereit)**

- [ ] Motiv exakt auf (⅓,⅓) → Score 1,0
- [ ] Motiv exakt in der Bildmitte → Score ≈ 0, kein Absturz, `Evaluated` (nicht `NotApplicable`)
- [ ] Kein Gesicht, keine Saliency → `NotApplicable`
- [ ] Gesicht **und** Saliency vorhanden, an verschiedenen Stellen → Gesicht gewinnt
- [ ] Zwei Saliency-Punkte links und rechts → Motivpunkt landet auf einem der beiden,
      **nicht** in der Mitte
- [ ] Gleicher normierter Versatz in Hoch- und Querformat → unterschiedlicher Score
      (belegt, dass `aspectRatio` wirkt)
- [ ] Motiv wandert langsam über die Bildmitte → Zielpunkt wechselt **einmal**, nicht mehrfach
- [ ] Versatz 3 % → kein Hinweistext; Versatz 12 % → Text nennt „12 %"

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

### 3.3 Android-Implementierung von `FrameAnalyzer`
- [ ] CameraX `ImageAnalysis`-Pipeline für Frame-Zugriff
- [ ] ML Kit Face Detection für `faces`
- [ ] Horizont: `SensorManager` (Accelerometer + Rotation Vector) statt Bildanalyse —
      deckt den Hauptfall (Gerät schief gehalten) ab, deckt NICHT den Fall "Gerät gerade,
      Horizont im Bild schief" ab (siehe Umsetzbarkeit.md, akzeptierte Einschränkung für v1)
- [ ] Saliency: leichtes offenes TFLite-Modell (z. B. U2Net-lite oder vergleichbar,
      < 5 MB, quantisiert) einbinden — **Rechercheaufgabe:** konkretes Modell mit
      passender Lizenz und Mobile-Performance evaluieren, bevor Implementierung startet
- [ ] Gleiche `FrameAnalysis`-Datenklasse befüllen wie auf iOS

### 3.4 UI (Compose, geteilt)
- [ ] `ScoreOverlay`: nicht-invasives Feedback am Bildschirmrand (Text + Icon), kein
      Overlay über dem eigentlichen Sucherbild
- [ ] Grid-Overlay (Drittel-Regel) ein-/ausblendbar
- [ ] Ton/Haptik-Trigger bei Score-Sprung (dezent, kein Dauer-Feedback)

### 3.5 Testing
- [ ] 50-100 eigene Testfotos je Plattform, manuelle Bewertung ob Feedback korrekt/sinnvoll
- [ ] Performance-Test: Frame-Analyse-Latenz auf älterem Testgerät (nicht nur Flaggschiff)

**Definition of Done:** Auf beiden Plattformen lauffähiger MVP mit korrektem,
nachvollziehbarem Live-Feedback zu Horizont, Drittel-Regel und toter Fläche.

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

- [ ] Onboarding/Erklärung für Erstnutzer (beide Plattformen)
- [ ] App-Icon, Store-Screenshots, Beschreibungstexte
- [ ] TestFlight-Beta (iOS) + Play Console interner Test (Android) mit echten Testern
- [ ] Store-Einreichung beider Plattformen

---

## 11. Offene technische Entscheidungen (vor Phase 1 zu klären)

- [ ] Konkretes Open-Source-Saliency-Modell für Android auswählen (Lizenz + Performance
      prüfen, bevor Zeit investiert wird)
- [ ] Konkretes NIMA-artiges Ästhetik-Modell für Phase 2 finden/nachbauen
- [ ] Minimal unterstützte OS-Versionen festlegen (beeinflusst z. B. Verfügbarkeit
      neuerer Vision-/ML-Kit-APIs)
- [ ] Datenschutz-Ansatz festlegen: alles on-device (wie SnapFrame wirbt) als
      Verkaufsargument, oder optionaler Cloud-Sync für Galerie/Duell-Modus?

## 12. Zeitschätzung gesamt

| Phase | Umfang | Dauer |
|---|---|---|
| 0 — Setup | Grundgerüst beide Plattformen | 1 Woche |
| 1 — MVP Regelbasiert | Kern-Feedback beide Plattformen | 4-5 Wochen |
| 2 — Erklärbares Overlay + Score | | 3 Wochen |
| 3 — Textbasiertes Coaching | | 1-2 Wochen |
| 4 — Aktive Hinweise (Heuristik) | | 3-4 Wochen |
| 5 — Story-Coach | | 2-3 Wochen |
| 6 — Eigenes ML-Modell | optional | offen (Monate) |
| 7 — Duell-Modus | optional | offen |
| 8 — Politur & Release | | 2-3 Wochen |
| **Kern-Pfad (0-3+8, ohne Optionales)** | **echter, differenzierter MVP für beide Plattformen** | **~14-17 Wochen** |

**Empfehlung unverändert:** Nach Phase 1 innehalten und ehrlich bewerten, ob sich
der Aufwand für dich noch lohnt — das ist bereits ein kompletter, vorzeigbarer MVP.
Phase 2 (erklärbares Overlay) und Phase 3 (Textbasiertes Coaching) sind die Stellen, an
denen sich die App von der bestehenden Konkurrenz abhebt — dort lohnt sich
Sorgfalt am meisten.

## Content-Begleitung (unverändert vom letzten Plan)

Jede abgeschlossene Phase = ein Build-in-Public-Post fürs LinkedIn-Ziel, z. B.:
1. "Ich baue eine App mit Kotlin Multiplatform für iOS + Android gleichzeitig"
2. "Wie ich Kompositions-Feedback ganz ohne eigenes ML-Modell gebaut habe"
3. "Warum meine App jetzt auch blinde Nutzer beim Fotografieren unterstützt"
4. usw.
