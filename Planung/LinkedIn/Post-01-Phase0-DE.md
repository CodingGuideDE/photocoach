# LinkedIn Post #1 — Phase 0 abgeschlossen (Deutsch)

Status: **Entwurf**
Bezug: Phase 0 (Projekt-Setup) aus `Planung/Plan-zur-Umsetzung.md`
Ton: Build in Public, ehrlich, technisch konkret. Keine Emojis.

---

Ich habe keine Ahnung von Fotografie. Also baue ich mir eine App, die es mir beibringt.

Meine Fotos sind langweilig: schiefer Horizont, Motiv brav in der Mitte. Statt einen Fotokurs
zu machen, baue ich eine App, die mir schon im Sucher sagt, was nicht stimmt und warum.

Phase 0 steht: eine Codebase für iOS und Android (Kotlin Multiplatform + Compose
Multiplatform), Kamera-Vorschau und Aufnahme laufen auf Android, iOS startet im Simulator.
Kamera und Bildanalyse sind pro Plattform nativ (CameraX + ML Kit, AVFoundation + Vision),
liefern aber in ein gemeinsames Datenmodell. Die Kompositionsregeln kennen dadurch keine
einzige Plattform-API und lassen sich als Unit-Test prüfen statt am Gerät.

Am meisten gelernt habe ich beim Android-Manifest. Die App läuft komplett on-device, trotzdem
standen dort Berechtigungen, die ich nie angefragt hatte: eine Kamera-Abhängigkeit brachte
über drei Ecken ACCESS_NETWORK_STATE mit, und die Schreibberechtigung für die Galerie zieht
automatisch unbeschränkten Lesezugriff auf die gesamte Fotobibliothek nach. Beides hätte im
Play-Store-Eintrag gestanden. Ein Datenschutz-Versprechen ist nicht das, was im README steht,
sondern das, was nach dem Manifest-Merge übrig bleibt.

Als Nächstes: die erste echte Kompositionsregel, Drittelregel live im Sucher.

Falls jemand hier Kotlin Multiplatform schon mal mit Kamera und On-Device-ML kombiniert hat:
Ich nehme jeden Hinweis, bevor ich in die nächste Falle laufe.

#BuildInPublic #KotlinMultiplatform #AndroidDev #iOSDev #OnDeviceML
