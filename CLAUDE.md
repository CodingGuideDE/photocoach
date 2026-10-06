# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Pflegehinweis (verbindlich)

Diese Datei nach jeder abgeschlossenen Aufgabe aktualisieren — neuer Stand unter
"Repository status" ergänzen, nicht nur im Kopf behalten oder nur in `Planung/Progress.md`
festhalten. Neu entdeckte Bugs/Lücken gehören ebenfalls sofort hier rein (siehe bestehende
"Bekannte Luecke"-Einträge als Vorbild), auch wenn sie noch nicht behoben sind.

## Plattform-Fokus: Android (iOS pausiert, 22.09.2026)

**Neue Funktionalität wird nur auf Android gebaut.** Begründung und Bedingungen für das
Wiederaufnehmen: `Planung/Plan-zur-Umsetzung.md` Abschnitt 0.

Was das fuer die Arbeit hier heisst:

- **Keine neuen iOS-Features.** `AVCaptureSession`, Preview und Aufnahme auf iOS bleiben
  liegen. `FrameAnalyzer.ios.kt` ist gebaut, aber nicht angeschlossen und inhaltlich
  unverifiziert — nicht darauf aufbauen.
- **Der iOS-Code muss weiter kompilieren.** Aendert sich etwas in `commonMain` (neues Feld
  in `FrameAnalysis`, neue Methode im `expect`), das iOS-`actual` mitziehen. Das kostet
  jetzt Minuten und spaeter Tage.
- **Die `expect`/`actual`-Naht und `commonMain` bleiben plattformneutral.** Sie sind der
  Grund, warum die Pause billig ist — Logik und UI gehoeren weiterhin dorthin, nicht nach
  `androidMain`.
- **Vor dem Commit `./gradlew :shared:allTests`** (schliesst die iOS-Simulator-Tests ein).
  Im Alltag reicht `:shared:testDebugUnitTest`, das spart den iOS-Compile.

## Repository status

Phase 0 (Projekt-Setup) ist angelegt: KMP-Gerueststruktur (`shared/`, `androidApp/`),
`FrameAnalyzer` als `expect`/`actual` mit Dummy-Implementierung auf Android.

**Kamera-Vorschau (Android) steht.** CameraX `Preview`-Use-Case ueber die
`expect`/`actual`-Naht `ui/CameraPreview.kt` (commonMain) → `ui/CameraPreview.android.kt`
(androidMain), Statusdarstellung geteilt in `ui/CameraScreen.kt`. Verifiziert im Emulator:
Berechtigungsdialog, Kamera bindet, Live-Bild sichtbar.

**Foto-Aufnahme (Android) steht — Phase-0-DoD fuer Android damit erfuellt.**
`ImageCapture`-Use-Case (`CAPTURE_MODE_MINIMIZE_LATENCY`) im selben `actual`; Speichern
ueber den MediaStore nach `Pictures/PhotoCoach`, also in die Galerie des Nutzers und nicht
in den App-Ordner — die Fotos sollen eine Deinstallation ueberleben und teilbar sein.
Ausloeser und Rueckmeldung liegen in `CameraScreen` (commonMain), gelten also spaeter fuer
iOS unveraendert.

Design-Prinzip der Naht: **jeder `CameraState` traegt die Aktionen, die in ihm moeglich
sind** — `PermissionRequired.requestPermission`, `Running.takePhoto`. Die Mechanik ist
plattformspezifisch, Texte/Layout/Buttons liegen in commonMain.

Im Emulator verifiziert: 1 Tap = 1 Foto, Datei im MediaStore vorhanden, drei schnelle Taps
erzeugen nur eine Aufnahme (Guard ueber `capturing`, Button wird sichtbar ausgegraut).

Bekannte Luecke: `imageCapture.targetRotation` wird nur beim Binden gesetzt. Dreht der
Nutzer das Geraet waehrend die App laeuft, wird das Foto falsch herum gespeichert — braucht
einen `OrientationEventListener`.

**Datenfluss Kamera -> FrameAnalyzer -> UI steht (Android).** `ImageAnalysis`-Use-Case
(`STRATEGY_KEEP_ONLY_LATEST`, eigener Executor, auf ~10 Hz gedrosselt) liegt in
`ui/CameraPreview.android.kt` und schickt jedes Frame als `CameraFrame` — jetzt ein duenner
Wrapper um `ImageProxy` — durch den `FrameAnalyzer`. Ergebnis geht ueber den neuen
`onAnalysis`-Callback der `CameraPreview`-Naht auf dem Main-Thread nach oben.
Der Analyzer selbst bleibt bewusst ein Dummy: er uebernimmt nur den echten Frame-Zeitstempel,
`horizonTiltDegrees`/`saliencyRegions`/`faces` bleiben leer, bis Phase 1 sie befuellt.
`CameraScreen` zeigt das als provisorisches `AnalysisDebugBadge` (Frame-Zaehler +
Zeitstempel) — faellt weg, sobald dort das echte ScoreOverlay haengt.
Im Emulator verifiziert: Zaehler laeuft hoch, Zeitstempel wandert mit, kein Crash.

Wichtig fuer Phase 1: das `ImageProxy` in `CameraFrame` ist nur waehrend des
`analyze()`-Aufrufs gueltig — der Aufrufer schliesst es danach. Nichts daraus ueber den
Aufruf hinaus festhalten.

Berechtigungen werden in `shared/src/androidMain/AndroidManifest.xml` deklariert (nicht in
androidApp) — dort liegt der Kamera-Code. Diese Datei haelt den Berechtigungs-Satz bewusst
klein, was zum on-device-Versprechen der App gehoert:
- `ACCESS_NETWORK_STATE` (kommt transitiv ueber `camera-view` → `androidx.media3`) wird per
  `tools:node="remove"` entfernt
- `WRITE_EXTERNAL_STORAGE` ist auf `maxSdkVersion=28` begrenzt (ab API 29 schreibt der
  MediaStore ohne Berechtigung)
- `READ_EXTERNAL_STORAGE` fuegt der Manifest-Merger automatisch hinzu, sobald WRITE
  deklariert ist — und zwar OHNE Grenze. Deshalb hier explizit ebenfalls auf
  `maxSdkVersion=28` gesetzt, sonst stuende auf allen Geraeten ein Zugriff auf die
  gesamte Fotobibliothek im Play-Store-Eintrag.

Ergebnis nach dem Merge: auf Geraeten ab API 29 fordert die App nur noch CAMERA an. Wer
hier Dependencies ergaenzt, sollte das gemergte Manifest gegenpruefen
(`androidApp/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml`).

**iOS-Targets sind aktiv, App laeuft im Simulator** (verifiziert: iPhone 17, iOS 26.5).
`CameraPreview.ios.kt` prueft die Kamera-Berechtigung echt (Systemdialog getestet), erkennt
mangels Kamera-Hardware im Simulator korrekt "keine Kamera gefunden" — `AVCaptureSession`/
Preview/Aufnahme folgen erst mit einem echten iPhone zum Testen (Phase 1). Details, inkl.
dreier echter Bugs, die dabei auftraten und wie sie behoben wurden (Compose Multiplatform
1.11.1 KLIB-Resolver-Bug, iosX64 nicht mehr veroeffentlicht, PlistSanityCheck-Crash ohne
`CADisableMinimumFrameDurationOnPhone`), stehen in `shared/src/iosMain/README.md`.

**Tests.** `./gradlew :shared:allTests` — laeuft auf Android UND im iOS-Simulator.
- `shared/src/commonTest/.../domain/TestFrames.kt` — Fixtures, um `FrameAnalysis`-Situationen
  zu beschreiben (`frame(...)`, `faceAt(...)`, `subjectAt(...)`, Konstanten `THIRD_LEFT` etc.).
  **Regeln in `domain/rules/` gehoeren hierueber getestet, nicht am Emulator** — sie sind pure
  Funktionen von `FrameAnalysis` auf einen Hinweis.
- `shared/src/commonTest/.../domain/rules/` — je Regel ein Test (Horizont, Rahmung, Drittel,
  tote Flaeche), `domain/scoring/` fuer Scorer, Stabilisierung und Glaettung,
  `capture/saliency/` fuer Spectral Residual auf synthetischen Bildern. Neue Regel =
  `ScoredRule` implementieren + im `CompositionScorer` einhaengen (Rangfolge und Gewicht).
- `shared/src/androidUnitTest/.../capture/HorizonSensorTest.kt` — Winkel-Mathematik der
  Horizont-Erkennung. Achtung bei Aenderungen: `Display.getRotation()` meldet die Drehung der
  Grafik, nicht des Geraets, beides ist gegenlaeufig. Die Konvention steht als Kommentar im
  Test — sie war beim Schreiben schon einmal vertauscht.
- Die vormals bekannt roten `FrameAnalyzerTest`-Faelle zur Saliency-Position auf
  `iosSimulatorArm64` sind behoben (durch Entfernen der im Simulator nicht verifizierbaren
  Positions-Assertions, siehe Kommentar dort und `iosMain/README.md`) — alle Tests gruen.

**Stand `FrameAnalysis`:** `faces` (Android: ML Kit, iOS: Vision) und `horizonTiltDegrees`
(Android: Schwerkraft-Sensor, iOS: `VNDetectHorizonRequest`) sind auf beiden Plattformen
echt. Vorzeichen-Konvention (positiv = rechts tiefer) steht verbindlich am Feld in
`FrameAnalysis.kt` — fuer iOS aus der Vision-Rotationsrichtung hergeleitet, aber **noch
nicht an einem echten Geraet verifiziert**. `saliencyRegions`: auf Android seit 30.09.2026
echt, ueber **Spectral Residual** (modellfrei, siehe "Kompositionsbewertung komplett"
unten), auf iOS ueber `VNGenerateAttentionBasedSaliencyImageRequest`.

`FrameAnalyzer` hat jetzt ein `close()` (gibt den ML-Kit-Detektor frei), aufgerufen aus dem
`DisposableEffect` in `CameraPreview.android.kt`.

**Noch nicht auf Geraet/Emulator verifiziert:** Gesichtserkennung und Neigungswert im
laufenden Betrieb. Build und Unit-Tests sind gruen, aber das Debug-Badge (zeigt jetzt
Neigung + Gesichter-Anzahl) wurde noch nicht live gesehen — der Emulator hing beim Testen.

**ScoreOverlay (Plan 3.4) steht.** Schmale Leiste am oberen Bildrand: Score-Zahl (0-100,
farbig) + Symbol + *ein* Satz. Kein Overlay ueber dem Motiv — der Sucher bleibt frei.

Kette: `FrameAnalysis` → `CompositionScorer` (Score + eine Aussage) → `VerdictStabilizer`
(Mindestanzeigezeit) bzw. `ScoreSmoother` (Glaettung) → `ScoreOverlay`. Alles in
`domain/scoring/`, nicht in der UI — Entscheidungen darueber *was* gezeigt wird, ohne
Compose testbar.

**Kompositionsbewertung komplett (30.09.2026).** Anlass: "Komposition passt" stand in ~95 %
der Faelle, sobald der Horizont gerade war. Ursache war nicht eine Regel, sondern die
Datenlage: auf Android war `saliencyRegions` leer, ohne Gesicht hatte damit *keine*
inhaltliche Regel eine Meinung, und "keine Meinung" wurde als "passt" angezeigt.

- **Saliency Android** — `capture/saliency/SpectralResidualSaliency` (commonMain, pures
  Kotlin, Hou & Zhang 2007) auf einem 64×64-Helligkeitsbild aus der Y-Ebene
  (`androidMain/capture/LumaSampler.kt`, Sensor→aufrecht per `rotationDegrees`,
  unit-getestet). Ausgabe in derselben Form wie iOS: 12×12-Raster, aufs Maximum normiert,
  Schwelle 0,45. Das ist die modellfreie Option aus Plan 11 — ein gelerntes Modell kann
  spaeter an derselben Stelle einspringen. Drei Fallen, die beim Bauen auftraten und im Code
  dokumentiert sind: Mittelwert vor der FFT abziehen (sonst gilt heller Himmel allein wegen
  seiner Helligkeit als salient), Amplituden-Boden vor dem Log (sonst Geistermuster weit weg
  vom Objekt bei harten Kanten), Randstreifen ausblenden (FFT-Periodizitaet erzeugt
  Scheinkanten am Bildrand). Kontrastarme Bilder liefern bewusst *keine* Punkte.
  Im Gegensatz zu iOS ist die Inhaltsabhaengigkeit hier per Test belegt (Fleck wandert →
  Motivpunkt wandert mit).
- **`DeadSpaceRule`** — Anteil des Saliency-Gewichts je Bildhaelfte (links/rechts,
  oben/unten); unter 10 % ist die Haelfte "tot". Begruendet ist die Leere, wenn das Motiv auf
  der Achse nahe einer Drittel-Linie liegt (sonst widerspraeche sie der Drittel-Regel).
  Eigentlicher Zweck: Szenen *ohne* einzelnes Motiv (Landschaft mit leerem Himmel).
- **0-100-Score** — Regeln implementieren jetzt `ScoredRule.assess()` → `RuleAssessment`
  (Score 0..1 + optionaler Hinweis; `null` = keine Meinung, zaehlt nicht als 0). Der
  `CompositionScorer` bildet den gewichteten Mittelwert: Rahmung/Horizont 1,0, Drittel
  0,6 × Motiv-Sicherheit (Plan: Mitte ist legitim → nur mittleres Gewicht), tote Flaeche 0,7.
- **Drei Aussagen statt zwei** (`Verdict`): `Fix(hint)`, `Good`, `NoSubject`. Ohne
  inhaltliche Regel gibt es *keinen* Score und den neutralen Text "Kein klares Motiv
  erkannt" statt gruenem "passt". Ein schiefer Horizont wird trotzdem gemeldet.
- Hinweis-Rangfolge nach **Reparierbarkeit**: Rahmung → Horizont → Drittel → tote Flaeche.

Dabei gefundene und behobene Fehler:
- **Blickraum-Pruefung in `PortraitFramingRule` war toter Code.** Blickrichtung wurde aus der
  Gesichtslage geschaetzt ("links der Mitte → blickt nach rechts") und dann der Platz in
  *dieser* Richtung geprueft — per Konstruktion immer > halbe Bildbreite. Ausloesen konnte
  sie nur bei Gesichtern > 80 % Bildbreite; die Tests nutzten Rechtecke ausserhalb 0..1.
  Ersetzt durch eine Pruefung auf seitlichen Anschnitt. **Bekannte Luecke:** echter
  Blickraum braucht die Kopfdrehung (ML Kit `headEulerAngleY`, in FAST-Modus verfuegbar).
  Doku: "positiv = looking to the right of the camera" — aus wessen Sicht, ist mehrdeutig.
  Erst am Geraet belegen (Kopf drehen, Wert loggen), dann einbauen.
- **`HintStabilizer` liess nach "kein Hinweis" den naechsten Hinweis sofort durch** —
  pendelte ein Wert um eine Schwelle, blitzte "passt" fuer ein Frame auf. `VerdictStabilizer`
  haelt jeden Zustand die Mindestzeit.
- **Drittel-Tracker wurde beim Objektivwechsel nicht zurueckgesetzt** (seine eigene Doku
  verlangt das). Jetzt `LaunchedEffect(activeLens)` in `CameraScreen`.
- **`HorizonRule` meldete "180° schief — linke Seite anheben"** (im Emulator gesehen, Neigung
  −180° bei aufrechter Anzeige — Geraet kopfueber, UI dreht nicht mit). Werte ueber 45°
  sind jetzt "keine Aussage". Der Sensorwert selbst ist damit nicht erklaert — siehe
  Horizont-Querformat-Bugfix unten, beim Geraetetest mitpruefen.
- Kotlin/Native erlaubt **keine Kommas in Backtick-Testnamen** — faellt nur in `allTests`
  auf, nicht in `testDebugUnitTest`.

**Im Emulator verifiziert (30.09.2026, virtuelle Wohnzimmer-Szene):** Saliency liefert
~18 Punkte, das Motiv landet auf dem kontrastreichen Schachbrett links im Bild, der
Hinweis ("Motiv ca. 19 % zu weit oben — Kamera etwas nach oben schwenken", Score 69) passt
in Richtung und Groesse zur Lage des Schachbretts relativ zum unteren linken
Drittel-Punkt. Die −179,9°-Neigung des Emulators wird ignoriert statt gemeldet.

**Noch nicht verifiziert:** Qualitaet der Spectral-Residual-Saliency an echten Szenen
(synthetische Tests und Emulator-Szene ja, echtes Geraet nein); Kalibrierung von
`DeadSpaceRule.EMPTY_SHARE` und der Score-Gewichte — gehoert in den Feldtest (Plan 3.5).
Das Feldtest-Protokoll schreibt jetzt Aussage + Score bei jeder Aenderung.

Das `AnalysisDebugBadge` zeigt nur Rohdaten (Frames, Neigung, Gesichter, Anzahl
Saliency-Punkte) und faellt weg, sobald der Geraetetest die Daten bestaetigt hat.

**Feldtest-Ruestzeug (Plan 3.5), temporaer.**
- `ui/ThirdsGrid` — Drittel-Raster mit hervorgehobenen Schnittpunkten, ueber
  `GridToggle` (oben rechts) ein-/ausblendbar, standardmaessig AN. Ohne sichtbare Linien
  laesst sich nicht beurteilen, ob ein Hinweis stimmt.
  Sichtbarkeit ueberarbeitet (30.09.2026): Masse jetzt in dp statt px (vorher 1,5 px =
  ~0,5 dp auf 3x-Displays — zu duenn), helle Linie 1 dp / 85 % auf dunklem 2,5-dp-Halo
  (35 %), Schnittpunkte als Ring statt Vollkreis. Build gruen, **optisch noch nicht am
  Geraet/Emulator geprueft.**
- `diagnostics/FieldLog` — schreibt jede Hinweis-Aenderung und jede Aufnahme mit Uhrzeit
  nach `/sdcard/Android/data/com.florianhaeglsperger.photocoach/files/feldtest.log`.
  Abholen: `adb pull <Pfad>`. Datei statt Logcat, weil das Geraet beim Test nicht am
  Rechner haengt. **Faellt weg, sobald der Feldtest ausgewertet ist.**

**Rotation.** `OrientationEventListener` in `CameraPreview.android.kt` haelt jetzt beides
aktuell — mit einer Unterscheidung, die leicht untergeht:
- `imageCapture.targetRotation` folgt der **physischen** Geraetelage (Querformat-Foto auch
  bei gesperrter Bildschirmdrehung) — abgeleitet ueber `toSurfaceRotation()`, unit-getestet.
- `horizonSensor.displayRotation` folgt der **Bildschirm**-Drehung, direkt vom Display
  gelesen. Der Nutzer beurteilt "gerade" an dem, was er sieht.
Die beiden zu verwechseln faellt im Hochformat nicht auf, im Querformat sofort.

**Bugfix Horizont im Querformat (30.09.2026).** Im Querformat meldete die App bei geradem
Geraet 180 Grad Neigung. Ursache: der Sensor liest den Vektor nach OBEN (aufrecht +Y),
nicht die Fallrichtung — die Offsets in `toOffsetDegrees()` (und die Test-Fixtures) waren
fuer ROTATION_90/270 vertauscht. Jetzt ROTATION_90 → +90, ROTATION_270 → -90;
`HorizonSensorTest` angepasst, gruen. Im Emulator verifiziert (`adb emu rotate` — dreht
Sensor UND Display; `settings put system user_rotation` dreht nur das Display und taugt
hier nicht, der Sensor bliebe im Hochformat): Hochformat und beide Querformat-Lagen
zeigen "Komposition passt" (Neigung 0,1° / 0,0° / -0,8°). **Am echten Geraet noch nicht.**
Offene Vermutung: nach derselben Logik (Sensor liest Aufwaertsvektor) koennte auch das
Vorzeichen im Hochformat invertiert sein (rechte Kante tiefer → X negativ, Code meldet
dann negativ, Konvention sagt positiv). Beim Geraetetest pruefen: rechts kippen, Hinweis
"rechte Seite anheben" muss erscheinen.

**Horizont-Toleranz (30.09.2026).** `HorizonRule.TOLERANCE_DEGREES` von 2° auf 4°
angehoben (Plan 3.2 nannte 2°, war im Gebrauch zu empfindlich). Tests angepasst.

**Foto loeschen in der Vollbild-Ansicht (06.10.2026).** `PhotoLibrary.delete()` (Android:
`ContentResolver.delete`, iOS: Stub `false`), Papierkorb-Knopf oben rechts im `PhotoViewer`
mit Rueckfrage-Dialog. Danach rueckt das naechstaeltere Foto nach, beim letzten schliesst die
Ansicht; `onDeleted` aktualisiert die Vorschau im Sucher. **Noch nicht am Geraet/Emulator
verifiziert**, keine Unit-Tests (haengt am MediaStore). Offen: Fotos, die der App nicht mehr
gehoeren, lassen sich ohne `RecoverableSecurityException`-Behandlung nicht loeschen ("Löschen
nicht möglich" erscheint) — sie tauchen ab API 29 aber ohnehin nicht in der Liste auf.

**Objektiv-Umschalter.** `LensToggle` rechts neben dem Ausloeser, nur sichtbar wenn das
Geraet beide Kameras hat. `CameraState.Running` traegt `lensFacing`, `canSwitchLens` und
`switchLens` — gleiche Linie wie die uebrigen Zustaende: jeder traegt die Aktionen, die in
ihm moeglich sind. CameraX kann das Objektiv nicht im Betrieb wechseln, deshalb bindet der
`DisposableEffect` bei Aenderung neu (Key `desiredLens`).

**Wichtig dabei — Frontkamera-Spiegelung:** Die *Vorschau* der Frontkamera ist gespiegelt
(macht jede Kamera-App so), die *Analyse-Frames* sind es nicht — die kommen roh vom Sensor.
Ohne Korrektur zeigt jeder Hinweis beim Selfie in die falsche Richtung. `CameraFrame` traegt
deshalb ein `mirrored`-Flag, und `analyze()` spiegelt die Gesichter ueber
`FaceRect.mirroredHorizontally()` (commonMain, 7 Unit-Tests).

Nicht gespiegelt wird die **Horizont-Neigung**: die misst die physische Geraetelage, und
die aendert sich nicht dadurch, welche Kamera aktiv ist.

Offen und bewusst so gelassen: Das *gespeicherte* Selfie ist nicht gespiegelt (CameraX-
Standard, so kennt man es von jeder Kamera-App) — es unterscheidet sich also spiegelbildlich
von der Vorschau, auf die sich die Hinweise bezogen. Fuer die Beurteilung der Komposition
ist das unkritisch (Drittel-Punkte bilden sich auf Drittel-Punkte ab), sollte aber nach dem
Feldtest bewusst entschieden werden.

**Sucher = Foto: 4:3-Vorschau und Zoom (30.09.2026).**
Bug: die Vorschau lief im Vollbild mit `FILL_CENTER` und schnitt vom 4:3-Sensorbild links
und rechts ein grosses Stueck ab — das gespeicherte Foto war spuerbar weitwinkliger als
der Sucher, und Drittel-Raster/Hinweise bezogen sich auf einen anderen Ausschnitt als das
Foto. Jetzt:
- `SENSOR_ASPECT_RATIO` (4:3, `ui/CameraPreview.kt`). Vorschau, Analyse und Foto fordern
  es per `ResolutionSelector` an; `CameraScreen` zeigt die Vorschau in einem Rahmen genau
  dieses Formats (hochkant 3:4, quer 4:3), `ThirdsGrid` liegt im selben Rahmen,
  `PreviewView` auf `FIT_CENTER`.
- Zoom ueber `CameraControl.setZoomRatio` (gilt fuer alle drei Use-Cases zugleich).
  `CameraState.Running` traegt `zoom: CameraZoom` + `setZoom`, gemeldet ueber den
  `zoomState`-LiveData. UI in `ui/ZoomControl.kt`: `ZoomBar` (Stufen 0,x×/1×/2×/5×, je
  nach Geraet, `zoomPresets()` unit-getestet) und Pinch-Geste auf dem Vorschau-Rahmen.
  Frontkamera: 1×/2×/**3×** statt 5× (05.10.2026, Nutzerwunsch — bei Armlaenge ist 5×
  rein digital und zu eng). `zoomPresets()` nimmt dafuer das `LensFacing` mit; im Emulator
  verifiziert (Leiste, Animation 1→3, Bild).
  Ultraweitwinkel (<1×) erscheint nur, wenn das Geraet es ueber die logische Rueckkamera
  anbietet (Pixel, neuere Samsung) — Geraete mit separater UW-Kamera-ID bekommen es so
  nicht, dafuer muesste man ueber `CameraInfo.getIntrinsicZoomRatio()` die Kamera wechseln.
- `FieldLog` protokolliert Zoom-Wechsel und die Zoomstufe bei jeder Aufnahme.

Mitbehoben: nach einem Objektivwechsel lief die Analyse nicht mehr (Frame-Zaehler stand) —
`clearAnalyzer()` im `onDispose` des Bind-Effekts, der Analyzer wurde aber nur einmal
gesetzt. `clearAnalyzer()` steht jetzt im Effect, der am Leben des Composables haengt.

Im Emulator verifiziert: Rueckkamera-Foto zeigt exakt den Ausschnitt der Vorschau;
Frontkamera 2× — Zoom sichtbar in Vorschau und gespeichertem Foto; Leiste blendet sich bei
der Emulator-Rueckkamera (Zoombereich 1–1) korrekt aus; Analyse laeuft nach Hin- und
Rueckwechsel des Objektivs weiter; Querformat zeigt 4:3-Rahmen. **Nicht getestet:**
Pinch (adb kann kein Multitouch) und Ultraweitwinkel (Emulator hat keins) — beides am
Geraet pruefen.

**Zoom-Animation (05.10.2026).** Ein Tipp auf eine Zoom-Stufe springt nicht mehr, sondern
faehrt in 300 ms dorthin (`ZoomAnimator` in `ui/ZoomControl.kt`, FastOutSlowIn).
Interpoliert wird **geometrisch** (`interpolateZoom`, unit-getestet), nicht linear — Zoom
wird als Faktor wahrgenommen, linear kroeche 1×→5× erst und raste dann. Jeder Schritt ist
ein eigener `setZoom`-Aufruf (~60 Hz), CameraX verwirft ueberholte. Pinch und
Objektivwechsel brechen eine laufende Animation ab. Die Buttons der `ZoomBar`
animieren Groesse und Farbe mit.
Im Emulator verifiziert: Leiste laeuft durch Zwischenwerte, und per Logcat (`CXCP`)
kommt bei der Kamera eine saubere, monotone Folge an (5,0 → 4,94 → … → 1,05). Das
*Kamerabild* des Emulators zieht aber nicht fluessig mit (springt, die emulierte HAL
meldet dabei `ApplyOverrideZoom: awbRegions needs to be specified`) — ein Artefakt der
Emulator-Kamera. **Ob das Bild auf echter Hardware weich zoomt, ist noch nicht gesehen.**

**Pro-Modus (30.09.2026).** `PRO`-Knopf (seit 05.10.2026 oben rechts unter dem
Raster-Knopf, gleiche Groesse 40 dp; der Platz links vom Ausloeser gehoert jetzt der
Foto-Vorschau) oeffnet ein Panel
(ersetzt solange die Zoomleiste, Pinch geht weiter): Belichtungskorrektur (EV), ISO,
Verschlusszeit, Weissabgleich (Voreinstellungen), manueller Fokus. Der Knopf wird gelb,
sobald irgendetwas nicht auf Automatik steht, auch bei geschlossenem Panel.
- `capture/ManualSettings.kt` (commonMain): `ManualSettings`, `ManualCapabilities`,
  Standard-Stufen (ISO-Drittelstufen, Verschlusszeiten bis max. 1 s) und Formatierung,
  unit-getestet (`ManualSettingsTest`). UI in `ui/ProControls.kt`.
- `CameraState.Running` traegt `manualCapabilities`, `manualSettings`,
  `setManualSettings` (gleiches Muster wie Zoom). Nach Objektivwechsel wieder alles Auto.
- Android: `capture/ManualCameraControl.kt` — EV ueber CameraX, alles andere per
  Camera2Interop (`Camera2CameraControl.setCaptureRequestOptions`), gilt fuer Vorschau,
  Analyse und Foto. ISO/Zeit/Fokus nur bei `MANUAL_SENSOR`, WB nur bei >=2 Modi —
  sonst blendet die UI den Regler aus. Ist nur ISO *oder* Zeit manuell, kommt der andere
  Wert aus der letzten Messung der Automatik (Capture-Callback an der Preview).
- Stolperfalle, gefunden im Emulator: Beim Zurueckschalten auf Automatik behielt die
  Kamera den zuletzt manuellen ISO-Wert und regelte nur noch die Zeit — nach ISO 1000 ->
  Auto dauerhaft ueberbelichtet, obwohl AE "eingeschwungen" meldete. Deshalb Rueckweg in
  zwei Schritten (erst letzte Automatik-Werte manuell setzen, dann AE an).
- `FieldLog` schreibt die manuellen Werte bei jeder Aufnahme mit.

Im Emulator verifiziert: Die **Emulator-Rueckkamera hat kein `MANUAL_SENSOR`** (nur EV
wird angeboten, korrekt), die **Frontkamera hat alles** — dort getestet: 1/8000 s dunkelt
sichtbar ab, ISO 1000 hellt auf, Foto mit manuellen Werten gespeichert und protokolliert,
Rueckweg zu Auto belichtet wieder normal, WB-/Fokus-UI setzen die Werte. **Nicht
verifiziert:** sichtbare Wirkung von WB und Fokus (synthetische Emulator-Szene) und
alles an einem echten Geraet. Offen: Tap-to-Focus fehlt, WB nur Voreinstellungen (kein
Kelvin), Blitz-Modi wurden nicht angefasst.

**Foto-Vorschau und Vollbild-Ansicht (05.10.2026).** Runde Vorschau des letzten Fotos
links vom Ausloeser (`PhotoThumbnail`), antippen oeffnet es im Vollbild (`PhotoViewer`).
- Naht `ui/PhotoLibrary.kt` (commonMain): `CapturedPhoto(id)` — undurchsichtige Referenz,
  auf Android die MediaStore-`content://`-URI — plus `PhotoLibrary.latest()`/`load()`.
  `CaptureResult.Success` traegt jetzt `photo` (aus `OutputFileResults.savedUri`).
  iOS-`actual` ist ein Stub ohne Fotos (pausiert).
- Android (`PhotoLibrary.android.kt`): `latest()` sucht per Dateinamen-Praefix
  `PHOTO_NAME_PREFIX` ("PhotoCoach_") — **keine neue Berechtigung**: ab API 29 sieht die
  App ihre eigenen MediaStore-Eintraege ohne Leserecht; nach Neuinstallation gehoeren alte
  Fotos ihr nicht mehr und erscheinen bewusst nicht. Dekodieren ueber `ImageDecoder`
  (dreht nach EXIF — CameraX schreibt die Drehung teils nur als Tag), auf API 26/27
  `BitmapFactory` + `ExifInterface` von Hand.
- **Volle Qualitaet:** Vollbild laedt bis 4096 px lange Kante (12-MP-Fotos also
  unverkleinert), Vorschau 256 px. Pinch bis 6×, Doppeltippen 2,5× zur angetippten Stelle,
  Verschieben begrenzt (`clampPan`, kein schwarzer Rand). Bis das volle Bild dekodiert ist,
  steht die Vorschau an seiner Stelle — der Uebergang wartet nicht aufs Dekodieren.
- **Uebergang:** Bild waechst aus dem Vorschau-Kreis an die Vollbild-Position (320 ms,
  FastOutSlowIn), Kreis oeffnet sich zum Rechteck, Hintergrund blendet nach Schwarz;
  Schliessen (X oder Zurueck-Taste) rueckwaerts. Trick: durchgehend `ContentScale.Crop`
  in ein interpoliertes Rechteck — am Ende hat es das Seitenverhaeltnis des Fotos, also
  fehlt nichts. Neue Vorschau "ploppt" kurz auf, wenn ein Foto dazukommt.
- `PlatformBackHandler` als eigene expect/actual-Naht (Android: activity-compose
  `BackHandler`), statt der CMP-Abhaengigkeit `ui-backhandler` im iOS-Build.
- **Wegwischen nach unten** (05.10.2026): Bild folgt dem Finger, schrumpft bis 30 %, der
  Sucher scheint durch; losgelassen schliesst es ab 15 % Bildschirmhoehe oder bei
  Schnippen > 1000 dp/s (`shouldDismiss`), sonst federt es zurueck. Nur bei nicht
  vergroessertem Bild — vergroessert verschiebt derselbe Wisch den Ausschnitt. Ob Wischen
  oder Zoomen/Verschieben, entscheidet sich einmal pro Geste nach der Touch-Slop
  (`viewerGestures`, eigene Schleife statt `detectTransformGestures`, weil das kein
  Gestenende meldet).
- **Blaettern durch alle App-Fotos** (06.10.2026): `PhotoLibrary.all()` liefert die
  eigenen Fotos neuestes zuerst (Praefix `PhotoCoach_` + ab API 29 Ordner
  `Pictures/PhotoCoach`, Gleichstand in `DATE_ADDED` per `_ID` aufgeloest). Finger nach
  links = aelteres Foto. Einrasten ab 20 % Breite oder Schnippen (`pageStep`,
  unit-getestet), am Rand der Reihe gummiartiger Widerstand, Zaehler "3 / 12" oben mittig.
  Eigene Seiten-Logik statt `HorizontalPager` — der haette mit Zoom und Wegziehen um
  dieselben Wische konkurriert; `viewerGestures` entscheidet pro Geste zwischen Wegziehen,
  Blaettern und Zoomen. **Speicher:** nur die aktuelle Seite in voller Aufloesung, die
  Nachbarn mit 1600 px vorgeladen, alles weiter weg wird freigegeben. Zurueck in den
  Vorschau-Kreis schrumpft nur das Foto, das die Vorschau zeigt; ein anderes blendet zur
  Mitte hin aus.
  Im Emulator verifiziert: 21 eigene Fotos, Zaehler laeuft 1 → 21 und zurueck, beim
  langsamen Wischen stehen beide Seiten mit Steg nebeneinander, am Ende der Reihe nur
  Gummi-Nachgeben, Schliessen von Seite 21 blendet aus. Fotos frueherer Installationen
  (auf dem Emulator liegen mehr `PhotoCoach_`-Dateien) erscheinen wie vorgesehen nicht.
  Inhaltlich zeigen alle Emulator-Fotos dieselbe Szene — dass die *richtigen* Bilder
  hinter den Seiten stehen, ist nur ueber den Zaehler belegt, am Geraet nachsehen.
- **Behobener Fehler dabei:** Die Gesten hingen am *bewegten* Bild (`pointerInput` nach
  `graphicsLayer`). In dessen Koordinaten gemessen schrumpfte jede Bewegung um die
  Mitbewegung des Bildes — Verschieben im vergroesserten Bild lief dem Finger hinterher.
  Jetzt haengen sie an der festen Vollbild-Flaeche; Zoomen haelt zusaetzlich den Punkt
  zwischen den Fingern fest (`panForZoomAround`, unit-getestet).

Im Emulator verifiziert: Vorschau zeigt beim Start das juengste vorhandene Foto, nach der
Aufnahme sofort das neue; Oeffnen-Uebergang (mit 10× verlangsamten Animationen
mitgeschnitten) waechst sauber aus dem Kreis; Vollbild scharf; Doppeltippen vergroessert
zur angetippten Stelle; X und Zurueck schliessen; kurzer langsamer Zug federt zurueck,
langer Zug und kurzes Schnippen schliessen. **Nicht verifiziert:** Pinch (adb kann
kein Multitouch), API 26/27-Pfad, echte 12-MP+-Fotos an einem Geraet (Speicher/Latenz
beim Dekodieren).
Testfalle fuer adb: `input tap` zweimal hintereinander ist oft zu langsam fuer einen
Doppeltipp — `input tap x y & sleep 0.12; input tap x y` in *einer* `adb shell` klappt.

**Teilen und Info in der Vollbild-Ansicht (06.10.2026) — Stufe A aus
`Planung/Foto-Bearbeitung.md` damit komplett.** Aktionsleiste unten (`ViewerActionBar` in
`ui/PhotoViewerActions.kt`), bewusst nur `Teilen · Info`: Analyse/Bearbeiten kommen mit
Stufe B/C, nicht vorher als tote Platzhalter.
- `PhotoLibrary.share()` — System-Chooser mit `ACTION_SEND`, Lesefreigabe nur fuer diese
  URI (`FLAG_GRANT_READ_URI_PERMISSION` + `ClipData`, sonst keine Vorschau im Chooser).
  Gestartet aus dem Activity-Context (`uiContext`), damit "Zurueck" in die App fuehrt.
- `PhotoLibrary.details()` → `PhotoDetails` (Rohwerte), Formatierung in commonMain
  (`ui/PhotoDetails.kt`, `rows()` + `formatExposureTime` usw., 9 Unit-Tests). Quelle:
  MediaStore (Name, Groesse, Ordner, Zeit) + EXIF (ISO, Zeit, Blende, Brennweite, WB, Blitz,
  Geraet). Breite/Hoehe nach EXIF-Orientierung getauscht — CameraX legt Hochformat-Fotos
  als quer gespeicherte Pixel + Orientierungs-Tag ab.
- **Falle:** das *Framework*-`android.media.ExifInterface` kennt
  `TAG_PHOTOGRAPHIC_SENSITIVITY` nicht (nur AndroidX) — `TAG_ISO_SPEED_RATINGS` ist
  derselbe Tag 0x8827.
- Info-Panel bleibt beim Blaettern offen, schluckt eigene Wischgesten (blaettert nicht),
  "Zurueck" schliesst erst das Panel (zweiter `PlatformBackHandler`, spaeter registriert).
- **Fehlt in der Info, weil nicht im EXIF:** Objektiv, Zoomstufe, Aufnahme-Score —
  braucht `CaptureMeta` (Plan 4.3, im Hauptplan Abschnitt 4a als naechster Schritt).
Im Emulator verifiziert: Werte stimmen mit direkt ausgelesenem EXIF ueberein (1/100 s,
f/4, ISO 200, 960 × 1280, 198 KB); Blaettern bei offenem Panel aktualisiert die Daten;
Wisch auf dem Panel blaettert nicht; Zurueck schliesst nur das Panel; Teilen oeffnet den
Chooser mit Bildvorschau, Zurueck fuehrt in den Viewer (gleicher Task). `allTests` gruen
inkl. iOS-Simulator. Datum ist im Emulator englisch formatiert — Geraete-Locale, gewollt.

**Build/Run (Android):**
- `./gradlew :androidApp:assembleDebug` — Debug-APK bauen
- Im Android Studio Projekt oeffnen (nutzt dessen gebuendeltes JBR) oder lokal:
  `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew ...`
  (kein separates System-JDK auf diesem Rechner installiert)
- Emulator "Medium_Phone_API_36.0" ist als AVD vorhanden

**Build/Run (iOS):**
- `cd iosApp && xcodegen generate && open iosApp.xcodeproj` — Simulator als Ziel waehlen, Run
  (baut `shared.framework` automatisch mit, siehe `preBuildScripts` in `project.yml`)
- Oder per CLI: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator
  -destination 'platform=iOS Simulator,name=<Geraetename>' -derivedDataPath iosApp/build build`
- `xcodegen` ist per Homebrew installiert; `iosApp.xcodeproj` selbst ist NICHT eingecheckt
  (generiertes Artefakt, siehe `.gitignore`)

Versionen (Stand 2026-08, siehe `gradle/libs.versions.toml`): Kotlin 2.2.20, AGP 8.11.1,
Compose Multiplatform 1.10.3 (bewusst nicht 1.11.1, siehe iosMain/README.md), Gradle 8.13,
compileSdk 36, targetSdk 35, minSdk 26, CameraX 1.6.1, iOS-Deploymenttarget 14.0.
compileSdk musste fuer CameraX 1.6 von 35 auf 36 — targetSdk bleibt bewusst auf 35, das ist
eine getrennte Entscheidung (Laufzeitverhalten).

- `Planung/Umsetzbarkeit.md` — feasibility research: competitor landscape (GudoCam,
  ComposeAI, SnapFrame, LiveCompose, etc.), which platform APIs cover which features,
  and a gap analysis (section 6) identifying underserved niches.
- `Planung/Plan-zur-Umsetzung.md` — the implementation plan derived from that research:
  tech stack decision, module structure, and a phased roadmap (Phase 0–8) with a
  Definition of Done per phase.
- `Planung/ML-Architektur.md` — follow-up ML research refining the plan (supervised
  approach instead of RL for Phase 6, CADB over AVA/NIMA as the composition dataset,
  on-device rationale).
- `Planung/Foto-Bearbeitung.md` — plan for viewing/editing photos after capture (06.10.2026,
  nothing built yet): edits as suggestions driven by the existing `CompositionScorer`
  (straighten, smart crop per aspect ratio), `EditRecipe` as pure data in `domain/edit/`,
  capture metadata (`CaptureMeta`) as a prerequisite, open decisions in §5.

Both documents are in German; keep planning/status updates to them in German to stay
consistent, unless the user asks otherwise.

## Project concept

An iOS + Android app that gives **live composition/framing feedback** while a user is
framing a shot (rule of thirds, horizon tilt, dead space, portrait framing), with a
post-capture aesthetic score and gallery. The two features chosen to differentiate from
existing competitor apps (per the gap analysis) are:

- **Explainable overlay** — visualize the actual saliency map / detected edges the
  scoring is based on, not just a bare score.
- **Text-based coaching** — proactive, concrete short instructions while framing
  ("Person etwas weiter rechts, naeher rankommen") instead of only a score or an overlay
  the user has to interpret. Driven by the same `FrameAnalysis` data as the overlay.
  (Speech/haptic output for blind/low-vision users was considered and deliberately
  deprioritised — see Planung/Umsetzbarkeit.md 6.1.)

## Planned architecture

**Stack:** Kotlin Multiplatform (KMP) + Compose Multiplatform, targeting iOS and Android
from one UI/logic codebase. Camera capture and ML/vision inference are implemented
natively per platform (no cross-platform vision library abstracts iOS Vision framework
and Android CameraX/ML Kit well enough) and funneled into one shared data model.

**Module layout (planned):**

```
photocoach/
├── shared/                 # KMP module
│   ├── commonMain/
│   │   ├── domain/         # Pure Kotlin: model/, rules/, scoring/, coaching/
│   │   ├── capture/        # FrameAnalyzer.kt — expect class
│   │   └── ui/             # Compose UI: CameraScreen, GalleryScreen, ScoreOverlay
│   ├── iosMain/capture/    # FrameAnalyzer.ios.kt — actual, AVFoundation + Vision
│   └── androidMain/capture/# FrameAnalyzer.android.kt — actual, CameraX + ML Kit + TFLite
├── iosApp/                 # thin iOS host (Xcode project)
└── androidApp/             # thin Android host
```

**Core cross-platform contract** (keeps platform-specific vision code out of shared logic):

```kotlin
// commonMain
expect class FrameAnalyzer {
    fun analyze(frame: CameraFrame): FrameAnalysis
}

data class FrameAnalysis(
    val horizonTiltDegrees: Float?,
    val saliencyRegions: List<SaliencyPoint>,  // normalized 0.0–1.0
    val faces: List<FaceRect>,
    val timestampMs: Long
)
```

All composition rules, scoring, and coaching-text logic in `domain/` operate only on
`FrameAnalysis` and never touch platform APIs directly — each platform's `FrameAnalyzer`
implementation (iOS: `VNDetectHorizonRequest`, `VNGenerateAttentionBasedSaliencyImageRequest`,
`VNDetectFaceRectanglesRequest`; Android: CameraX + ML Kit + a small TFLite saliency model +
sensor fusion for horizon) is the only place that varies per platform. This is the seam to
preserve when adding features: new composition rules go in `domain/rules/`, new sensing
capability goes behind `FrameAnalyzer`.

## Roadmap phases (see Planung/Plan-zur-Umsetzung.md for full detail)

0. Project setup — KMP scaffold, dummy `FrameAnalyzer`, basic camera preview both platforms.
1. MVP — rule-based feedback (rule of thirds, horizon, dead space, portrait framing) live
   in-viewfinder, both platforms.
2. Explainable overlay + post-capture aesthetic score (NIMA-style model → CoreML + LiteRT).
3. Text-based coaching — short, actionable in-viewfinder hints derived from the same
   `FrameAnalysis` data (template-based, no model needed).
4. Active directional suggestions via heuristics (saliency → nearest third-point vector).
5. Story/sequence coach for content creators (shot-type variety, color consistency across
   a marked photo series).
6. Optional: own ML model for directional suggestions (only if Phase 4 heuristics prove
   insufficient) — study github.com/LiveCompose/LiveCapture (MIT) as reference.
7. Optional: social duel/comparison mode (backend-heavy, deliberately late).
8. Polish & release (both app stores).

Open technical decisions that should be resolved before Phase 1 work starts (see
Planung/Plan-zur-Umsetzung.md §11): which Android saliency model to use, which
aesthetic-score model to build/port for Phase 2, minimum supported OS versions, and the
on-device-vs-cloud privacy stance.
