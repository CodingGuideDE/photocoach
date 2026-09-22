package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.rules.Hint
import com.florianhaeglsperger.photocoach.domain.rules.HorizonRule
import com.florianhaeglsperger.photocoach.domain.rules.PortraitFramingRule
import com.florianhaeglsperger.photocoach.domain.rules.RuleOfThirdsRule
import com.florianhaeglsperger.photocoach.domain.rules.ThirdsTargetTracker
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import com.florianhaeglsperger.photocoach.domain.subject.SubjectResolver

/**
 * Waehlt aus allen zutreffenden Regel-Hinweisen **genau einen** aus (Plan 3.2: "nur 1
 * Hinweis gleichzeitig anzeigen, wichtigsten zuerst — sonst Overload").
 *
 * Das ist der erste Teil dessen, was der Plan `CompositionScorer` nennt. Der zweite Teil —
 * ein 0-100-Live-Score — fehlt bewusst noch: dafuer muessten die Regeln melden, *wie stark*
 * sie verletzt sind, und sie liefern bisher nur "Hinweis oder nicht". Das nachzuruesten
 * aendert jede Regel und gehoert in einen eigenen Schritt.
 *
 * ## Die Rangfolge und warum sie so herum ist
 *
 * 1. **Portraet-Rahmung** — ein angeschnittener Kopf ist der einzige Fehler hier, der sich
 *    hinterher *nicht* reparieren laesst. Was weg ist, ist weg.
 * 2. **Horizont** — objektiv falsch und eindeutig benennbar, aber notfalls durch Drehen und
 *    Beschneiden zu retten. Kostet dann Bildrand.
 * 3. **Drittel-Regel** — die weichste der drei. Eine Empfehlung, keine Regel, und je nach
 *    Motiv auch bewusst verletzbar.
 *
 * Kurz: nach *Reparierbarkeit* sortiert, nicht nach Auffaelligkeit. Wer nur einen Hinweis
 * geben darf, sollte den geben, den man im Nachhinein nicht mehr befolgen kann.
 *
 * Haelt Zustand (ueber [ThirdsTargetTracker]) und ist deshalb eine Klasse, kein `object` —
 * pro Sucher-Sitzung eine Instanz.
 */
class HintSelector(
    private val subjectResolver: SubjectResolver = DefaultSubjectResolver,
    private val thirdsTracker: ThirdsTargetTracker = ThirdsTargetTracker(),
) {

    fun select(analysis: FrameAnalysis): Hint? {
        // Zuerst auswerten, nicht zuerst priorisieren: der Tracker braucht jedes Frame,
        // sonst veraltet seine Hysterese, sobald laengere Zeit eine hoeher priorisierte
        // Regel greift.
        val thirdsHint = evaluateThirds(analysis)

        return PortraitFramingRule.evaluate(analysis)
            ?: HorizonRule.evaluate(analysis)
            ?: thirdsHint
    }

    private fun evaluateThirds(analysis: FrameAnalysis): Hint? {
        val subject = subjectResolver.resolve(analysis)
        if (subject == null) {
            // Ohne erkennbares Motiv den Tracker zuruecksetzen: sonst haelt er einen
            // Zielpunkt fest, der zur naechsten Szene keinen Bezug mehr hat, und der
            // erste Hinweis danach zeigt in die falsche Richtung.
            thirdsTracker.reset()
            return null
        }
        val target = thirdsTracker.select(subject.x, subject.y, analysis.aspectRatio)
        return RuleOfThirdsRule.evaluate(analysis, target)
    }
}
