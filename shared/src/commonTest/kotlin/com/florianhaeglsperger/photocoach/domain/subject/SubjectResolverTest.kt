package com.florianhaeglsperger.photocoach.domain.subject

import com.florianhaeglsperger.photocoach.domain.CENTER
import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.subjectAt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubjectResolverTest {

    private val resolver = DefaultSubjectResolver

    @Test
    fun `ohne Gesicht und ohne Saliency gibt es kein Motiv`() {
        assertNull(resolver.resolve(frame()))
    }

    @Test
    fun `ein Gesicht liefert seinen Mittelpunkt`() {
        val subject = resolver.resolve(frame(faces = listOf(faceAt(0.25f, 0.4f))))

        assertNotNull(subject)
        assertEquals(0.25f, subject.x, 0.001f)
        assertEquals(0.4f, subject.y, 0.001f)
        assertEquals(SubjectSource.FACE, subject.source)
        assertEquals(1f, subject.confidence)
    }

    @Test
    fun `das groesste Gesicht gewinnt`() {
        val subject = resolver.resolve(
            frame(faces = listOf(faceAt(0.2f, 0.2f, size = 0.15f), faceAt(0.8f, 0.8f, size = 0.3f))),
        )

        assertEquals(0.8f, assertNotNull(subject).x, 0.001f)
    }

    @Test
    fun `zu kleines Gesicht zaehlt nicht als Motiv`() {
        // Unter MIN_FACE_WIDTH — auf Android filtert ML Kit das schon weg, auf iOS nicht.
        // Ohne diese Huerde liefen die Plattformen hier auseinander.
        val small = faceAt(CENTER, CENTER, size = DefaultSubjectResolver.MIN_FACE_WIDTH / 2f)

        assertNull(resolver.resolve(frame(faces = listOf(small))))
    }

    @Test
    fun `Gesicht schlaegt Saliency`() {
        val subject = resolver.resolve(
            frame(
                saliency = listOf(subjectAt(0.2f, 0.2f)),
                faces = listOf(faceAt(0.7f, 0.7f)),
            ),
        )

        assertNotNull(subject)
        assertEquals(SubjectSource.FACE, subject.source)
        assertEquals(0.7f, subject.x, 0.001f)
    }

    @Test
    fun `ein Saliency-Klumpen liefert seinen gewichteten Schwerpunkt`() {
        val subject = resolver.resolve(
            frame(saliency = listOf(subjectAt(0.30f, 0.5f), subjectAt(0.34f, 0.5f))),
        )

        assertNotNull(subject)
        assertEquals(SubjectSource.SALIENCY, subject.source)
        assertEquals(0.32f, subject.x, 0.001f)
    }

    @Test
    fun `zwei getrennte Motive ergeben keinen Punkt in der Mitte`() {
        // Der klassische Fehler: der Schwerpunkt ueber *alle* Punkte laege bei 0.5 —
        // also genau dort, wo nachweislich kein Motiv ist.
        val subject = resolver.resolve(
            frame(saliency = listOf(subjectAt(0.25f, 0.5f), subjectAt(0.75f, 0.5f))),
        )

        assertNotNull(subject)
        assertTrue(
            abs(subject.x - CENTER) > 0.2f,
            "Motivpunkt in der Bildmitte gelandet: ${subject.x}",
        )
    }

    @Test
    fun `gleichmaessig verteilte Saliency ergibt kein Motiv`() {
        // Der Fall "weisse Wand": Vision normiert auf das Frame-Maximum, also hat auch hier
        // die staerkste Zelle Gewicht 1.0 und die Liste ist alles andere als leer.
        // Erkennbar ist es nur daran, dass sich nichts zusammenballt.
        val spread = (0..4).flatMap { row ->
            (0..4).map { column -> subjectAt(0.1f + column * 0.2f, 0.1f + row * 0.2f) }
        }

        assertNull(resolver.resolve(frame(saliency = spread)))
    }

    @Test
    fun `Konfidenz sinkt — wenn ein zweites Motiv mitkonkurriert`() {
        val alone = resolver.resolve(frame(saliency = listOf(subjectAt(0.25f, 0.5f))))
        val contested = resolver.resolve(
            frame(saliency = listOf(subjectAt(0.25f, 0.5f), subjectAt(0.75f, 0.5f))),
        )

        assertTrue(assertNotNull(alone).confidence > assertNotNull(contested).confidence)
    }
}
