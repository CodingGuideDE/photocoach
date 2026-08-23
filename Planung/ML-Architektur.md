# ML-Systeme & Architektur

Recherche-Stand: 23.08.2026
Ergänzt [Umsetzbarkeit.md](./Umsetzbarkeit.md) und [Plan-zur-Umsetzung.md](./Plan-zur-Umsetzung.md).
Grundlage: alles läuft on-device (Begründung siehe Abschnitt 8).

---

## 1. Kurzfassung der Recherche

Vier Erkenntnisse, die den bisherigen Plan spürbar verändern:

1. **Phase 6 braucht kein Reinforcement Learning.** Google hat 2021 gezeigt, dass sich
   Richtungsvorschläge als normale überwachte Klassifikation lösen lassen — die
   Trainingsdaten erzeugt man sich selbst mit Pseudo-Labels. Das ist für einen
   Einzelentwickler eine völlig andere Hausnummer als ein RL-Setup.

2. **AVA/NIMA ist für dich der falsche Datensatz.** Es gibt mit **CADB** einen Datensatz,
   der speziell *Komposition* bewertet statt allgemeiner Bildqualität. AVA vermischt
   Komposition mit Belichtung, Motivwahl und Nachbearbeitung — für eine Kompositions-App
   ist das Rauschen.

3. **Ein geteilter Backbone statt vier Einzelmodelle.** Apple selbst fährt genau diese
   Architektur im System (16,4 MB für *alle* Vision-Aufgaben zusammen). Das ist für dich
   die entscheidende Design-Entscheidung.

4. **Das „gilt die Regel hier überhaupt?"-Problem ist lösbar.** Es gibt einen Datensatz
   (KU-PCP) für Kompositions-*Typ*-Klassifikation. Damit erkennt die App erst, welche
   Komposition die Szene überhaupt trägt, und wendet dann die passende Regel an — statt
   immer stur die Drittel-Regel zu predigen.

---

## 2. Teil A — Alle benötigten ML-Systeme

| # | System | Zweck | Typ | Phase |
|---|---|---|---|---|
| **S1** | Saliency / Motiv-Maske | Wo schaut das Auge hin? Basis für fast alles | Gelerntes Modell | 1 |
| **S2** | Gesichts-/Personendetektion | Porträt-Regeln, Blickraum | Plattform-Bordmittel | 1 |
| **S3** | Horizont & Neigung | Schiefe erkennen | **Kein ML** — Sensor + klassische CV | 1 |
| **S4** | Kompositions-Typ-Klassifikation | Welche Regel passt zu dieser Szene? | Gelerntes Modell, 9 Klassen | 2 |
| **S5** | Kompositions-Score | Wie gut ist die Komposition? | Gelerntes Modell, Verteilung | 2 |
| **S6** | View-Adjustment | „geh nach links", „geh tiefer" | Gelerntes Modell, 3 Köpfe | 4 |
| **S7** | Personalisierungs-Adapter | Individueller Geschmack | Winziger Zusatz-Layer, on-device | später |
| **S8** | Shot-Type + Farbpalette | Story-/Serien-Coach | **Kein ML** — klassische Bildverarbeitung | 5 |
| **S9** | Sprachliche Erklärung | Ausformulierte Tipps | LLM, optional | optional |

**Wichtig: Auch das textbasierte Coaching (Phase 3) braucht kein Modell.** Die
Kurzanweisungen („Person etwas weiter rechts, näher rankommen") entstehen als Textbausteine
aus den Regel-Ergebnissen in `coaching/` — S9 ist nur für ausformulierte *Erklärabsätze*
auf Knopfdruck gedacht und bleibt optional.

**Wichtig: S3 und S8 sind ausdrücklich kein ML.** Horizont bekommst du auf iOS geschenkt
(`VNDetectHorizonRequest`) und auf Android aus dem Rotationsvektor; als plattformneutrale
Ergänzung reicht eine Hough-Transformation für dominante Linien. Shot-Type ist ein
Flächenverhältnis, dominante Farben sind ein k-Means über Pixel. Wer das mit einem
neuronalen Netz löst, verbrennt Zeit und Akku.

**Von den neun Systemen musst du also genau vier Modelle trainieren oder beschaffen:**
S1, S4, S5, S6. Und die teilen sich, wie unten gezeigt, einen gemeinsamen Backbone.

---

## 3. Teil B — Was die Recherche hergibt

### 3.1 NIMA (Talebi & Milanfar, Google, 2017) — der Klassiker, aber nicht dein Ziel

Ersetzt die letzte Schicht eines Standard-CNN durch eine 10-Neuronen-Schicht mit Softmax
und sagt damit die *Verteilung* der Bewertungen voraus statt eines Einzelwerts. Trainiert
mit **squared Earth Mover's Distance (EMD)** — die Idee dahinter: bei geordneten Klassen
(1–10 Sterne) soll ein Fehler um zwei Stufen stärker bestraft werden als einer um eine
Stufe, was normale Cross-Entropy nicht leistet.

Die MobileNet-Variante: **3,22 Mio. Parameter, 1,29 GFLOPs, 30,45 ms auf CPU.**
Eingabe 256×256, davon zufällige 224×224-Crops.

**Übernehmen:** Verteilungs-Vorhersage + EMD-Loss. Das ist der wichtigste einzelne
Trick aus der Literatur — siehe Abschnitt 4.4, warum das für dich Gold wert ist.
**Nicht übernehmen:** AVA als Datensatz für den Kompositions-Score.

### 3.2 CADB + SAMP-Net (Zhang et al., 2021) — der eigentlich passende Ansatz

**CADB (Composition Assessment DataBase):** 9.497 Bilder, jedes von **fünf Kunst-Fachleuten**
auf einer 1–5-Skala **ausdrücklich nach Komposition** bewertet. Aufteilung 8.547 / 950.
Deutlich kleiner als AVA, aber unvergleichlich besser auf dein Problem zugeschnitten.

**SAMP-Net** (im Paper mit ResNet18-Backbone, 7×7-Feature-Map bei 224×224-Eingabe) bringt
die für dich interessanteste Idee: **Saliency-Augmented Multi-Pattern Pooling.** Statt die
Feature-Map global zu poolen, wird sie nach **acht klassischen Kompositions-Mustern**
gepoolt — symmetrische Balance, Diagonale, Zentrum, Drittel-Regel. Die Saliency-Map wird
auf 56×56 heruntergerechnet und pro Partition an den Feature-Vektor gehängt. Ein gelerntes
Softmax-Gewicht entscheidet dann, **welches Muster für dieses Bild relevant ist**.
Loss: gewichteter EMD plus MSE für Hilfs-Attribute.

**Das ist die architektonische Antwort auf dein Erklärbarkeits-Ziel (6.4)** — dazu
Abschnitt 4.5.

### 3.3 Camera View Adjustment Prediction (Google, 2021) — ersetzt deine Phase 6

Das direkt einschlägigste Paper. Löst exakt deine „Variante 2", und zwar ohne RL.

**Architektur:** MobileNet-Backbone, Eingabe 299×299, danach eine Spatial-Pyramid-Pooling-
Schicht (1×1, 2×2, 5×5) und zwei Fully-Connected-Schichten mit je 1.024 Einheiten.
Darauf **drei getrennte Köpfe**:

1. **Suggestion-Head** — binär: ist überhaupt eine Korrektur nötig?
2. **Adjustment-Head** — 8 Klassen: links/rechts, hoch/runter, rein-/rauszoomen,
   Drehung im/gegen Uhrzeigersinn
3. **Magnitude-Head** — acht getrennte Regressoren für das *Ausmaß* (Prozent der Bildgröße
   bei Verschiebung/Zoom, Radiant bei Drehung)

**Der entscheidende Trick — zweistufiges Training mit Pseudo-Labels:**

- *Stufe 1:* Ein Kompositions-Bewertungsmodell wird mit Pairwise-Ranking-Loss
  `max(0, δ + M(I_schlecht) − M(I_gut))` trainiert. Annahme: professionell
  veröffentlichte Bilder (Unsplash) sind gut komponiert, beschnittene Varianten davon
  schlechter. **Das braucht keine manuellen Labels.**
- *Stufe 2:* Für jedes unbeschriftete Bild werden **72 simulierte Kamera-Bewegungen**
  durchgerechnet und mit dem Modell aus Stufe 1 bewertet. Die beste Bewegung wird zum
  Label — sofern die Verbesserung über einem Schwellwert (Δ = 0,2) liegt. Damit
  entsteht ein beliebig großer Trainingsdatensatz aus reinen Bildersammlungen.

**Ergebnis:** IoU 0,75 gegenüber 0,61 für die beste Crop-Methode. In der Nutzerstudie
verbesserten die Vorschläge die Komposition in **79 % der Fälle**.

**Ehrlicher Blick auf die Schwachstelle:** Der F1-Score liegt bei 0,608 insgesamt, aber
nur **0,221 für die reine Richtungsvorhersage** bei 0,3 FPR. Das heißt im Klartext: das
Modell erkennt gut, *dass* etwas nicht stimmt, aber die konkrete Richtung trifft es oft
nicht. Für dich folgt daraus eine Design-Regel — siehe Abschnitt 4.6.

### 3.4 LiveCapture — was die Referenz-App tatsächlich macht

Der Blick in das MIT-lizenzierte Repo bestätigt die Linie und liefert ein Detail, das
in Umsetzbarkeit.md noch offen war. Zwei CoreML-Stufen:

1. **BBox-Modell:** 224×224 RGB rein, normierte Box `[cx, cy, w, h]` raus
2. **Actor-Modell:** bekommt den Ausschnitt, gibt **7 Aktions-Wahrscheinlichkeiten**
   aus (still / links / rechts / hoch / runter / zoom in / zoom out)

Die Modelle heißen `AdacropStudentBBox`/`AdacropStudentActor` (Modus „Fast") und
`AdacropTeacherBBox`/`AdacropTeacherActor` (Modus „Pro"). **Das ist Teacher-Student-
Distillation im Auslieferungszustand** — die App liefert beide Modelle mit und lässt
den Nutzer zwischen schnell und genau wählen. Ein Muster, das du direkt übernehmen kannst.

Die eigentliche Bewegungsführung läuft dann nicht im Modell, sondern in klassischer
Logik obendrauf: Gyroskop-Daten (`CMAttitude`) plus ein gedämpftes Tracking mit
adaptiver Verstärkung und „magnetischem" Einrasten. **Das Modell liefert nur das Ziel,
die flüssige Führung ist Physik-Code.** Wichtige Einordnung: Der gefühlte Qualitäts-
unterschied solcher Apps steckt oft weniger im Modell als in dieser Glättungsschicht.

### 3.5 KU-PCP — Kompositions-Typ-Klassifikation

4.251 Außenaufnahmen (3.169 Training / 1.082 Test), von 18 Personen mit fotografischen
Grundkenntnissen in **neun Klassen** einsortiert: Drittel-Regel, Zentrum, horizontal,
symmetrisch, diagonal, Kurve, vertikal, Dreieck, wiederkehrendes Muster. Nur Labels, die
mindestens die Hälfte der 18 vergeben hat, gelten. Rund 20 % der Bilder haben mehrere
Labels — es ist also **Multi-Label**, nicht Multi-Class.

Das ist der Datensatz, der dein größtes inhaltliches Problem löst.

### 3.6 Apple ANSA — der Beweis, dass der Multi-Head-Ansatz trägt

Apples eigener Scene Analyzer, der hinter dem Vision-Framework steckt:

- **MobileNetV3-Variante, 16 Mio. Parameter, 16,4 MB auf der Platte**
- **Unter 9,7 ms für *alle* Aufgaben zusammen auf einem iPhone 13**, 24,6 MB Spitzen-
  Speicher auf der Neural Engine
- Mehrere aufgabenspezifische Köpfe zweigen **in unterschiedlichen Tiefen** vom
  eingefrorenen Backbone ab: einfache Aufgaben (Filterung) an einer flachen linearen
  Schicht, komplexe (Objektdetektion) tiefer mit eigenen Faltungs-Köpfen
- Backbone kontrastiv auf mehreren hundert Millionen Bild-Text-Paaren vortrainiert,
  danach eingefroren

Explizit genannte Lehren, die für dich gelten: geteilte Backbones amortisieren Rechen-
und Speicherkosten; unter 10 ms ist die Latenzgrenze für interaktive Nutzung; und —
für dein Android-Problem entscheidend — **Faltungsnetze wurden bewusst gegenüber
Transformern bevorzugt, weil nicht jedes Gerät eine Neural Engine hat.**

Bemerkenswert: „aesthetic ranking" ist einer der Köpfe. Apple macht auf dem Gerät
bereits, was du vorhast.

### 3.7 MobileNetV4 (Google, ECCV 2024) — die Backbone-Wahl

Für ein Projekt, das iOS *und* Android bedienen muss, ist das die wichtigste Einzelaussage
der ganzen Recherche: MobileNetV4 ist **auf mobilen CPUs, GPUs, DSPs und dedizierten
Beschleunigern gleichermaßen nahezu Pareto-optimal — eine Eigenschaft, die kein anderes
getestetes Modell zeigt.** Auf CPU rund doppelt so schnell wie MobileNetV3 bei gleicher
Genauigkeit; auf iPhone-13-CoreML auf der Pareto-Front.

Genau dein Fall: Du kannst nicht pro Plattform ein anderes Modell pflegen, und dein
schwächstes Android-Gerät hat keinen brauchbaren Beschleuniger.

**Wichtige Einschränkung:** Nimm die **Conv-Varianten**, nicht die Hybrid-Variante mit
dem „Mobile MQA"-Attention-Block. Der bringt zwar 39 % Beschleunigung, aber
Attention-Operatoren sind quer über CoreML- und LiteRT-Delegates deutlich schlechter
unterstützt — genau das Portabilitätsproblem, das du vermeiden willst.

### 3.8 Personalisierung und Erklärbarkeit — Stand der Forschung

Für **S7** ist der brauchbare Ansatz aus der aktuellen Literatur (federiertes
Präferenzlernen, 2026): Backbone einfrieren, nur einen leichten personalisierten
Scorer-Kalibrierungs-Layer on-device anpassen. Genau der pragmatische Zwischenschritt,
den Umsetzbarkeit.md 6.2 schon vorgeschlagen hat — die Forschung bestätigt ihn.

Für **S9** gibt es mit **MICA** (2026) ein Framework, das Score *und* erklärenden Text
erzeugt, validiert unter anderem auf KU-PCP. Interessant als Fernziel, aber ein
multimodales Sprachmodell gehört nicht in deine Live-Pipeline. Der billigere Weg zu
Erklärbarkeit steht in Abschnitt 4.5.

---

## 4. Teil C — Die vorgeschlagene Architektur

### 4.1 Leitidee

**Ein geteilter Backbone, mehrere leichte Köpfe** — nach dem Vorbild von Apple ANSA.

Deine vier Modelle (S1, S4, S5, S6) arbeiten alle auf demselben Kamerabild und brauchen
alle dieselben Low-Level-Merkmale: Kanten, Regionen, Motivgrenzen. Vier getrennte Modelle
zu laden hieße, diese Arbeit viermal zu machen — bei viermal Speicher und viermal
Rechenzeit. Bei einem Live-Sucher ist das nicht tragbar.

```
                 Kamerabild 224×224
                         │
          ┌──────────────▼──────────────┐
          │   MobileNetV4-Conv-S        │   ← geteilter Backbone
          │   (ImageNet vortrainiert,   │      ~3-4 Mio. Parameter
          │    später eingefroren)      │
          └──┬────────┬────────┬────────┘
             │        │        │
    flache   │        │ tiefe Merkmale
    Merkmale │        │        │
             │        │        │
   ┌─────────▼──┐  ┌──▼─────┐  └──────────┬────────────┐
   │ S1         │  │ SAMP   │             │            │
   │ Saliency-  │  │ Pooling│        ┌────▼─────┐ ┌────▼──────┐
   │ Decoder    │  │ 8 Muster│       │ S4       │ │ S6        │
   │ → 56×56    │  └──┬─────┘        │ Typ      │ │ View-Adj. │
   └─────┬──────┘     │              │ 9 Klassen│ │ 3 Köpfe   │
         │            │              │ Multi-   │ │ (nur bei  │
         │  Saliency  │              │ Label    │ │  Bedarf)  │
         └───────────►│              └──────────┘ └───────────┘
                      │
                 ┌────▼────────┐
                 │ S5 Score    │
                 │ 5-Bin-      │
                 │ Verteilung  │
                 └─────────────┘
                         │
   ══════════════════════▼═══════════════════════════════
      FrameAnalysis  →  domain/ (reine Kotlin-Logik)
```

Der `FrameAnalysis`-Vertrag aus deinem Plan bleibt exakt wie er ist — er bekommt nur
zusätzliche Felder. Deine `expect`/`actual`-Trennung war architektonisch richtig: sie
erlaubt dir, in Phase 1 Plattform-Bordmittel zu nutzen und sie später gegen das eigene
Modell zu tauschen, **ohne eine Zeile in `domain/` anzufassen**.

### 4.2 Vorgeschlagene Erweiterung von `FrameAnalysis`

```kotlin
data class FrameAnalysis(
    // bestehend
    val horizonTiltDegrees: Float?,
    val saliencyRegions: List<SaliencyPoint>,
    val faces: List<FaceRect>,
    val timestampMs: Long,

    // neu — aus S4
    val compositionTypes: Map<CompositionType, Float>,  // Multi-Label, 9 Klassen

    // neu — aus S5
    val scoreDistribution: FloatArray,   // 5 Bins, summiert zu 1.0
    val scoreMean: Float,                // abgeleitet
    val scoreConfidence: Float,          // 1 - normierte Streuung  ← siehe 4.4

    // neu — aus S6, optional
    val adjustment: ViewAdjustment?      // null, wenn Suggestion-Head "nein" sagt
)

enum class CompositionType {
    RULE_OF_THIRDS, CENTER, HORIZONTAL, SYMMETRIC,
    DIAGONAL, CURVED, VERTICAL, TRIANGLE, PATTERN
}

data class ViewAdjustment(
    val direction: Direction,   // 8 Klassen wie im Google-Paper
    val magnitude: Float,       // Anteil der Bildgröße bzw. Radiant
    val confidence: Float
)
```

### 4.3 S4 macht deine Regel-Logik erst richtig

Das ist die inhaltlich wichtigste Änderung gegenüber dem bisherigen Plan. Aktuell prüft
`RuleOfThirdsRule` immer auf Drittel-Regel — auch bei einer streng symmetrischen
Architekturaufnahme, bei der Zentrierung das Richtige wäre. Die App würde den Nutzer
aktiv in die falsche Richtung schicken.

Mit S4 kehrt sich der Ablauf um:

```
1. S4 klassifiziert die Szene  →  z. B. {SYMMETRIC: 0.71, CENTER: 0.44, THIRDS: 0.08}
2. domain/ wählt die passende Regel  →  SymmetryRule statt RuleOfThirdsRule
3. Nur diese eine Regel erzeugt den Hinweis
```

Damit wird aus „die App kennt eine Regel und drückt sie jedem auf" ein System, das erst
liest, was für ein Bild da entsteht. Das ist auch für dein *Lernziel* der bessere Weg:
Der Nutzer erfährt nicht nur „so geht es besser", sondern „das hier ist eine
Symmetrie-Situation" — er lernt die Kategorie, nicht nur die Korrektur.

### 4.4 Die Verteilung ist wichtiger als der Score

NIMA und SAMP-Net geben beide eine *Verteilung* aus, keinen einzelnen Wert. Das ist für
dich kein Detail, sondern die direkte Lösung deines größten Produktrisikos
(„schlechte Vorschläge sind schlimmer als keine"):

- **Schmale Verteilung** → das Modell ist sich einig → Hinweis anzeigen
- **Breite oder zweigipflige Verteilung** → das Modell ist unsicher, die Szene ist
  mehrdeutig → **nichts anzeigen**

Ein Modell, das nur eine Zahl ausgibt, kann nicht zugeben, dass es unsicher ist. Es sagt
dann eben „6,2" — und du hast keine Möglichkeit zu erkennen, dass diese 6,2 geraten sind.
Genau deshalb: EMD-Loss über Verteilungs-Bins, nie MSE auf einen Skalar.

**Konkrete Regel für `CompositionScorer`:** Hinweis nur zeigen, wenn `scoreConfidence`
über einem Schwellwert liegt. Diesen Schwellwert erst nach den ersten Nutzertests
festzurren — er ist der wichtigste einzelne Stellhebel für das Gefühl „die App nervt"
gegenüber „die App hilft".

### 4.5 Erklärbarkeit fällt aus der Architektur heraus ab

Für Feature 6.4 („erklärbares Overlay") brauchst du **kein zusätzliches Modell**. Die
Muster-Gewichte aus dem SAMP-Pooling *sind* die Erklärung:

| Was das Modell intern tut | Was der Nutzer sieht |
|---|---|
| Muster-Softmax legt 0,7 auf „Drittel-Regel" | Drittel-Raster wird eingeblendet |
| Muster-Softmax legt 0,6 auf „Diagonale" | Diagonale Führungslinie wird eingezeichnet |
| Saliency-Kopf liefert 56×56-Karte | Halbtransparente Heatmap |
| S4 sagt `SYMMETRIC: 0.71` | „Symmetrische Szene erkannt" |

Das Overlay zeigt damit nicht irgendeine plausible Visualisierung, sondern **genau die
Größe, auf die das Modell seine Entscheidung gestützt hat**. Das ist der Unterschied
zwischen echter Erklärbarkeit und Dekoration — und der Grund, warum es sich lohnt, S5
als SAMP-Variante zu bauen statt als schlichten Score-Regressor.

### 4.6 S6 richtig einsetzen: Richtung ist unsicherer als Anlass

Aus dem F1-Wert von 0,221 für reine Richtungsvorhersage folgt eine klare Konsequenz:
**verlass dich auf den Suggestion-Head, sei vorsichtig mit dem Adjustment-Head.**

Praktisch heißt das ein dreistufiges Feedback:

1. Suggestion-Head sagt „Korrektur nötig", Richtung unsicher
   → nur ein dezenter Hinweis, dass etwas besser ginge
2. Suggestion- **und** Adjustment-Head übereinstimmend sicher
   → konkreter Richtungspfeil
3. Magnitude nur zur Steuerung der Pfeil-*Intensität* nutzen, nie als Zahl anzeigen
   („geh 23 cm nach links" wäre eine Genauigkeit, die das Modell nicht hat)

Und, aus LiveCapture gelernt: Das Modell darf nur alle 100–200 ms feuern, die *Führung*
dazwischen läuft über gedämpfte Interpolation plus Gyroskop. Sonst zappelt der Pfeil.

### 4.7 Warum das den Speicherrahmen hält

| Komponente | Größe (INT8) | Rechenzeit iPhone | Rechenzeit Android-Mittelklasse |
|---|---|---|---|
| Backbone MobileNetV4-Conv-S | ~4 MB | ~4 ms | ~25 ms |
| S1 Saliency-Decoder | ~2 MB | ~3 ms | ~20 ms |
| S4 Typ-Kopf | < 0,5 MB | < 1 ms | ~2 ms |
| S5 SAMP + Score-Kopf | ~1 MB | ~1 ms | ~4 ms |
| S6 View-Adjustment-Köpfe | ~1 MB | ~2 ms | ~10 ms |
| **Summe** | **~9 MB** | **~11 ms** | **~60 ms** |

Zum Vergleich: Apple erreicht 16,4 MB und < 9,7 ms für ein deutlich breiteres
Aufgabenspektrum. Die Zahlen oben sind Schätzungen aus den Paper-Angaben, keine Messungen
— aber die Größenordnung stimmt, und sie liegt für iOS komfortabel innerhalb deines
100–200-ms-Budgets bei 5–10 Hz.

Auf Android-Mittelklasse ohne Beschleuniger wird es eng. Zwei Gegenmaßnahmen, beide
schon aus der Recherche belegt: das **Bewegungs-Gating** (S1/S5/S6 nur rechnen, wenn das
Gerät ruhig gehalten wird) und das **Teacher-Student-Paar** nach LiveCapture-Vorbild —
ein kleineres destilliertes Modell für schwache Geräte, das größere für starke.

---

## 5. Teil D — Trainings-Pipeline und Daten

### 5.1 Datensätze

| Datensatz | Umfang | Inhalt | Wofür |
|---|---|---|---|
| **CADB** | 9.497 | Kompositions-Score 1–5, 5 Fachleute | S5 — Hauptquelle |
| **KU-PCP** | 4.251 | 9 Kompositionsklassen, Multi-Label | S4 |
| **AVA** | ~255.000 | Allgemeine Ästhetik 1–10 | Nur Vortraining |
| **FCDB / GAICD / CPC** | — | Crop-Annotationen | S6 Stufe 1 |
| **Unsplash / Open Images** | beliebig | Unbeschriftet | S6 Stufe 2, Pseudo-Labels |
| **DUTS / o. ä.** | — | Saliency-Masken | S1 |

Lizenzen vor Nutzung prüfen — insbesondere ob kommerzielle Verwendung erlaubt ist. Das
ist die Rechercheaufgabe, die in Plan-zur-Umsetzung.md Abschnitt 11 schon offen steht;
sie gilt jetzt für alle sechs Zeilen.

### 5.2 Reihenfolge des Trainings

```
Schritt 1  Backbone: ImageNet-Gewichte für MobileNetV4-Conv-S übernehmen
              ↓
Schritt 2  Backbone auf AVA vor-feintunen (allgemeiner Ästhetik-Begriff),
           dann einfrieren  ← ab hier ändert sich der Backbone nicht mehr
              ↓
Schritt 3  S1-Decoder auf Saliency-Daten trainieren
              ↓
Schritt 4  S4-Kopf auf KU-PCP (Multi-Label, BCE-Loss)
              ↓
Schritt 5  S5-Kopf auf CADB (SAMP-Pooling, gewichteter EMD-Loss)
              ↓
Schritt 6  S6 Stufe 1: Ranking-Modell auf Crop-Datensätzen + Unsplash
           (Pairwise-Loss, keine manuellen Labels nötig)
              ↓
Schritt 7  S6 Stufe 2: 72 simulierte Bewegungen pro Bild durchrechnen,
           beste als Pseudo-Label (Schwelle Δ = 0,2), damit die drei
           Köpfe trainieren
              ↓
Schritt 8  Distillation: Teacher → kleineres Student-Modell
              ↓
Schritt 9  INT8-QAT, Export nach CoreML + LiteRT
```

Der eingefrorene Backbone ab Schritt 2 ist wichtig: Danach ist jeder Kopf einzeln und
schnell trainierbar, ohne dass sich die anderen verschlechtern. Du kannst S6 überarbeiten,
ohne S5 neu zu trainieren. Bei einem Feierabend-Projekt entscheidet das darüber, ob du
je fertig wirst.

### 5.3 Realistische Einordnung des Aufwands

| Schritt | Aufwand | Bemerkung |
|---|---|---|
| 1–2 | Stunden | Fertige Gewichte, ein Feintuning-Lauf |
| 3 | ~1 Woche | Oder ganz überspringen (siehe 6.1) |
| 4 | ~3 Tage | Kleiner Datensatz, einfache Aufgabe |
| 5 | ~1–2 Wochen | SAMP-Pooling selbst implementieren |
| 6–7 | ~3–4 Wochen | Die Pseudo-Label-Pipeline ist der Brocken |
| 8–9 | ~1 Woche | Konvertierung und Verifikation auf echten Geräten |

Das ersetzt die „offen (Monate)"-Schätzung für Phase 6 aus dem bisherigen Plan durch
etwas Planbares — weil der RL-Teil komplett entfällt.

### 5.4 Deployment

- **Eine PyTorch-Quelle → zwei Artefakte:** `coremltools` für iOS, LiteRT-Converter
  für Android. Nie zwei getrennte Implementierungen pflegen.
- **INT8 mit Quantization-Aware Training, nicht Post-Training-Quantisierung.**
  Reine PTQ kostet typischerweise 1–3 % Genauigkeit; QAT simuliert das Quantisierungs-
  rauschen schon im Training und schneidet durchweg besser ab. Der Mehraufwand ist ein
  zusätzlicher Trainingslauf.
- **Numerische Verifikation nach der Konvertierung:** Dieselben 200 Testbilder durch
  PyTorch, CoreML und LiteRT schicken und die Ausgaben vergleichen. Konvertierungsfehler
  sind still — das Modell läuft, liefert aber leicht andere Werte, und du suchst den
  Fehler später in der Regel-Logik.
- **Beide Plattformen müssen dieselben Zahlen liefern.** Sonst zerfällt die geteilte
  `domain/`-Logik in zwei faktisch verschiedene Apps mit identischem Code.

---

## 6. Teil E — Empfohlener Weg durch die Phasen

### 6.1 Phase 1 bleibt wie geplant — mit einer Ergänzung

Nutze weiter die Plattform-Bordmittel (Vision auf iOS, ML Kit + Fertigmodell auf Android).
Du kommst schnell zu einer laufenden App, und das ist mehr wert als architektonische
Reinheit.

**Aber:** Miss von Anfang an, **wie unterschiedlich** die beiden Plattformen dieselbe
Szene bewerten. Nimm 50 Testbilder, schick sie durch beide Pipelines, vergleiche die
Saliency-Schwerpunkte. Wenn die Abweichung klein ist, kannst du dauerhaft bei Bordmitteln
bleiben und sparst dir S1 komplett. Wenn sie groß ist, weißt du früh, dass du ein eigenes
S1 brauchst — und nicht erst, wenn Nutzer melden, dass die App auf ihrem Android anders
urteilt als auf dem iPhone eines Freundes.

Das ist billig zu messen und beantwortet eine Frage, die sonst bis Phase 4 offen bleibt.

### 6.2 Vorgezogen: S4 nach Phase 1

Der Kompositions-Typ-Klassifikator ist mit Abstand das beste Aufwand-Nutzen-Verhältnis
im ganzen Katalog: kleiner Datensatz, einfache Aufgabe, wenige Tage Arbeit — und er
behebt den grundlegendsten inhaltlichen Fehler der aktuellen Regel-Logik. Ich würde ihn
**vor** dem Ästhetik-Score angehen.

### 6.3 Phase 2 auf CADB umstellen

Der Plan sieht dort „NIMA-artiges Modell, AVA" vor. Ersetzen durch: **SAMP-artiges Modell
auf CADB.** Gleicher Aufwand, spezifischer auf Komposition trainiert, und du bekommst die
Muster-Gewichte für das erklärbare Overlay geschenkt, statt sie separat bauen zu müssen.

### 6.4 Phase 6 wird zu einer normalen Phase

Bisher: „Optional, offen, Monate, Reinforcement Learning". Neu: überwachtes Lernen mit
selbst erzeugten Pseudo-Labels, etwa 4–5 Wochen, kein RL-Wissen nötig. Damit rückt
Phase 6 aus dem „vermutlich nie"-Bereich in etwas, das du tatsächlich angehen kannst —
und Phase 4 (Heuristik) wird zur sinnvollen Vorstufe, an der du misst, ob sich der
Modell-Aufwand überhaupt lohnt.

---

## 7. Zusammenfassung der Änderungen gegenüber Plan-zur-Umsetzung.md

| Bereich | Bisher | Neu |
|---|---|---|
| Modell-Struktur | Einzelmodelle pro Aufgabe | Ein geteilter Backbone, mehrere Köpfe |
| Backbone | offen | MobileNetV4-Conv-S (nicht die Attention-Variante) |
| Ästhetik-Datensatz | AVA / NIMA | **CADB / SAMP** — AVA nur zum Vortraining |
| Score-Ausgabe | 0–100-Zahl | 5-Bin-Verteilung + abgeleitete Sicherheit |
| Regel-Auswahl | immer Drittel-Regel | **S4 klassifiziert erst den Kompositions-Typ** |
| Erklärbares Overlay | zusätzliche Visualisierung | fällt aus SAMP-Muster-Gewichten ab |
| Phase 6 | Reinforcement Learning, „Monate" | **Überwacht + Pseudo-Labels, ~4–5 Wochen** |
| Android-Schwachgeräte | offen | Teacher-Student-Paar wie LiveCapture |
| Quantisierung | nicht spezifiziert | INT8 mit QAT, nicht PTQ |

**Offene Punkte, die die Recherche nicht beantwortet hat:**

- Lizenzlage von CADB und KU-PCP für kommerzielle Nutzung — vor jedem Trainingslauf klären
- Ob ein eigenes S1 nötig ist oder Bordmittel reichen (Messung aus 6.1 entscheidet)
- Ob S4 auf KU-PCP generalisiert: der Datensatz enthält **ausschließlich Außenaufnahmen**.
  Für Innenräume und Porträts musst du entweder nachlabeln oder S4 dort abschalten.
  Das ist die relevanteste Einschränkung in diesem ganzen Dokument.

---

## 8. Warum on-device (Kurzbegründung)

Live-Analyse im Sucher hat ein Latenzbudget von rund 100 ms; ein Mobilfunk-Roundtrip
verbraucht davon allein 50–150 ms, bevor ein Byte Bild übertragen ist. Dauersendendes
Funken kostet zudem mehr Akku als lokale Inferenz auf der Neural Engine, und die
Situationen mit den besten Motiven (Berge, Ausland, Keller) haben oft kein Netz.

Einzige sinnvolle Cloud-Nutzung: **S9** (ausformulierte Texttipps, nutzerausgelöst) und
das **Training selbst**. Für S9 lohnt sich ein Blick auf Apples Foundation-Models-Framework
(iOS 26) — dann bliebe auch das on-device. Damit ist die offene Entscheidung aus
Plan-zur-Umsetzung.md Abschnitt 11 („Datenschutz-Ansatz") beantwortet: vollständig
on-device, als Verkaufsargument.

---

## 9. Quellen

- Talebi & Milanfar, *NIMA: Neural Image Assessment* — https://arxiv.org/abs/1709.05424
- Zhang et al., *Image Composition Assessment with Saliency-augmented Multi-pattern Pooling* (CADB, SAMP-Net) — https://arxiv.org/abs/2104.03133
- Zhong et al., *Camera View Adjustment Prediction for Improving Image Composition* (Google) — https://arxiv.org/abs/2104.07608
- Chen et al., *Learning to Compose with Professional Photographs on the Web* (VFN) — https://arxiv.org/abs/1702.00503
- Wei et al., *Listwise View Ranking for Image Cropping* (VPN) — https://arxiv.org/abs/1905.05352
- Lee et al., *Photographic composition classification and dominant geometric element detection for outdoor scenes* (KU-PCP) — https://www.sciencedirect.com/science/article/abs/pii/S1047320318301147
- Apple, *A Multi-Task Neural Architecture for On-Device Scene Analysis* (ANSA) — https://machinelearning.apple.com/research/on-device-scene-analysis
- Qin et al., *U²-Net: Going Deeper with Nested U-Structure for Salient Object Detection* — https://arxiv.org/abs/2005.09007
- Qin et al., *MobileNetV4: Universal Models for the Mobile Ecosystem* — https://arxiv.org/abs/2404.10518
- Zeng et al., *Grid Anchor based Image Cropping* (GAICD) — https://arxiv.org/abs/1909.08989
- LiveCompose, *LiveCapture* (MIT) — https://github.com/LiveCompose/LiveCapture
