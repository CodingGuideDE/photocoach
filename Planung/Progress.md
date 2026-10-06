# Progress.md — Implementierungsstand PhotoCoach

Stand: 22.09.2026. **Plattform-Fokus: Android — iOS pausiert**
(siehe [Plan-zur-Umsetzung.md](./Plan-zur-Umsetzung.md) Abschnitt 0).
Alle Angaben unten beziehen sich auf Android, sofern nicht anders vermerkt.

Diese Datei ist die **Kurzübersicht** "was ist fertig, was fehlt" —
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
| 0 — Projekt-Setup | ✅ Fertig (Android) | KMP-Grundgerüst, Kamera-Vorschau, Foto-Aufnahme, Datenfluss zur UI |
| 1 — MVP: Regelbasiertes Feedback | 🚧 ~⅘ | Alle 4 Regeln + 0-100-Score + Saliency (Android) fertig. Offen: Gerätetest |
| 2 — Erklärbares Overlay + Score | ⬜ Nicht begonnen | Braucht Saliency |
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
- [x] Datenfluss Kamera → `FrameAnalyzer` → UI steht, inkl. Debug-Badge
- [~] iOS-Targets aktiv, App läuft im Simulator und zeigt die geteilte UI — **kein
      Kamerabild**. ⏸️ pausiert, Abschnitt 0.

**Definition of Done für Android erfüllt.** Der iOS-Anteil ist bewusst verschoben.

---

## Phase 1 — MVP: Regelbasiertes Kompositions-Feedback 🚧 (~⅔)

### Sensorik (Android) — `FrameAnalysis`
- [x] `ImageAnalysis`-Pipeline, `KEEP_ONLY_LATEST`, eigener Thread, ~10 Hz
- [x] `faces` — **echt**, ML Kit on-device, gebündeltes Modell, FAST-Modus
- [x] `horizonTiltDegrees` — **echt**, Schwerkraftsensor (`HorizonSensor.kt`),
      gegen Display-Rotation normalisiert, Winkelmathematik unit-getestet
- [x] `aspectRatio` — befüllt aus `uprightWidth/uprightHeight`
- [x] `saliencyRegions` — **echt**, Spectral Residual (modellfrei, commonMain), 64×64 aus der
      Y-Ebene → 12×12-Raster wie iOS. Qualität an echten Szenen noch ungeprüft

### Domain-Logik (`commonMain`, plattformneutral)
- [x] `domain/geometry/Thirds` — Drittel-Linien und -Schnittpunkte, seitenverhältnis-korrigiert
- [x] `domain/subject/SubjectResolver` — Motivpunkt aus Gesicht (bevorzugt) oder Saliency
- [x] `domain/rules/HorizonRule`
- [x] `domain/rules/PortraitFramingRule`
- [x] `domain/rules/RuleOfThirdsRule` + `ThirdsTargetTracker` (Hysterese gegen Zielspringen)
- [x] `domain/rules/DeadSpaceRule` — leere Bildhälfte ohne Drittel-Begründung
- [x] `domain/scoring/CompositionScorer` — **ein** Hinweis (Rangfolge nach Reparierbarkeit:
      Rahmung → Horizont → Drittel → tote Fläche) **und 0-100-Score** (gewichteter Mittelwert
      der `ScoredRule`-Scores). Ohne Motiv: kein Score, „Kein klares Motiv erkannt"
- [x] `domain/scoring/VerdictStabilizer` — 1,8 s Mindestanzeigezeit für *jeden* Zustand
- [x] `domain/scoring/ScoreSmoother` — Glättung der Zahl (EMA, 500 ms)
- [~] `PortraitFramingRule` — Blickraum-Prüfung war toter Code, ersetzt durch seitlichen
      Anschnitt. Echter Blickraum braucht Kopfdrehung (ML Kit), Vorzeichen erst am Gerät prüfen

### UI (`commonMain`)
- [x] `ScoreOverlay` — Leiste oben, Symbol + Text, ein Hinweis, im Emulator verifiziert
      (Ruhe- und Warnzustand)
- [x] Grid-Overlay (Drittel-Regel), ein-/ausblendbar
- [x] ScoreOverlay zeigt Score-Zahl + drei Zustände (Hinweis / passt / kein Motiv)
- [ ] Dezenter Ton-/Haptik-Trigger
- [~] `AnalysisDebugBadge` zeigt nur noch Rohdaten — fällt nach dem Gerätetest weg

### Testing
- [x] 154 Unit-Tests, grün auf Android **und** im iOS-Simulator
- [ ] **50-100 eigene Testfotos auf echtem Android-Gerät** — nicht begonnen, wichtigster
      offener Schritt für die Phase-1-DoD
- [ ] Latenz- und Wärmemessung auf schwachem Gerät

### iOS ⏸️
- [~] `FrameAnalyzer.ios.kt` — alle drei Vision-Requests gebaut, **nicht angeschlossen**
      (keine `AVCaptureVideoDataOutput`, `analyze()` läuft nur in Tests) und inhaltlich
      unverifiziert (Plan §12). Keine weitere Arbeit, Abschnitt 0.

### Bekannte Bugs / Lücken
- Foto-Rotation: `imageCapture.targetRotation` wird nur beim Binden gesetzt — dreht der
  Nutzer das Gerät während die App läuft, wird das Foto falsch herum gespeichert
  (braucht `OrientationEventListener`)
- Horizont-Erkennung deckt nur "Gerät schief gehalten" ab, nicht "Gerät gerade, Horizont
  im Bild schief" (akzeptierte v1-Einschränkung)
- Blickraum im Porträt fehlt (siehe oben) — braucht verifizierte Kopfdrehung
- Horizont: im Emulator −180° Neigung bei aufrechter Anzeige beobachtet; `HorizonRule`
  ignoriert jetzt > 45°, die Sensor-Ursache ist beim Gerätetest zu klären

### Nächste sinnvolle Schritte
1. Gerätetest auf echtem Android-Gerät (3.5) — Saliency-Qualität, Schwellwerte
   (`DeadSpaceRule.EMPTY_SHARE`, Score-Gewichte) und Horizont-Vorzeichen kalibrieren
2. Kopfdrehung (ML Kit `headEulerAngleY`) am Gerät loggen → Blickraum-Regel zurück
3. Dezenter Haptik-Trigger bei Score-Sprung (3.4)

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
