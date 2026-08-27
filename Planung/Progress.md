# Progress.md — Implementierungsstand PhotoCoach

Stand: 27.08.2026. Diese Datei ist die **Kurzübersicht** "was ist fertig, was fehlt" —
Begründungen, Detail-Entscheidungen und technische Stolpersteine stehen weiterhin in
`CLAUDE.md` (Root) und in den anderen `Planung/*.md`-Dateien. Phasen-Nummerierung und
Definition-of-Done je Phase: siehe [Plan-zur-Umsetzung.md](./Plan-zur-Umsetzung.md).

**Pflege-Hinweis:** Diese Datei nach jedem abgeschlossenen Arbeitsschritt aktualisieren —
Status-Symbol setzen, "Nächste sinnvolle Schritte" neu ziehen. Nicht mit Prosa/Rationale
zumüllen, das gehört in CLAUDE.md.

---

## Phasen-Überblick

| Phase | Status | Kurznotiz |
|---|---|---|
| 0 — Projekt-Setup | ✅ Fertig | KMP-Grundgerüst, Kamera-Vorschau, Foto-Aufnahme, Datenfluss zur UI (Android) |
| 1 — MVP: Regelbasiertes Feedback | 🚧 In Arbeit | Sensorik teils echt (Android), Domain-Regeln noch nicht implementiert |
| 2 — Erklärbares Overlay + Score | ⬜ Nicht begonnen | |
| 3 — Textbasiertes Coaching | ⬜ Nicht begonnen | Konzept in CLAUDE.md/Umsetzbarkeit.md 6.1 festgehalten |
| 4 — Aktive Richtungsvorschläge | ⬜ Nicht begonnen | |
| 5 — Story-/Sequenz-Coach | ⬜ Nicht begonnen | |
| 6 — Eigenes ML-Modell | ⬜ Nicht begonnen (optional) | Nur falls Phase 4 nicht reicht |
| 7 — Duell-Modus | ⬜ Nicht begonnen (optional) | |
| 8 — Politur & Release | ⬜ Nicht begonnen | |

---

## Phase 0 — Projekt-Setup ✅

- [x] KMP-Projekt (`shared`, `androidApp`, `iosApp`) aufgesetzt
- [x] `FrameAnalyzer`/`CameraFrame` als expect/actual-Naht angelegt
- [x] Kamera-Vorschau Android (CameraX `Preview`), verifiziert im Emulator
- [x] Foto-Aufnahme Android (`ImageCapture`, Speicherung in MediaStore-Galerie), verifiziert
- [x] iOS-Targets aktiv, App läuft im Simulator (Kamera-Berechtigung geprüft; echte
      Preview/Aufnahme erst mit echtem iPhone möglich, Simulator hat keine Kamera)
- [x] Datenfluss Kamera → `FrameAnalyzer` → UI steht (Android), inkl. Debug-Badge

**Definition of Done erfüllt** (Android vollständig, iOS eingeschränkt auf "läuft im Simulator").

---

## Phase 1 — MVP: Regelbasiertes Kompositions-Feedback 🚧

### Was steht
- [x] Android: ML Kit Face Detection — **echt**, on-device, FAST-Modus
- [x] Android: Horizont-Neigung — **echt**, aus Schwerkraftsensor (`HorizonSensor.kt`)
- [x] Android: `ImageAnalysis`-Pipeline, gedrosselt auf ~10 Hz
- [x] `FrameAnalyzer.close()` zur Ressourcenfreigabe (ML-Kit-Detektor)
- [x] `RuleOfThirdsRule` bis ins Detail durchgeplant (Motivbestimmung, Geometrie,
      Score-Kennlinie, Hysterese, Hinweistext) — siehe Plan-zur-Umsetzung.md §3.2.1

### Was fehlt
- [ ] **`domain/rules/`, `domain/scoring/`, `domain/coaching/`, `domain/subject/`,
      `domain/geometry/` existieren im Code noch nicht** — der Plan aus §3.2.1 ist
      geschrieben, aber noch nicht umgesetzt. Das ist aktuell der größte Rückstand.
- [ ] `aspectRatio`-Feld fehlt noch in `FrameAnalysis` (Voraussetzung für Schritt 1 der
      Rule-of-Thirds-Umsetzung)
- [ ] `saliencyRegions` auf Android leer — hängt an offener Modellentscheidung
      (Plan §11: welches TFLite-Saliency-Modell)
- [ ] iOS `FrameAnalyzer` komplett Dummy — Vision-Framework-Requests
      (`VNDetectHorizonRequest`, `VNGenerateAttentionBasedSaliencyImageRequest`,
      `VNDetectFaceRectanglesRequest`) noch nicht implementiert; braucht echtes iPhone
      zum Testen (keine Kamera im Simulator)
- [ ] `HorizonRule`, `DeadSpaceRule`, `PortraitFramingRule`, `CompositionScorer` — nicht
      begonnen
- [ ] UI: `ScoreOverlay`, ein-/ausblendbares Grid-Overlay, dezenter Ton/Haptik-Trigger —
      aktuell nur provisorisches `AnalysisDebugBadge` (Frame-Zähler + Zeitstempel)
- [ ] Testing mit 50-100 eigenen Testfotos je Plattform — nicht begonnen
- [ ] Performance-Test auf älterem Testgerät — nicht begonnen

### Bekannte Bugs / Lücken
- Foto-Rotation: `imageCapture.targetRotation` wird nur beim Binden gesetzt — dreht der
  Nutzer das Gerät während die App läuft, wird das Foto falsch herum gespeichert
  (braucht `OrientationEventListener`)
- Horizont-Erkennung deckt nur "Gerät schief gehalten" ab, nicht "Gerät gerade, Horizont
  im Bild schief" (akzeptierte v1-Einschränkung)
- Gesichtserkennung + Neigungswert sind noch nicht live auf Gerät/Emulator verifiziert
  (Build und Unit-Tests grün, aber visuell noch nicht gesehen — Emulator hing beim letzten
  Testversuch)

---

## Phase 2 — Erklärbares Overlay & Nach-Aufnahme-Score ⬜

Noch nicht begonnen. Braucht `saliencyRegions` aus Phase 1 als Voraussetzung.

---

## Phase 3 — Textbasiertes Coaching ⬜

Noch nicht begonnen. Konzept und Formulierungsrichtlinien bereits festgelegt (siehe
CLAUDE.md "Project concept" und Umsetzbarkeit.md 6.1) — Audio-/Haptik-Ausgabe für
blinde/sehbehinderte Nutzer ist als spätere Ausbaustufe zurückgestellt, nicht Teil dieser
Phase.

---

## Phase 4-8 ⬜

Noch nicht begonnen, keine Vorarbeiten.

---

## Offene technische Entscheidungen (Plan §11, vor Vollendung Phase 1 zu klären)

- [ ] Konkretes Open-Source-Saliency-Modell für Android (Lizenz + Mobile-Performance prüfen)
- [ ] Konkretes NIMA-artiges Ästhetik-Modell für Phase 2
- [ ] Minimal unterstützte OS-Versionen final festlegen
- [ ] Datenschutz-Ansatz: vollständig on-device vs. optionaler Cloud-Sync (Galerie/Duell-Modus)

---

## Nächste sinnvolle Schritte

1. `RuleOfThirdsRule` gemäß Plan §3.2.1 umsetzen (Schritte 1-6, ~4,5 Tage veranschlagt) —
   vollständig durchgeplant, jetzt reine Umsetzung
2. Android-Saliency-Modell auswählen (blockiert vollständige Motiverkennung + Phase 2)
3. Rotation-Bug bei Foto-Aufnahme fixen (`OrientationEventListener`)
4. Gesichtserkennung/Neigung einmal live am Emulator/Gerät verifizieren
5. iOS `FrameAnalyzer` echte Vision-Implementierung — sobald ein echtes iPhone zum
   Testen verfügbar ist
