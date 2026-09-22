# Umsetzbarkeit: App für Bildkomposition & Kamerawinkel

Recherche-Stand: 21.08.2026

> **⏸️ Hinweis 22.09.2026:** Die Machbarkeitsaussagen unten gelten für beide Plattformen
> und bleiben gültig. Gebaut wird aktuell aber **nur Android** — siehe
> [Plan-zur-Umsetzung.md](./Plan-zur-Umsetzung.md) Abschnitt 0. Wo hier „Apple-Bordmittel"
> als Erleichterung genannt sind (Abschnitt 2), gilt das entsprechend erst wieder, wenn
> iOS zurückkommt; auf Android ist die Saliency weiterhin der Engpass.

## Kurzfazit

**Machbar, und es gibt bereits Beweise dafür in freier Wildbahn.** Der Markt ist aktiver als
ursprünglich angenommen — mehrere Apps machen genau das schon (siehe unten), inklusive
mindestens einer, die Open Source ist und die "schwierige" Variante (aktive
Richtungsvorschläge) bereits im App Store hat. Das ändert die Einschätzung von "Forschungsprojekt"
zu "ambitioniertes, aber realistisches Projekt mit Referenzimplementierung".

## 1. Was es am Markt schon gibt

| App | Ansatz |
|---|---|
| **GudoCam** | Erkennt 14 Kompositionsarten live im Sucher (Drittel-Regel, Goldener Schnitt, Fluchtpunkt), LLM-Tipps auf Anfrage |
| **GridCam** | On-Device-Modell sagt passenden Grid-Typ live voraus |
| **ComposeAI** | Live-Kompositionsanalyse, intelligenter Zuschnitt, Ästhetik-Score |
| **NotanPro** | Live-Scoring mit Drittel-/Goldener-Schnitt-Overlays |
| **Entropix** | Kuratierte Komposition-Templates passend zur aktuellen Szene, auch generative Vorschläge |
| **SnapFrame** | On-Device-KI, analysiert Rahmen jede Sekunde, erkennt Gesichter/Motive/Szene, komplett privat (keine Uploads) |
| **构妙 LiveCapture (LiveCompose)** | **Open Source (MIT), iOS, bereits im App Store.** Nutzt Reinforcement Learning + Gyroskop, um aktiv vorzuschlagen, wie das Handy bewegt werden soll, für die beste Aufnahme — genau die "Variante 2" (Richtungsvorschläge), die ich zuerst als reines Forschungsproblem eingeschätzt hatte. Code: github.com/LiveCompose/LiveCapture |

**Einordnung:** Das ist kein weißer Fleck, sondern ein aktiv bearbeitetes Nischenfeld mit
kleinen, meist unabhängigen Entwicklern/Teams (keine Big-Tech-Platzhirsche). Realistisch
für ein Einzelprojekt — aber auch: Differenzierung wird schwieriger, es gibt schon
Konkurrenz zu jeder möglichen Idee hier.

## 2. Technische Bausteine, die die Umsetzung erleichtern

Wichtigste Erkenntnis: **Apple liefert einen großen Teil der Basis-Funktionalität bereits
fertig im Vision-Framework mit**, kein eigenes Modell nötig für den Einstieg:

- `VNDetectHorizonRequest` — Horizont-Erkennung inkl. Neigungswinkel, eingebaut
- `VNGenerateAttentionBasedSaliencyImageRequest` / `VNGenerateObjectnessBasedSaliencyImageRequest`
  — Saliency-Map (wo schaut das Auge zuerst hin?), eingebaut. Das ist die Grundlage,
  um zu prüfen, ob das Hauptmotiv sinnvoll platziert ist (z. B. nahe einer Drittel-Linie)
- `VNDetectFaceRectanglesRequest` / `VNDetectFaceLandmarksRequest` — Gesichtserkennung
  für Porträt-spezifische Regeln
- `VNDetectContoursRequest` — Kanten-/Linienerkennung, Basis für Leading-Lines-Checks
- `CoreML` — für alles, was über die Standard-Vision-Requests hinausgeht (z. B. Ästhetik-Score,
  Bewegungsvorschläge)

→ Ein großer Teil von "Variante 1" (regelbasiertes Feedback: Horizont, Drittel-Regel,
Motiv-Platzierung) lässt sich **ohne eigenes trainiertes Modell** bauen, nur mit
Apple-Bordmitteln + etwas Geometrie/Logik obendrauf.

## 3. Ästhetik-Bewertung (für "wie gut ist die Komposition insgesamt")

- **NIMA (Neural Image Assessment, Google Research)** — etabliertes Modell, MobileNet-basiert,
  klein genug für Mobile-Deployment, gibt einen 1-10-Ästhetik-Score aus. Gut dokumentiert,
  mehrere Open-Source-Nachbauten verfügbar, auf CoreML/TFLite konvertierbar.
- Trainingsdaten: **AVA-Datensatz** (ca. 250.000 bewertete Fotos), Standard-Referenz
  für diese Art Modell.
- Aktuelle Forschung geht Richtung "Fine-Grained"-Bewertung mit Begründungstexten
  (z. B. ArtiMuse, CVPR 2026) — spannend, aber für ein MVP nicht nötig, eher spätere Ausbaustufe.

## 4. Die zwei Varianten neu bewertet

### Variante 1 — Regelbasiertes Feedback (Horizont, Drittel-Regel, Motiv-Platzierung, toter Raum)
**Machbarkeit: Hoch.** Größtenteils mit Apple-Vision-Bordmitteln umsetzbar, keine eigene
Modell-Trainingsinfrastruktur nötig. Realistischer Umfang für einen einzelnen Entwickler
in mehreren Wochen (Abende/Wochenenden neben der Schule).

### Variante 2 — Aktive Richtungsvorschläge ("geh 2 Schritte nach rechts", "geh tiefer")
**Machbarkeit: Mittel, aber erwiesenermaßen machbar** — LiveCompose beweist, dass es geht,
und ist sogar Open Source einsehbar als Referenz (Two-Stage-CoreML-Detektor: BBox-Modell +
"Actor"-Modell, vermutlich ein Actor-Critic-Reinforcement-Learning-Ansatz). Erfordert aber:
- Eigenes Trainings-Setup oder Nachbau/Anpassung eines bestehenden Modells
- Deutlich mehr ML-Erfahrung (Reinforcement Learning ist komplexer als Klassifikation)
- Realistisch eher Monate als Wochen, wenn man nicht einfach den bestehenden Code forkt

**Pragmatischer Mittelweg:** Statt eigenem RL-Modell könnte man mit einfacheren Heuristiken
(Saliency-Map + Distanz zu Drittel-Linien, dann Text-Hinweis "Motiv näher an linke
Drittel-Linie bringen") einen guten Teil des gefühlten Nutzens von Variante 2 erreichen,
ohne die volle ML-Komplexität.

## 5. Risiken

- **Marktsättigung:** Wie oben gezeigt, gibt es schon 6-7 vergleichbare Apps. Differenzierung
  nötig (z. B. Fokus auf Lerneffekt/Erklärung statt nur Score, oder eine spezifische
  Nische wie Schul-/Einsteiger-Fotografie)
- **On-Device-Performance:** Echtzeit-Analyse bei 30 fps kostet Akku/Rechenleistung —
  Vision-Framework-Requests sind dafür optimiert, eigene CoreML-Modelle müssen klein/quantisiert sein
- **Modellqualität:** Aesthetik ist subjektiv — schlechte Vorschläge sind schlimmer als keine
  (siehe Einschätzung von letzter Woche), gutes Testing mit echten Nutzern nötig

## 6. Feature-Lücken bei bestehenden Apps — Recherche & Bewertung

Recherche-Stand: 22.08.2026. Geprüft gegen die Feature-Listen von GudoCam, ComposeAI,
Entropix, SnapFrame und NotanPro (alle: Live-Grid-Erkennung, Motiv-Ausrichtung,
Zoom-/Belichtungs-Hinweise, Ästhetik-Score, teils LLM-Tipps auf Anfrage — siehe Abschnitt 1).
Alle fünf Apps bedienen im Kern dieselbe Nische: sehender Einzel-Nutzer, generischer
"Standard-Geschmack", visuelles Overlay als einzige Feedback-Form. Daraus ergeben sich
mehrere echte Lücken:

### 6.1 Textbasiertes Coaching mit konkreten Kurzanweisungen

**Lücke bestätigt:** GudoCam & Co. zeigen ein Grid und ggf. einen Score, teils
LLM-Tipps auf Anfrage — aber keine gibt während des Framens proaktiv konkrete,
handlungsanweisende Kurztexte aus ("Person etwas weiter rechts, näher rankommen").
Das Feedback bleibt entweder ein abstrakter Score oder ein Overlay, das der Nutzer
selbst interpretieren muss, statt eine direkte Handlungsanweisung wie ein Coach.

**Machbarkeit: Hoch.** Die Bausteine sind dieselben wie in Abschnitt 2
(Saliency-Map, Gesichtserkennung, Horizont) — die Ausgabe ist ein kurzer Text-Hinweis
direkt im Kamera-UI statt nur Overlay/Score. Kein zusätzliches Modell nötig, nur eine
Textgenerierungs-/Ausgabe-Schicht auf bereits vorhandenen Daten. Größte
Herausforderung ist, die Hinweise kurz und eindeutig genug zu formulieren, damit sie
beim schnellen Blick aufs Display sofort verständlich sind.

*Später denkbare Ausbaustufe:* Dieselben Kurzanweisungen ließen sich grundsätzlich
auch über Sprache (`AVSpeechSynthesizer`) oder Haptik (`CoreHaptics`) ausgeben, was
Richtung Barrierefreiheit für blinde/sehbehinderte Nutzer öffnen würde. Das ist aber
aktuell nicht Teil der Zielgruppe und wird deshalb nicht priorisiert.

### 6.2 Personalisierter statt generischer Ästhetik-Geschmack

**Lücke bestätigt:** Alle geprüften Apps nutzen einen einzigen, für alle Nutzer
gleichen Ästhetik-Score (trainiert auf allgemeinen Datensätzen wie AVA). Aktuelle
Forschung (Kim et al., *"Learning Personalized Photographic Style from Pairwise User
Preferences"*, CVPR 2026, inkl. PPSD-Datensatz mit ~60.000 Präferenz-Urteilen von 767
Nutzern) zeigt, dass sich individueller Geschmack aus Paarvergleichen ("Bild A oder B
schöner?") lernen lässt — bislang aber nur als Forschungsarbeit, kein Produkt am Markt
nutzt das.

**Machbarkeit: Mittel.** Prinzip ist erprobt, aber: (a) Cold-Start-Problem — der Nutzer
müsste erst genug eigene Vergleichsurteile abgeben (z. B. 30-50 Foto-Duelle beim
Onboarding), bevor Personalisierung greift; (b) das eigentliche Nachbauen des
Preference-Learning-Ansatzes erfordert mehr ML-Erfahrung als reines Modell-Einbinden.
Realistischer Zwischenschritt: einfacher Gewichtungs-Layer, der den generischen Score
mit Nutzer-Feedback ("gefällt mir" auf eigenen Fotos) nachjustiert, statt volles
Preference-Learning-Modell nachzubauen.

### 6.3 Sequenz-/Story-Komposition für Content-Creator

**Lücke bestätigt:** Alle geprüften Apps bewerten ein Einzelbild. Keine berücksichtigt,
dass ein Instagram-Carousel oder eine Story aus mehreren, bewusst unterschiedlichen
Einstellungen besteht (weit/mittel/nah, wiederkehrende Farbpalette). Für genau diese
Zielgruppe (auch dein eigenes LinkedIn/Instagram-Ziel) gibt es aktuell kein
Coaching-Tool.

**Machbarkeit: Mittel-Hoch.** Braucht kein neues Kernmodell — nur Zusatzlogik über
mehrere bereits aufgenommene Fotos hinweg: Motiv-zu-Bild-Verhältnis schätzen
(→ Weit/Mittel/Nah-Klassifikation), dominante Farben extrahieren (klassische
Bildverarbeitung, kein ML nötig) und auf Abwechslung/Konsistenz prüfen. Guter
Kandidat für eine Ausbaustufe, weil er auf ohnehin vorhandenen Bausteinen aufbaut.

### 6.4 Erklärbares Live-Overlay statt reinem Score

**Lücke bestätigt:** GudoCam & Co. zeigen ein Grid und ggf. einen Score/eine
Text-Empfehlung, aber nicht *warum* — die zugrundeliegende Saliency-Map oder erkannte
Linien werden nicht sichtbar gemacht. Für den Lerneffekt (dein ursprüngliches Ziel:
Nutzer sollen tatsächlich etwas lernen, nicht nur einer Zahl folgen) ist das ein
relevanter Unterschied.

**Machbarkeit: Hoch.** Reine Visualisierungsaufgabe — die Daten (Saliency-Map,
erkannte Kanten/Linien) liegen durch die Vision-Requests aus Abschnitt 2 ohnehin
schon vor, sie müssten nur zusätzlich als halbtransparentes Overlay gezeichnet werden
statt nur intern für die Score-Berechnung verwendet zu werden. Kein zusätzliches
Modell, geringer Mehraufwand.

### 6.5 Sozialer Duell-/Vergleichsmodus

**Lücke bestätigt:** Alle geprüften Apps sind Solo-Coaching-Tools ohne soziale
Komponente. Ein Modus, bei dem zwei Nutzer dasselbe Motiv fotografieren und die App
(oder die Community) blind bewertet, welche Komposition besser gelungen ist, existiert
in keiner der geprüften Apps.

**Machbarkeit: Mittel.** Technisch simpel in Bezug auf KI (nutzt den ohnehin
vorhandenen Ästhetik-Score), der Aufwand steckt im Drumherum: Accounts, Matching
zweier Nutzer, Foto-Sharing/Backend, Moderation gegen Missbrauch. Eher ein
Spätphasen-Feature mit Server-Infrastruktur, weniger ein ML-Problem.

### Priorisierungs-Empfehlung

Bestes Aufwand-Nutzen-Verhältnis für echte Differenzierung: **6.4 (erklärbares
Overlay)** und **6.1 (Textbasiertes Coaching)** — beide bauen direkt auf den in Phase 1
ohnehin vorhandenen Vision-Framework-Daten auf, brauchen kein neues Modell, und keine
der geprüften Konkurrenz-Apps deckt sie ab. 6.3 (Story-Coach) passt inhaltlich am
besten zu deinem eigenen Content-Ziel und ist ebenfalls ohne große KI-Neuentwicklung
machbar. 6.2 und 6.5 sind spannend, aber eher etwas für eine spätere Version, wenn der
Kern-MVP läuft und es echte Nutzer/Nutzungsdaten gibt.

## Gesamtfazit

Realistisch umsetzbar, mit klarem Weg von "einfach machbar" (Variante 1, Apple-Bordmittel)
zu "anspruchsvoll aber bewiesen machbar" (Variante 2, siehe LiveCompose als Referenz).
Empfehlung bleibt: mit Variante 1 starten, LiveCompose-Code als Lernressource für einen
späteren Ausbau in Richtung Variante 2 nutzen. Für echte Differenzierung gegenüber der
bereits aktiven Konkurrenz lohnt sich danach vor allem ein Blick auf Abschnitt 6.4
(erklärbares Overlay) und 6.1 (Barrierefreiheit) — beides Nischen, die aktuell niemand
bedient, obwohl die technische Basis dafür in Phase 1 bereits entsteht.
