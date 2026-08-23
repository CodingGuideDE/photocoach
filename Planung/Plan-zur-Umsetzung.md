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
- [ ] `HorizonRule`: Warnung bei `horizonTiltDegrees` > 2°
- [ ] `DeadSpaceRule`: Anteil des Bilds ohne Saliency pro Bildhälfte vergleichen,
      Warnung bei starker Asymmetrie ohne erkennbaren Grund (z. B. Blickrichtung)
- [ ] `PortraitFramingRule`: bei erkanntem Gesicht — Kopf nicht zu weit oben/unten
      abgeschnitten, ausreichend Blickraum in Blickrichtung (Blickrichtung grob aus
      Gesichts-Bounding-Box-Position relativ zu Bildmitte geschätzt)
- [ ] `CompositionScorer`: kombiniert alle Regel-Ergebnisse zu einem einzigen
      0-100-Live-Score + priorisierter Liste an Hinweisen (nur 1 Hinweis gleichzeitig
      anzeigen, wichtigsten zuerst — sonst Overload)

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
| 3 — Barrierefreiheit | | 2-3 Wochen |
| 4 — Aktive Hinweise (Heuristik) | | 3-4 Wochen |
| 5 — Story-Coach | | 2-3 Wochen |
| 6 — Eigenes ML-Modell | optional | offen (Monate) |
| 7 — Duell-Modus | optional | offen |
| 8 — Politur & Release | | 2-3 Wochen |
| **Kern-Pfad (0-3+8, ohne Optionales)** | **echter, differenzierter MVP für beide Plattformen** | **~15-18 Wochen** |

**Empfehlung unverändert:** Nach Phase 1 innehalten und ehrlich bewerten, ob sich
der Aufwand für dich noch lohnt — das ist bereits ein kompletter, vorzeigbarer MVP.
Phase 2 (erklärbares Overlay) und Phase 3 (Barrierefreiheit) sind die Stellen, an
denen sich die App von der bestehenden Konkurrenz abhebt — dort lohnt sich
Sorgfalt am meisten.

## Content-Begleitung (unverändert vom letzten Plan)

Jede abgeschlossene Phase = ein Build-in-Public-Post fürs LinkedIn-Ziel, z. B.:
1. "Ich baue eine App mit Kotlin Multiplatform für iOS + Android gleichzeitig"
2. "Wie ich Kompositions-Feedback ganz ohne eigenes ML-Modell gebaut habe"
3. "Warum meine App jetzt auch blinde Nutzer beim Fotografieren unterstützt"
4. usw.
