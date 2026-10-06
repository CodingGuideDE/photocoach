# Instagram Reel #4 — Script (English)

Status: **Draft, ready for review**
Length: ~35-40 seconds (~95 words)
Format: Reel, vertical, talking-head + screen recordings from the phone

Build-in-public **Episode 3** — follows [Video-02-Project-Update-EN.md](./Video-02-Project-Update-EN.md).
**Stands on its own:** don't assume viewers have seen earlier episodes. The premise ("no clue
about photography, building an app that teaches me") is restated in the hook, in one line.

Core story of this episode: **the app said "looks good" to almost every photo — because it
couldn't actually see anything.** That's the hook, the bug and the lesson in one.

---

## Hook (0:00–0:07)

**Shot:** You, straight to camera, slightly accusing tone. Phone in hand, screen towards
the camera showing a green "Composition looks good".

**Voiceover:**
> "I know nothing about photography, so I'm building an app that teaches me. And it just
> lied to me."

**On-screen text:**
> MY APP WAS LYING TO ME

---

## The Bug (0:07–0:16)

**Shot:** Screen recording: point the camera at a blank wall — green check. Hold the phone
crooked — it complains about the horizon. Straighten it — green again.

**Voiceover:**
> "It said 'looks good' on every photo. Even a blank wall. It only checked whether I held
> the phone straight — everything else, it couldn't see."

**On-screen text:**
> STRAIGHT HORIZON = "PERFECT" 🤡

---

## The Fix (0:16–0:29)

**Shot:** Quick cuts: (1) the score badge with a number and an orange hint like "Subject
too far left — pan left", (2) the hint changing as you move the phone, (3) a blank wall now
showing grey "No clear subject".

**Voiceover:**
> "So I taught it to spot what stands out in a picture. Now it gives a score out of 100,
> tells me where my subject should go — and when it can't tell, it admits it."

**On-screen text (fade in one by one):**
> ✅ SCORE 0–100
> ✅ WHERE TO PUT THE SUBJECT
> ✅ HONEST "NO CLEAR SUBJECT"

---

## Quick Win + CTA (0:29–0:38)

**Shot:** Two-second flash of the thumbnail → full-screen photo → swipe away, then you,
straight to camera.

**Voiceover:**
> "Also new: photo viewer, zoom, pro mode. Next up: a real-world test. Would you trust an
> app that rates your photos?"

**On-screen text:**
> NEXT: REAL-WORLD TEST 📸
> BUILD IN PUBLIC — EPISODE 3

---

## Caption (for the post)

I know nothing about photography, so I'm building an app that teaches me. This week it told me almost every shot was "good" 🤡

Turns out it was only checking whether I held the phone straight. Everything else it couldn't see — and instead of saying "I don't know", it said "perfect".

Fixed: it now spots what stands out in the picture (no AI model, just a math trick from a 2007 paper), gives a real score out of 100, and honestly says "no clear subject" when it can't tell.

Also new: photo viewer, zoom, pro mode. Next: a real-world test. Would you trust an app that rates your photos? 👇

#BuildInPublic #IndieApp #KotlinMultiplatform #AppDev #TeenDev #CodingJourney #Photography

---

## Production Notes

- **Record the "before" first.** The hook only works if the old behaviour is on screen.
  The old version is still in git (last commit before the scoring changes) — or simply
  re-enact it with the screen showing the green check on a blank wall. Don't fake a
  screen you can't show for real.
- **Film on the real phone, not the emulator.** The emulator's virtual living room looks
  obviously fake and undermines the "it works now" beat.
- Don't say "spectral residual" or "FFT" in the voiceover — if anyone asks in the
  comments, that's a great reply. (The "2007 paper" detail now lives in the caption only.)
- At ~35-40 s the pace is tight: cut the quick-win flash first if it runs long.
- Wording on-screen: the app's hints are in German. Either show them as they are (authentic,
  add an English subtitle) or switch the phone language for the shot — but don't mix both.

## Honesty Check (what's verified vs. not)

- Scoring and subject detection: built and tested, seen running in the emulator — **not yet
  tested on real-world photos**. That's why the closing says "next: real test". Don't claim
  it "works great" before the field test.
- iOS is paused (Android only for now). Don't say "on iPhone and Android".
- Zoom and pro mode are emulator-tested; pinch-zoom and ultra-wide haven't been seen on a
  real device yet.

## Review Questions for You

1. Is one line of premise in the hook enough for newcomers, or do you want a short
   "Ep. 3 of building this in public" on screen as well?
2. The empty-space warning and the sideways-photo fix were cut for length — fine to leave
   them for the caption/comments, or should one of them stay in?
3. Do you want to show the real score number on camera even if it's a low one? A bad
   score on your own photo would be a fun, self-deprecating beat.

---

## Full Script (for editing)

I know nothing about photography, so I'm building an app that teaches me. And it just
lied to me.

It said "looks good" on every photo. Even a blank wall. It only checked whether I held the
phone straight — everything else, it couldn't see.

So I taught it to spot what stands out in a picture. Now it gives a score out of 100,
tells me where my subject should go — and when it can't tell, it admits it.

Also new: photo viewer, zoom, pro mode. Next up: a real-world test. Would you trust an
app that rates your photos?
