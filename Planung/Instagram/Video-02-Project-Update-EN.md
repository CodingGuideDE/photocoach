# Instagram Reel #2 — Script (English)

Status: **Draft, ready for review**
Length: ~50-60 seconds
Format: Reel, vertical, talking-head + screen recordings

Follows [Video-01-Script-EN.md](./Video-01-Script-EN.md) — assumes viewers saw the
"I have no idea about photography, building an app anyway" intro.

---

## Hook (0:00–0:05)

**Shot:** You, straight to camera.

**Voiceover:**
> "Last time I said I have no idea how photography works and I'm building an app
> anyway. Quick update: parts of it actually work now."

**On-screen text:**
> APP UPDATE — EPISODE 2

---

## What Actually Works Now (0:05–0:25)

**Shot:** Screen recording, split into two quick halves — phone held up in front
of a tilted picture frame or horizon-like edge, showing the tilt getting picked up
live (even if not visually rendered yet, show the debug counter/value on screen).

**Voiceover:**
> "The camera can now actually tell when I'm holding it crooked — real horizon
> detection, working on both iPhone and Android. It also spots faces in the frame.
> None of this existed two weeks ago; it was all fake placeholder data."

**On-screen text (fade in one by one):**
> ✅ HORIZON TILT — DETECTED IN REAL TIME
> ✅ FACE DETECTION — WORKING
> ✅ FIRST COMPOSITION RULE — BUILT & TESTED

---

## The Honest Bug Moment (0:25–0:38)

**Shot:** You, slightly more casual/self-deprecating tone. Maybe show a sideways
or upside-down test photo as a visual gag.

**Voiceover:**
> "It's not all smooth — turn your phone while the app is open and it currently
> saves the photo sideways. Classic. Building this thing is a lot of 'it works!'
> immediately followed by 'wait, why is this upside down.'"

**On-screen text:**
> KNOWN BUG: ROTATION 🙃 (fixing it)

---

## What's Next (0:38–0:50)

**Shot:** Screen recording of the debug overlay (numbers/badge showing tilt +
face count), then a quick sketch of what it'll look like once it's an actual
visual overlay instead of debug numbers.

**Voiceover:**
> "Right now all of this is just numbers on a debug screen — next step is turning
> that into actual feedback you'd see while framing a shot. That's the part that
> makes this an actual coach instead of a science experiment."

**On-screen text:**
> NEXT: TURNING DATA INTO REAL FEEDBACK

---

## Closing / CTA (0:50–0:58)

**Shot:** You, straight to camera, warm close.

**Voiceover:**
> "Still don't know if this whole thing is gonna be any good. But it's further
> along than a week ago, and that's the whole point of showing this stuff."

**On-screen text:**
> BUILD IN PUBLIC — EPISODE 2

---

## Caption (for the post)

Update on the "I know nothing about photography but I'm building an app for it" project 👇

This week: real horizon-tilt detection on both iPhone and Android, real face detection, and the first actual composition rule — with automated tests, not just vibes.

Also found my first genuinely funny bug: rotate the phone mid-shot and the photo saves sideways. Building in public means showing the broken parts too.

What should episode 3 cover — the bug fixes, or the first real on-screen feedback? 👇

#BuildInPublic #IndieApp #KotlinMultiplatform #AppDev #TeenDev #CodingJourney #TechTok

---

## Production Notes

- Keep the bug section — it's the most relatable beat in the video, don't cut it
  for looking "unprofessional." Authenticity is the point of this format.
- If a debug badge/counter is on screen, zoom in enough that it's actually readable
  on a phone screen, not just a tiny corner element.
- Don't over-explain technical terms (CameraX, ML Kit, etc.) in voiceover — the
  on-screen text carries the technical proof, the voiceover stays plain-language.

## Review Questions for You

1. Comfortable showing the rotation bug on camera, or keep it text-only?
2. Do you have actual footage of the debug badge running, or should this wait
   until the visual overlay (Plan Phase 2) exists for a more "showable" demo?
3. Same closing tone as Episode 1 ("no promises, just my best effort") — keep
   that as a running theme across episodes, or vary it?

---

## Full Script (for editing)

Last time I said I have no idea how photography works and I'm building an app
anyway. Quick update: parts of it actually work now.

The camera can now actually tell when I'm holding it crooked — real horizon
detection, working on both iPhone and Android. It also spots faces in the frame.
None of this existed two weeks ago; it was all fake placeholder data.

It's not all smooth — turn your phone while the app is open and it currently
saves the photo sideways. Classic. Building this thing is a lot of "it works!"
immediately followed by "wait, why is this upside down."

Right now all of this is just numbers on a debug screen — next step is turning
that into actual feedback you'd see while framing a shot. That's the part that
makes this an actual coach instead of a science experiment.

Still don't know if this whole thing is gonna be any good. But it's further
along than a week ago, and that's the whole point of showing this stuff.
