# Lore and vocabulary bible

The fiction that every system spec builds on, and the rules for naming things. Term definitions live in [`CONTEXT.md`](../../CONTEXT.md). This file holds the decisions and rules behind them.

Resolved in [Lore and vocabulary bible](https://github.com/andrewbrogoff/voices-from-the-void/issues/9).

## Tone

The tone ramps with progression. Each tier's art, sound and writing follows the stage the player has reached.

1. **Early game: occult natural philosophy.** A scholar with brass, glass and notebooks, driven by measured curiosity.
2. **Mid game: melancholy wonder.** The Void is vast, sad, beautiful and dangerous.
3. **Deep warp and endgame: cosmic horror.** The player arrives at horror; the mod doesn't start there.

All listening is **acoustic**: tuning forks, bells, resonating glass, ear-trumpets. Never use radio, telescope or signal-processing imagery. That keeps us clear of *Voices of the Void* (mrdrnose, 2023), a game about a radio-telescope operator whose title is close to ours.

## Cosmology

- **The world is resonance.** Aspects are its notes and vis is its free energy.
- **The Void is the silence beneath it.** It isn't somewhere you visit by default; it lies under every place.
- **The Voices are what the Choir became.** The Choir was an earlier order of scholars who listened too long and went in. They aren't malicious, but they are no longer human, and they want to be heard. Whether joining them is doom or release is **deliberately left unresolved**.
- **Choir ruins** are scattered across the world. They are the source of Choir pages and the TC4-obelisk-equivalent structures, and they lead toward wherever the Choir went.
- **The Answerers** are living would-be listeners who speak back to the Voices without understanding them. They are hostile, they are found at Choir sites, and they are the mid-game threat with a human face. Their roster and mechanics belong to the worldgen and mobs spec.

## Listening and warp

- **Listening is always a deliberate act**, done through an instrument or a rite. It never happens to the player.
- **What it pays:** knowledge (research hints, aspect insights) and power (Void-tier crafting, opt-in bargains).
- **What it costs: warp**, meaning *the Void can now hear you back*. Warp grows through listening and Void-tier work. The more you have, the more you *sound like them*, and the more the Voices intrude. The permanent share of warp is "what you can never un-hear".
- **Never a hidden tax.** Warp is only ever the consequence of a choice the player can see. Its numbers, decay, cures and effects belong to the warp spec. They must keep warp chosen, legible and bounded, as the [community pain points research](https://github.com/andrewbrogoff/voices-from-the-void/issues/5) requires.

## Aspects

- **Vacuos is a hidden primal.** At first the player is taught that it is the compound Aer + Perditio. Listening reveals that it is fundamental, and that the other primals sit *on top of* it. This is the mod's central revelation, and it is the first time a Choir page contradicts the player's own notes.
- The rest of the aspect tree (its pairs and tiers) is redesigned in the aspects spec. All aspects keep Latin names.

## The Hush (replaces taint)

Flux is the spilled residue of magic. Where it pools, the world thins until the Void seeps through and the land goes **quiet**: colour drains, sound dies away, and things hollow out. The Hush looks desaturated and silent, never like TC4's purple, fleshy goo. Its spread, budgets and counterplay belong to the taint and flux spec.

## Vocabulary policy

**Kept unchanged** (generic or Latin terms): aspect, all Latin aspect names, primal, compound, vis, essentia, warp, flux, node, wand, focus/foci, golem, crucible, infusion, arcane (as in arcane crafting), and eldritch (an adjective only, never a proper noun).

**Replacements for Azanor's coinages:**

| TC4 | Ours |
|---|---|
| Thaumonomicon | Commonplace Book |
| Thaumometer | Tuning Fork |
| Thaumium | Resonant Brass (made from copper) |
| Thaumaturge | Listener |
| Taint | the Hush |
| Crimson Cult | the Answerers |

**Naming register for everything else:**

- Plain, evocative English for everyday things.
- Latin for aspects only.
- Void-tongue for eldritch-tier things only.

Each pillar spec names its own content under this policy and adds the new terms to `CONTEXT.md`. Never invent new `Thaum-`-style Latin or Greek neologisms.

## The Commonplace Book (the narrator)

- **The writer** is the player character. It is their own research notebook, written in the first person, and it fills in as they learn.
- **Authored decline.** Each progression stage's entries are written in a more unravelled voice than the last. Later entries become inconsistent with earlier ones.
- **Live-warp overlay.** While warp is high, marginalia, crossings-out and handwriting that isn't the player's appear on the pages. They recede when warp drops, so the notebook doubles as a diegetic warp gauge.
- **Choir pages** are found in ruins and tipped into the book. They are lucid where the player is slipping, they carry the backstory, and they sometimes contradict the player's notes.
- **The guardrail.** Recipes, costs, stability figures and every number the player makes a decision on are **always accurate**. Only prose and marginalia are unreliable.
- Locked entries give a diegetic hint about how to unlock them. The book never mentions content the player can't yet find.

## How the Voices sound

- **Source.** The project owner's own recorded voice, processed into a choir with layering, pitch shifting and reverb (sox). The licence is clean.
- **Fallback.** A local TTS model, but only if that model's output licence permits shipping.
- **Language ramp.** At low warp the Voices are whispered English fragments, almost intelligible. They slide into the Void-tongue as warp rises, matching the notebook's decline.
- **Delivery.** Audio plus short on-screen text, as a subtitle and never as chat messages.

## Void-tongue phonology

The language should sound like breath through glass. Hard stops are what the living use, so it has none.

- **Consonants:** s, sh, h, l, r, m, n, v, th (as in *thin*), w, y. Never use p, b, t, d, k, g, or ch, j.
- **Vowels:** a, e, i, o, u. A doubled vowel (`aa`, `ii`, …) means a long vowel, and long vowels are common.
- **Syllables:** (C)V(V)(C). Only s, l, r, n or h may close a syllable.
- **Stress** falls on the first long vowel; if a word has none, on the first syllable.
- **Romanisation** is lowercase in prose and capitalised only when used as a proper item name.

The lexicon of about 20 words and the list of Voice lines are not yet specified. They are written once the warp and eldritch specs know which names and lines they need.
