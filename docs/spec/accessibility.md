# Localisation and accessibility

How the mod's text is authored, validated, translated and shown, and the accessibility baseline every screen, HUD and sound meets. This covers:
- the **lang pipeline**: the `vftv:lang/1` inputs under `content/lang`, the datagen merge and the one validator every owner's lang table feeds;
- **placeholders**, **protected text** (Void-tongue and the Latin aspect names), **numbers** and **plurals**;
- the **locales** v1 ships, the fallback, and the bar a later locale must clear;
- the **shared text-overflow rules** every screen and HUD cites, and the **status line**, the one destination for a player's world-use feedback;
- the **screen baseline** (keyboard, focus and narration), **HUD narration**, **subtitles** and **captions**;
- **photosensitivity and motion**, **colour and contrast**, and how long transient text stays;
- the shared **test fixture**, the wide-font pack, and what waits for a second locale.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the layers, the seams and datagen;
- the [quality gates](quality-gates.md) for the test layers and the definition of done;
- the [art style](art-style.md) for the palette and the asset pipeline;
- the [configuration spec](config.md#client-keys) for every client key;
- every system spec that shows text, draws a screen or a HUD, or plays a sound. **Each owner keeps its lang table, geometry, anchors, visibility, layer order and narration keys.** This spec owns the shared rules they conform to, and changes nothing a sibling settled except the amendments under "Contracts on sibling specs".

Resolved in [Localisation and accessibility spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/78).

**Source citations:** `MC:` is `minecraft-patched-26.2.0.88-sources.jar` and `NF:` the NeoForge 26.2.0.88 sources; package prefixes (`net/minecraft/`, `net/neoforged/neoforge/`) are omitted.

## The lang pipeline

### Files

**One envelope** for every authored lang input ([conventions, Lang](conventions.md#lang)):

```json
{"schema": "vftv:lang/1", "strings": {"<key>": "<text>"}}
```

- **Inputs** live at `content/lang/<code>/<file>.json`. The **file names stay each owner's**: research's `stage1.json` … `stage4.json` and `marginalia.json`, warp's `warp.json` and `voices.json`, foci's `foci.json` and `foci_void.json`, and so on. A non-English locale uses the same file names as `en_us`, so a key's file tells its owner.
- **`<code>`** is a language code from the client's supported language catalogue (`en_us`, `fr_fr`, `pt_br`, `zh_cn`, …), not a pattern: the validator checks it against the catalogue in the pinned client jar.
- **Code-defined keys** (the [configuration](config.md#lang-keys), [compatibility](compat.md#lang-keys), [research UI](research.md#book-ui-keys), [aspects](aspects.md#lang-keys) and [shared message](conventions.md#message-keys) keys, whose English is final in their specs) are written by datagen into `en_us` only. Their translations are authored in `content/lang/<code>/<owner>.json` like any other key, where `<owner>.json` is the **file the owner declares** for them: `config.json`, `compat.json`, `aspects.json`, `research_ui.json` and, for this spec's own keys and the shared message keys, `accessibility.json`.
- **Merge:** datagen writes `assets/vftv/lang/<code>.json` as the union of that code's `strings` objects (plus the code-defined keys for `en_us`), **sorted by key**, so the [datagen gate](quality-gates.md#static-gates) diffs stably.

### Validation

**One validator** (`core.text.model`, Minecraft-free), run by datagen over every `content/lang` input and the code-defined keys. Each owner's lang table is its data: the key or key pattern, the **Args** count and, where the owner says so, the file. The owners' own "Rejected" lists stay authoritative for their extra rules (research's patterns and pools, warp's segments, foci's Void-only file); this validator runs the common rules once.

| # | Rejected | Scope |
|---|---|---|
| 1 | an unknown `schema`, a missing or non-object `strings`, an unknown top-level field, a non-string value | every file |
| 2 | a **duplicate key inside one JSON object** (the reader rejects it; a plain map would keep the last) | every file |
| 3 | a duplicate key across files of one locale, or across a file and a code-defined key | every locale |
| 4 | a key no owner table declares or matches (an **unowned** key), or a key in a file other than its owner's | every locale |
| 5 | a required key missing from `en_us`, checked after every file is merged | `en_us` |
| 6 | an empty string | every locale |
| 7 | a malformed template ("Placeholders") or malformed book markup ([research, Prose and fact references](research.md#prose-and-fact-references)) | every locale |
| 8 | a digit or number word in authored prose ("Per-locale lints") | every locale |
| 9 | a `subtitles.vftv.*` value over **32 code points**; a `voice.*` line over **80 code points** | every locale |

**Rules that only another locale can exercise** (a key `en_us` doesn't have; an altered protected sequence, "Protected text") arrive with the **second-locale ticket** ("Adding a locale"). v1 ships `en_us` only, so they would have no input.

**Escapes are interpreted once.** Book prose (`research.*`, `marginalia.*` keys) is parsed with the book's markup (`\` escapes `*`, `{` and `\`), and its fact spans are not templates. Every other value is a translatable template, parsed by "Placeholders", where `\` is an ordinary character. No value is parsed both ways.

### Placeholders

**Vanilla's grammar** is `%(?:(\d+)\$)?([A-Za-z%]|$)` (`MC:network/chat/contents/TranslatableContents.java:53`). Vanilla throws inside `decompose` on an unsupported type or a stray `%`, catches it, and renders the raw template (`:107–177`), and the language loader rewrites some `%d` and `%f` forms to `%s` (`MC:locale/Language.java:98–114`). **Our accepted subset is stricter, as policy:**

- **Allowed:** `%s`, `%n$s` (1-based) and `%%`. Every other `%` sequence is rejected, including the `%d` and `%f` vanilla would rewrite.
- **No mixing:** a value uses either `%s` or `%n$s`, never both. The [compatibility spec's](compat.md#lang-keys) `%1$s` keys stay legal.
- **Arity, in `en_us`:** exactly *n* placeholders, where *n* is the owner's **Args**, covering exactly the indices 1…*n*. With `%s`, that means exactly *n* occurrences; with `%n$s`, every index 1…*n* **exactly once** and none above. So `%1$s %1$s` fails an Args 2 declaration, and so does `%1$s %2$s %2$s`.
- **Arity, in other locales:** every index 1…*n* used at least once, none above; a translation may reorder (`%2$s … %1$s`) and repeat.
- **Book prose:** each value's multiset of `{fact:…}` spans equals `en_us`'s; order is free.

**The grammar check** is these rules alone, a few lines over our own parser, with unit cases for each rejection. A parity corpus against vanilla's `TranslatableContents` waits for the second-locale ticket: our subset is strictly inside vanilla's, so `en_us` can't reach a template vanilla falls back on.

### Protected text

**The definition stands; the check is the second-locale ticket's** (there is no non-English value to check in v1). Some sequences are never translated. A non-English value must contain each of its `en_us` value's protected sequences **exactly**, character for character and case included, in the same order relative to one another; translated prose around them may move.

- **Void-tongue:** each maximal run of lexicon words in the `en_us` value ([warp, The lexicon](warp.md#the-lexicon)), with the spaces and punctuation between them. That covers the proper names (`item.vftv.hollow_bell` "Thahvis", the Choristers' names, the two Void foci in `foci_void.json`), every `void` segment of a Voice line ([warp, Language segments](warp.md#language-segments)), and any Void-tongue inside book prose. Void-tongue stays lowercase in prose and capitalised only as a proper name ([lore, Void-tongue phonology](lore.md#void-tongue-phonology)), so case is never changed.
- **Aspect names:** the 37 Latin names ([aspects, The aspects](aspects.md#the-aspects)) wherever they appear in a value, and every `aspect.vftv.<path>` value itself. Glosses (`aspect.vftv.<path>.gloss`) are translated.
- **A value made only of protected text** (an `en_us` value that is wholly Void-tongue or a Latin name) may be omitted from a locale; the English fallback is identical.
- **Never translated at all:** the Voice recordings (every locale hears the owner's English and Void-tongue clips; [`NOTICE.md`](../../NOTICE.md#voice-recordings) is untouched), the lexicon's glosses (review only), TOML identifiers and comments ([configuration, Validation](config.md#validation)), log text and the derivation export's `report.txt`.

### Per-locale lints

- **Digits:** authored book prose (`research.*` titles, hints, pages and margins; `marginalia.*`) contains no decimal digit (`\p{Nd}`, not only ASCII) outside fact spans, in every locale. This generalises the research spec's ASCII digit lint ([research, Prose and fact references](research.md#prose-and-fact-references)).
- **Number words:** each locale has one list, `content/research/number_words/<code>.json`, schema **`vftv:number_words/1`**:

  | Field | Required | Rules |
  |---|---|---|
  | `schema` | yes | `"vftv:number_words/1"` |
  | `words` | yes | 1–256 strings, each a lowercase word of letters only (`\p{L}+`), unique |
  | `allowed` | no, default `{}` | `{<lang key>: <one-line reason>}`; each key a book-prose key that exists, each reason 1–120 characters |

  Unknown fields are rejected. **Matching:** the value outside fact spans is lowercased with `Locale.ROOT`, split into maximal `\p{L}` runs, and a run equal to a listed word fails, unless the key is in `allowed`, which exempts that key from the list entirely. **v1 has only `en_us.json`**, the research spec's exhaustive English list with its reviewed exceptions. Folding with each locale's own `Locale` (Turkish `I`, `pt_br` → `pt-br`) arrives with the second-locale ticket, with that locale's list:

  ```json
  {"schema": "vftv:number_words/1",
   "words": ["zero", "one", "two", "…", "couple"],
   "allowed": {"research.vftv.choir.beneath_the_notes.page.0": "'one' as a pronoun, not a quantity"}}
  ```

  Rejected: `{"schema": "vftv:number_words/1", "words": ["One"]}` (uppercase) and `{"schema": "vftv:number_words/1", "words": ["two"], "allowed": {"research.vftv.nothing.page.0": "x"}}` (an unknown key).

  A locale with no list can't be shipped ("Adding a locale").
- **Completeness** is defined by keys, not files: a shipped locale has every `en_us` key except the protected-only values above.

### Numbers and plurals

- **Numbers are frozen** in every locale, as their owners already say: ASCII digits, `.` as the decimal point, `%` as the percentage suffix and no grouping ([arcane crafting, The screen](arcane.md#the-screen); the fact formats under [research, Prose and fact references](research.md#prose-and-fact-references)). Every format call and every case fold in our code takes `Locale.ROOT`, enforced by Error Prone's **`DefaultLocale`** and **`StringCaseLocaleUsage`** as errors ([quality gates, Static gates](quality-gates.md#static-gates)), which is why v1 needs no reruns under other JVM default locales. So the book's case-insensitive search ([research, Structure](research.md#structure)) folds with `Locale.ROOT` too.
- **No plural machinery.** Vanilla has none, and `.one`/`.other` pairs can't express Polish or Russian. **A value's grammar never depends on an argument's number:** use a label ("Level cost: %s"), a mass noun ("Spills %s flux.") or "of" ("Drawn %s of %s"). Each content ticket's review checks it, and any existing key that breaks it is amended.

### Text built on the server

A dedicated server holds `en_us` only, so the server never resolves player-facing text:
- every message, status, refusal, death message and command reply is a `Component.translatable` with arguments;
- **names are arguments as components** (an item's `getHoverName()`, an aspect's, an effect's, a block's), never resolved strings, so the client renders them in its own language;
- numbers are arguments pre-formatted under "Numbers and plurals";
- player names, ids and file paths are literal arguments, which a later pseudo-locale review exempts.

## Locales

### v1

**v1 ships `en_us` only** (owner-approved, #78). Other locales may come in later releases, each **complete and fluently reviewed** under "Adding a locale".

- **Fallback is vanilla's:** the client loads `en_us` first and the selected language over it (`MC:client/resources/language/LanguageManager.java:54–65`; `MC:client/resources/language/ClientLanguage.java:43–58`), so any language without our file, or a key a file lacks, shows our English and never a raw key.
- **Right-to-left:** no authored RTL locale ships in v1, because the book's own layout (strikes, blots and marginalia placement; [research, Prose that degrades with warp](research.md#prose-that-degrades-with-warp)) is left-to-right only. Selecting a vanilla RTL language still shows usable English fallback (a test). An RTL locale needs a spec change first.
- **Scripts without spaces** (zh, ja, th): the overlay's eligible word is a maximal letter run, so a strike can cover a whole clause. The words stay legible and `book.clear_prose` exists, so this is a known limitation; such a locale's band-3 screenshots are reviewed before it ships.

### Adding a locale

- **The second-locale ticket** is the first locale's, and comes before its content. It delivers what v1 leaves out because `en_us` can't exercise it: the validator rules for keys `en_us` lacks and for protected text ("Validation"); number-word folding per locale ("Per-locale lints"); the placeholder parity corpus ("Placeholders"); and the pseudo-locale and partial-locale packs with their scenarios ("Fixtures").
- **How:** a PR adding `content/lang/<code>/` and `content/research/number_words/<code>.json`, under `CC-BY-4.0` by [`NOTICE.md`](../../NOTICE.md) rule 5. The contributor licenses it as that file's Contributions section says; there is no CLA. No translation platform in v1.
- **Machine-assisted drafts** may ship only from a tool whose terms permit redistribution under CC-BY-4.0, and only after the same checks and review as any other translation. Provenance is recorded honestly ([ADR 0005](../adr/0005-licensing.md)): no copyright is claimed in purely machine-generated text, and the reviewer is recorded as a reviewer, not as a commissioned translator.
- **One provenance row per locale batch** in [`docs/assets-provenance.md`](../assets-provenance.md): the tool and exact model revision (if any), the tool's terms, the date, the human contribution, **the named fluent reviewer and the revision they reviewed**.
- **The bar,** all of it before the locale ships in a release:

  | # | Check | Where |
  |---|---|---|
  | 1 | complete ("Per-locale lints"); every validator rule passes | `check` |
  | 2 | its number-word list exists | `check` |
  | 3 | the pseudo-locale scenarios rerun **in that locale**, screenshots reviewed (band 3 included) | E2E |
  | 4 | the status line, subtitle and caption scenarios rerun in that locale | E2E |
  | 5 | a named fluent review of the revision shipped | provenance |

  A new locale is its own content ticket. Rows 1–4 also guard every later change to a shipped locale.

## Text overflow

Six shared algorithms. Every screen and HUD cites one. **Each owner keeps its dimensions, anchors, visibility, precedence and numeric-cell exceptions**; these rules say only how text that is too long behaves. They generalise rules siblings already settled (R1 the book's viewports, R2 the Worktable's status line, R3 the infusion HUD, R4 the cost cell); the status line and the tooltip-height rule are new.

All measurement uses the **active font** (`Font.width`, `Font.split`), with styles, so resource-pack fonts and Force Unicode Font are measured as drawn. Layouts are measured when an input changes and cached, **never per frame**; each owner's zero-allocation draw contract stands.

### R1: viewports

Multi-line text in a screen (prose, panels, cards, a confirm's body) is laid out at its column width and drawn in a **clipped, vertically scrolling viewport** with the research spec's scrollbar sprites ([research, GUI contract](research.md#gui-contract)). Nothing is cut and nothing pages automatically. Titles and buttons stay outside the viewport and visible. **Each viewport is a focus target:** Up and Down scroll it by one line, Home and End to its ends, and the wheel scrolls it when hovered. Each owner freezes its viewports' bounds.

### R2: one-line fields

A one-line field in a screen (a status, detail or feedback line, a title, **every button label**) is clipped at a character boundary so that the measured text plus "…" fits. **Its full text is its tooltip on hover and on keyboard focus, and narration always reads the full text.** A field that holds text and has no widget of its own (a status line, the book's feedback line) becomes an **inspection focus target** in its screen's focus order while it shows text. Inactive (disabled) widgets stay focusable for inspection and narrate why they are disabled; activating one does nothing.

### R3: HUD text

HUD text has no hover, so it **wraps and is never clipped**. Each line wraps to its owner's width with `Font.split`; no number or name is ever cut. Then the owner's frame, anchor, clamp and pose-scale fallback fit the block. **An owner's line count is an English design target, not a cap**, except in the caption band, whose blocks cap their visible lines and keep the whole text reachable (scaled to fit, or in the details view; "The caption band"). R3 shares the measuring and wrapping only: the [Hush reading](hush.md#reading) keeps its 200-px wrap and its place under the vis frame, the [Voice text](warp.md#voice-lines) its 240-px width and bottom anchor, the [vis HUD](wands.md#display) its 160-px inert-wand limit, and the [Baffle row](armor.md#the-hud) its fixed row. **R4 cells are excluded.**

### R4: numbers in fixed cells

The [shared cost-cell rule](arcane.md#the-screen), unchanged: the number scales down inside its field, the sprite never does, a cost above 9,999 draws the `beyond` glyph, and **the tooltip is always exact and unscaled.** Its limit is stated honestly: four digits in the wide-font fixture (36 px) draw at 20/36 ≈ 0.56 even inside a display drawn at scale 1, which is why the tooltip and the narration carry the exact value.

### R5: the status line

**The one destination for world-use feedback:** every status line, refusal and confirmation the server sends one player about a block, item or entity they used. It replaces the vanilla action bar for our messages, because the action bar is one unwrapped line (`MC:client/gui/Hud.java:364–392`) and our messages carry names, joined lists and up to five arguments. Which feedback goes here is [conventions, Feedback routing](conventions.md#feedback-routing).

- **Payload:** S2C **`StatusLine(text)`**, `text` a `Component` in vanilla's **bounded** `ComponentSerialization.STREAM_CODEC` (`MC:network/chat/ComponentSerialization.java:38`), never its trusted variant, to that player only. That codec's bound is the wire limit: a component too large to decode is rejected there. **No limit applies to the rendered text**, which a client's resource pack decides; long rendered text is handled by the visible-line cap and the details view below.
- **Code:** the server sends it through `StatusLines.send(ServerPlayer, Component)` in **`core.text`**, an ordinary feature that may use `api`, vanilla Minecraft and the JDK, but **no other `core` feature**; `core.text.model` is Minecraft-free, and client types live in `core.text.client`. The payload goes through the networking seam, and the layers and key mappings are registered by the `neoforge.text.client` listener ([architecture, Seams](architecture.md#seams)). Caption geometry from other features (the Voice text's lines) is passed in as plain numbers; `core.text` imports nothing from warp.
- **HUD layer `vftv:status`** (`RegisterGuiLayersEvent`), registered above the caption band's layer ("The caption band"). Text in `hush3` with a shadow, wrapped (R3) at `min(300, W − 12)`, centred, on a **black backing at alpha 0.8**, 2 px of padding. **Anchor:** its bottom edge is the caption band's bottom, so it never covers the vanilla action bar, the held-item name or the status rows.
- **Visible lines: at most 3.** Text that wraps to more shows its first 2 lines, the second clipped with "…", then `hud.vftv.status.more` naming the details key. The whole text is always in the details view and in the narration.
- **The details view:** the key mapping `key.vftv.status_details` (J by default, rebindable) opens **`StatusDetailsScreen`** on the **latest** status line, kept until a level change or disconnect, so it can be read after the line has faded. A 240 × 120 screen, centred: the title `screen.vftv.status_details` at (8, 6); the body, an R1 viewport at (8, 20), 220 × 72, with its scrollbar at (230, 20), holding the full text; **Close** (`screen.vftv.close`), 54 × 18, at (93, 96). Focus starts on the body, then Close; Esc closes. It narrates the title and the full text.
- **Lifetime:** `ceil(60 × max(1, notificationDisplayTime))` ticks, the last 20 fading; vanilla's own action bar starts at 60 ticks (`MC:client/gui/Hud.java:1347–1350`). **A newer line replaces the current one at once.** Cleared at a level change and at disconnect. Hidden with F1; drawn beneath an open screen, as vanilla's action bar is.
- **Narration:** on arrival, the full text is passed once to the narration helper ("HUD narration").
- **Fit:** the longest shipped messages with their longest arguments (the crucible's `SHORT` list of at most `MAX_COST_ENTRIES` (8) aspects, essentia's two block names, the node tap's five arguments) show within the 3 visible lines and are complete in the details view, under the wide-font fixture (an E2E assertion).
- **Not on the status line:** chat (never used), command replies (vanilla's command feedback), and HUD readouts, which keep their own layers.

### The caption band

The status line and the Voice text share one **caption band**, bounded and always on screen, which the crosshair cards keep clear of ([crucible, The HUD](crucible.md#the-hud)).

- **Its blocks:** the status line, 4 + 9 × (1–3) px tall, at the bottom; the Voice text ([warp, Voice lines](warp.md#voice-lines)), 4 + 9 × (1–4) px tall, its bottom 8 px above the status line's top while one shows, else at the band's bottom. **A Voice line shows at most 4 lines:** authored Voice text is at most 80 code points, which fits 4 lines at 240 px even in the wide-font fixture; text that would need more (an unvalidated resource pack) is drawn at the pose scale that fits it into 4 lines' height, never cut.
- **Its bottom** is `base − 8`, where `base = guiHeight − max(max(leftHeight, rightHeight) + 9, 68)` is the vanilla action-bar baseline (`MC:client/gui/Hud.java:379–381`). **Its top**, `bandTop`, is the top of the highest block showing, but never above ⌊`guiHeight` / 3⌋: if the blocks would reach higher (only with very tall status rows), the Voice block's pose scale shrinks it to fit. So the band's height is at most 3 + 4 lines of text plus padding and the 8-px gap (79 px), and at 320 × 240 with standard rows `bandTop` ≥ 85.
- **Publication:** a zero-draw layer **`vftv:caption_band`** is registered directly above vanilla's last status-row layer (after armour, food, air and the Baffle row), so `leftHeight` and `rightHeight` are final. Each frame it computes the band from the status line's and the Voice text's state and those heights, and **publishes it before any card layout or draw**: `bandTop`, `showing` and a **revision** that rises whenever either changes, on a line's arrival, expiry, replacement or hiding (F1, `voices.text`), or on a change in the status-row heights. The status line, Voice text and every crosshair card layer are registered above it, and every card rebuilds its layout when the revision changes.
- **No caption showing:** the band is empty and every card uses the whole screen, so the cards' existing vectors stand.

### R6: tooltips

- **Width:** mod-owned tooltips wrap at **170 px**, the width vanilla widgets use (`MC:client/gui/components/Tooltip.java:20,72–73`). Item tooltip lines keep their components and styles; a line of ours longer than 170 px is split the same way.
- **Height:** vanilla flips and repositions a tooltip, but never scrolls it, and can place it above the screen when it is taller (`MC:client/gui/screens/inventory/tooltip/DefaultTooltipPositioner.java:22–30`; `MC:client/gui/GuiGraphicsExtractor.java:1151–1184`). So every mod-owned tooltip fits **240 − 8 px** tall at 320 × 240 under the wide-font fixture (an E2E assertion). A tooltip that can't fit keeps a short summary and moves its detail into a focusable R1 viewport in its screen, opened by mouse and keyboard. No shipped tooltip needs one: the Lathe's rank quote is at most three aspects and fits as the arcane cost tooltip ([foci, The screen](foci.md#the-screen)).

## The minimum view

- **320 × 240 scaled pixels is the floor:** the smallest scaled screen vanilla's GUI scale allows for a window at least that large ([crucible, The HUD](crucible.md#the-hud)). Every custom screen fits it whole: the book through its own integer scale ([research, GUI contract](research.md#gui-contract)), where `guiScale` means the effective `Window.getGuiScale()`, not the option value (0 is "auto"); the Lathe at 288 × 234; the Worktable at 176 × 220; the Heart at 248 × 166. Every HUD fits through its owner's clamp and fallback.
- **The pose-scale fixture gate:** in the E2E scenarios at 320 × 240 under the wide-font fixture, every HUD's **outer** pose scale is **≥ 0.75**. It is a gate on the fixtures, not a runtime floor. Each fixture freezes its worst case: the maximum entry counts its owner declares (64 crucible entries, 8 infusion cells, 6 node aspects, 16 golem marks), the longest shipped names, four-digit amounts, and the overlays that can show together (the vis HUD with the Hush reading; a crosshair card with the caption band at its maximum). **A crosshair card that would fall below 0.75 pages into sheets** ([crucible, The HUD](crucible.md#the-hud)), with every entry reachable by Page Up and Page Down. The crucible card is capped at 12 cells, and its capped-card vectors, frozen there, never page (the lowest scale is 79/93 ≈ 0.85). Any other HUD that fails gets **shorter authored labels**, never a lost live value or name. R4's inner number scale is separate.

## Screens

### The baseline

The custom screens are the Commonplace Book (with the Ledger and its confirm), the Worktable, the Lathe, the Heart (with its confirm) and this spec's `StatusDetailsScreen`. NeoForge's `ConfigurationScreen` and JEI's UI are theirs. Each custom screen meets all of these:

1. **Keyboard:** Tab and Shift+Tab cycle the focus targets in the owner's frozen order; arrows move within a region; Enter or Space activates; Esc closes the innermost layer. **Every mouse action has a key.** **Vanilla container slots are exempt**: moving items stays vanilla's mouse-and-hotkey interaction, and vanilla's item tooltips on slots are not keyboard-reachable. That is an inherited limitation, recorded, not a promise.
2. **The focused element is always visible:** a scroll or pan keeps it in view, and it draws its focus state, which differs from idle by shape or luminance ("Colour and contrast").
3. **Information shown in a hover tooltip is reachable by keyboard:** its element is focusable and shows the same tooltip on focus.
4. **Narration:** every focus target narrates its owner's `narration.vftv.*` key or its label, then its exact values, then its state; clipped text reads in full (R2); the book's warp overlay is never read ([research, GUI contract](research.md#gui-contract)).
5. **Irreversible or costly confirms open with focus on Cancel**, and Esc cancels, as the [Heart's](eldritch.md#the-block-and-the-screen) does. The confirm's body is an R1 viewport and the first focus target after Cancel's initial focus.
6. **No timed input loses anything:** a timeout only returns to the state before it. The bargain offer's expiry, the infusion abandon window and the book's 40-tick pending re-enable already do; server deadlines are never extended for reading.

### Focus orders

Each owner freezes its order (amended under "Contracts on sibling specs"). Summary:

| Screen | Order |
|---|---|
| Commonplace Book | tabs → search → bookmarks → main region (canvas entries; or page widgets → palette → grid → buttons → feedback line) ([research, GUI contract](research.md#gui-contract)) |
| Ledger warp spread | left viewport → right viewport → Accept → Refuse → feedback line |
| Book confirm | (initial: Cancel) → body → Confirm → Cancel |
| Worktable | preview → cost cells → status line ([arcane, The screen](arcane.md#the-screen)) |
| Lathe | effect strip → medium strip → sockets → cost cells → Turn → Raise rank → detail line → status line ([foci, The screen](foci.md#the-screen)) |
| Heart | Joining viewport → Joining → Silencing viewport → Silencing → Close ([Eldritch tier, The block and the screen](eldritch.md#the-block-and-the-screen)) |
| Heart confirm | (initial: Cancel) → body → Confirm → Cancel |

### Inherited surfaces

- **Boss bars** are not narrated: vanilla's `BossHealthOverlay` draws them with no narration (`MC:client/gui/components/BossHealthOverlay.java:60–89`). The Choristers' fights stay readable without it: every telegraph has a pose, a mark, a sound and a subtitle ([Choristers, Marks](choristers.md#marks)).
- **The subtitle overlay** is vanilla's, one unwrapped line per cue (`MC:client/gui/components/SubtitleOverlay.java:65–110`); our captions keep to the 32-code-point bound ("Validation").
- **NeoForge's config screen** shows each key's translated tooltip and translated enum values ([configuration, The config screen](config.md#the-config-screen)).

## HUD narration

**Only readouts a deliberate act creates are narrated,** once. Each owner calls the **narration helper**, `Narration.say(Component)` in `core.text.client`, **whatever the narrator mode**; the helper calls `GameNarrator.saySystemQueued` (`MC:client/GameNarrator.java:40–44`), which alone decides from the player's mode (Off, Chat, System, All) whether anything is spoken. So an attempt never depends on the mode, and emission always follows it. Passive HUDs, which change as the player looks around, are not narrated, like vanilla's hearts.

| Narrated once, when the act's result arrives | Owner's text | Not narrated (passive) |
|---|---|---|
| a strike's card, with exact amounts and unheard masking | [aspects, What a strike reveals](aspects.md#what-a-strike-reveals) | the vis HUD, the Baffle row, the Spectacles card |
| a sounding's answer: direction, up or down, and the band | [aspects, Sounding for nodes](aspects.md#sounding-for-nodes) | the crucible HUD, the Hush reading |
| an infusion readout marked `solicited` (the answer to the player's strike or commit): its lines 1 and 2 | [infusion, The HUD](infusion.md#the-hud) | infusion refreshes, revision bumps and run updates; a golem card's passive status updates |
| a golem card page turn: the new page | [golems, The golem card](golems.md#the-golem-card) | the Voice text (the clip is the audio) |
| a card's sheet turn: the new sheet's body | [crucible, The HUD](crucible.md#the-hud) | |
| every status line, its full text | "R5" | |

Nothing is narrated per frame or per passive sync, and nothing is narrated twice: our messages no longer use the vanilla overlay path, which would narrate them again (`MC:client/multiplayer/chat/ChatListener.java:241–245`).

## Sound and captions

### Subtitles

- **Every sound event we register** names a subtitle in `sounds.json`, `subtitles.vftv.<event path>`, and that key exists in `en_us` (a static check over the generated `sounds.json`). Variants of one event share its caption.
- **The one exception is the 46 recorded Voice line events** (`<ns>:voice.<path>` for each line in `content/warp/voice_lines.json`, [warp, Voice lines](warp.md#voice-lines)): their caption is the Voice text, on by default and independent of vanilla's subtitle option. The exception is that enumerated list, **not a prefix**: `vftv:voice.insight`, an effect sound, keeps `subtitles.vftv.voice.insight`.
- **Captions are short** (≤ 32 code points) and name the source and what it does ("Chorister gathers a knell"), vanilla's style.

### No information by sound alone

Every sound a decision rests on has a visible counterpart beyond its subtitle:

| Sound | Visible counterpart |
|---|---|
| a Chorister's windup | its pose and its floor mark ([Choristers, Marks](choristers.md#marks)) |
| the sounding answer | its particles and the bearing marker with the band's word ([aspects, Sounding for nodes](aspects.md#sounding-for-nodes)) |
| the infusion strike's pitch | the stability on the HUD ([infusion, The HUD](infusion.md#the-hud)) |
| a Voice line | the Voice text |
| the Hush's muffling | the hushed blocks, their motes and the Clearing Bell's reading; muffling never touches subtitles ([Hush, Muffling](hush.md#muffling)) |
| a refusal's sound (`crucible.refuse`, `step_bell.still`) | its status line |

### Voice text

The [warp spec](warp.md#voice-lines) owns the Voice text; it is amended here so that:
- it draws on a **black backing at alpha 0.8**, our own, not `Options.getBackgroundColor`, whose opacity the player can lower to zero (`MC:client/Options.java:308–314,573–581,1502–1507`). At 0.8 over white, `amethyst3` measures **6.48:1** and `hush3` **7.17:1**;
- it uses **`amethyst3`** (`#c4b0ec`) for a line with any `void` segment and **`hush3`** (`#c6c2c4`) otherwise;
- it sits in the **caption band** ("The caption band"): **its bottom edge 8 px above the status line's top** while a status line shows, else at the band's bottom; at most 4 lines, scaled to fit rather than cut;
- **crosshair cards keep clear of it**: while a Voice line or a status line shows, the crosshair cards' usable height ends above the band ([crucible, The HUD](crucible.md#the-hud)), so a caption never covers a card's numbers;
- it stays for `ceil((clip ticks + 20) × max(1, notificationDisplayTime))` ticks and holds at most 80 code points in every locale;
- **a new line replaces a caption still showing** once its own sound may start (the sound rule, one line at a time, is unchanged); and in the ending scene the next line waits for the later of its predecessor's sound stopping and its caption's hold ending ([Eldritch tier, The ending scene](eldritch.md#the-ending-scene)).

### How long transient text stays

**Our transient text multiplies its time by `max(1, notificationDisplayTime)`** (`MC:client/Options.java:410–416`), an intentional minimum of 1 where vanilla's subtitles and held-item name use the multiplier directly, down to 0.5 (`MC:client/gui/components/SubtitleOverlay.java:62–67`; `MC:client/gui/Hud.java:1323`). Exactly these surfaces:

| Surface | Base | Supersession |
|---|---|---|
| the status line | 60 ticks | a newer line replaces it |
| the Voice text | the clip + 20 ticks | "Voice text" |
| the strike card | `readout_hold_ticks` (60), on the client ([aspects, Tuning](aspects.md#tuning)) | the next strike's card replaces it |

**Unchanged:** server deadlines (the bargain's expiry, the infusion abandon window, the arm's lifetime), the golem card's 100-tick page cursor, the sounding marker (it lasts until the next strike).

## Photosensitivity and motion

- **The only screen-wide effect of ours is the colour drain**, with its existing rules: desaturation with luminance preserved, at most 1.3% change per tick, scaled by `screenEffectScale`, and its own key ([warp, Perception effects](warp.md#perception-effects)). The Joining scene reuses it. No built-in effect of ours applies Darkness (`pressing_dark` is Slowness only, [warp, Danger](warp.md#danger)); a pack's status event that does gets vanilla's `darknessEffectScale`.
- **No full-screen flash. No camera shake, roll, forced rotation or FOV change of ours.** A teleport is an instant move, as vanilla's portals are.
- **No reduce-motion key.** Nothing of ours moves the camera, and what moves in the world (orbiting motes, idle sways, animated textures) is the kind of motion vanilla offers no switch for. That confirms the configuration spec's "would govern nothing a spec defines" ([configuration, Client keys](config.md#client-keys)).

### The flash test

A **project limit, not a WCAG proof,** held by review plus one assertion:

- **The rule, enforced by the asset review:** no animation of ours alternates between frames whose **mean relative luminance** (Rec. 709 weights over linear sRGB) differs by **≥ 0.10** faster than **once per 7 ticks**, which keeps it under 3 flashes in any 20 ticks. It covers every animated texture we ship (each `.mcmeta` strip, GUI ones included) and **every code-driven schedule** its owner declares: the focus bolt's `(age / 2) % 4` ([foci, Rendering and sound](foci.md#rendering-and-sound)) and the node core's `frame_ticks[clarity]` from `assets/vftv/nodes/client.json` ([wands, Rendering and sound](wands.md#rendering-and-sound)). A code-driven animation its owner hasn't declared isn't allowed: GUI and HUD elements of ours are otherwise static. The asset ticket's contact sheet lists each strip's frame timings and mean luminances for the reviewer.
- **Red, a unit test:** no colour in the locked palette is a saturated red (R / (R + G + B) ≥ 0.8), so a red flash can't come from our art. Addon aspect colours tint the bolt and are the addon's.
- **No per-pixel analyser in v1.** The mod has a handful of strips, all authored under the art style's slow, low-contrast motion; a reviewer reading their timings catches what an analyser would.

## Colour and contrast

### State never rests on hue alone

A state shown by colour is also shown by shape, by luminance, by text or by another channel. Already met: aspect glyphs, distinct in greyscale ([aspects, Colours](aspects.md#colours)); jar and tube glyph labels ([essentia, Labels](essentia.md#labels)); the crucible's tint ([crucible, The liquid](crucible.md#the-liquid)); the Choristers' dashed and solid marks ([Choristers, Marks](choristers.md#marks)); the Baffle's pips; the book's parent lines (`ink2` against `brass2`, a large luminance gap); the Hush, which also carries motes, muffling and the bell's reading. **The colour drain never reaches the GUI:** it runs at `AfterLevel`, before the HUD and screens ([architecture, Rendering](architecture.md#rendering)).

**State-variant sets.** Every set below differs by an **explicit non-hue mark** named by its owner, and its asset ticket's contact sheet adds **greyscale, deuteranopia, protanopia and tritanopia** previews (Machado 2009 matrices, in `tools/art`'s preview tools) at the sprites' real sizes, reviewed by an agent:

| Set | Owner | Non-hue mark |
|---|---|---|
| entry frames LOCKED, OPEN, LEARNED (per kind and the opener) | research | LOCKED a broken outline; OPEN a solid outline; LEARNED a solid outline with a filled inner ring |
| tabs selected and unselected; search idle and focused; bookmark empty and set; arrows; buttons normal, hover (focus) and disabled | research | selected and focused states raised 2 px or outlined in `ink1`; disabled with a 1-px hatch |
| hex cells (empty, anchor ring, silent, hover/focus, unanswered); palette slot normal and selected | research | already shapes (ring, hatch), with focus as the focus ring |
| `cost_cell` and `cost_cell_short` | arcane | `cost_cell_short` carries a 3 × 3 `ink1` notch in its top-right corner |
| Worktable preview idle, disabled, hover, ready | arcane | already distinct frames |
| Lathe choices idle, hover, selected, disabled, and **focused while selected** | foci | selected a 1-px inset outline; focus the hover sprite plus a 1-px outer outline, so both show at once; disabled hatched |
| golem role sprites and status glyphs | golems | each role and status a distinct silhouette at 16 × 16 and 9 × 9 |

**A regression check** runs on the committed sprites: two variants of one set differ in Rec. 709 luma by ≥ 0.15 on at least 8 pixels. It catches a hue-only swap; it is not a proof of distinguishability, which stays the review's. **Self-test:** a hue-only pair must fail it.

### Text contrast

**Every text style we draw, in every enabled and focused state, meets 4.5:1** (WCAG AA) against **every background pixel that can lie beneath it**; inactive (disabled) labels are excepted. Each row's owner declares the **text region** (a rectangle in the sprite or the screen) where the style draws, and the test checks it one of two ways:
- **a uniform region:** the owner declares the region one palette colour, and the test checks that every pixel of the committed sprite in the region is that colour, then computes the ratio from the hexes; or
- **a textured region:** the test computes the ratio against **every pixel** of the region and takes the minimum.

A translucent background (a backing, a partly transparent sprite pixel) is composited over **black and over white**, and the minimum of the two is taken. **No high-contrast variants in v1:** vanilla's `high_contrast` pack reaches only vanilla's sprites (`MC:client/Options.java:325`), so ours meet contrast on their own.

| Text style | Colour | Background (region) | Ratio |
|---|---|---|---|
| Voice text (`void`) | `amethyst3` `#c4b0ec` | black at alpha 0.8, composited (worst: over white) | 6.48:1 |
| Voice text, status line, Baffle value | `hush3` `#c6c2c4` | black at alpha 0.8, composited (worst: over white) | 7.17:1 |
| HUD cards (vis, crucible, infusion, golem, Hush, Spectacles, strike) and their sheet indicator | `hush3` | the frame's interior, uniform `ink1` `#1a1a2a` | 9.73:1 |
| book prose, titles, hints, Ledger rows, feedback line, and confirm bodies | `ink1` | the page paper's text column, or the panel's body, uniform `brass5` `#e8d8b4` | 12.17:1 |
| the foreign hand ([research, Prose that degrades with warp](research.md#prose-that-degrades-with-warp)) | **`amethyst1`** `#634a92` | the page paper's text column | 5.10:1 (`amethyst2` would give 2.73:1) |
| Choir page prose | `ink1` | the Choir paper's text column, uniform `glass1` `#d5e6e8` | 13.31:1 |
| Worktable and Lathe titles and inventory labels | `ink1` | the label strips, uniform `brass4` `#d8c399` | 9.95:1 |
| Worktable status line; Lathe detail and status lines | `ink1` | the line's rectangle, uniform `brass5` | 12.17:1 |
| cost numbers in `cost_cell` and `cost_cell_short` (the Worktable, the Lathe, JEI) | `ink1` | the number field x = 18…38, uniform `brass5` in both sprites | 12.17:1 |
| Worktable preview count | white, with vanilla's shadow | the preview frame's count corner, uniform `ink1` | 17.14:1 |
| 54×18 button labels (book, Lathe, Heart), enabled | `ink1` | the face, uniform `brass4` | 9.95:1 |
| the same, hovered or keyboard-focused | `ink1` | the face, uniform `brass5` | 12.17:1 |
| Heart panel text | `ink1` | the panel's body, uniform `brass5` | 12.17:1 |

**Self-tests** that must fail: low-opacity text (the Voice colours over black at alpha 0.5, over white); a sprite whose declared sample pixel passes but another pixel inside its text region fails; a region declared uniform that holds a second colour.

## Inventory

Every surface that shows text, with the rules it follows. Owners hold the details.

| Surface | Owner | Overflow | Keyboard | Narration | Colour |
|---|---|---|---|---|---|
| Commonplace Book, Ledger, confirm | [research](research.md#gui-contract) | R1 prose, panels and confirm body; R2 labels, buttons and feedback line | full, with inspection targets | entries, cells, palette, buttons, Ledger rows; clean prose only | frame and widget sets; `amethyst1` hand |
| Worktable | [arcane](arcane.md#the-screen) | R2 status line; R4 cells; R6 cost tooltip | preview, cells, status line | preview, each cell's full breakdown | `cost_cell_short` notch |
| Lathe | [foci](foci.md#the-screen) | R2 detail and status lines, buttons; R4; R6, Raise rank's tooltip as the rank quote | full, backward cycle | choices, sockets, cells, buttons | choice set |
| Heart and confirm | [Eldritch tier](eldritch.md#the-block-and-the-screen) | R1 panels and body; R2 buttons | full, viewports included | choice and panels | as owned |
| NeoForge config screen | [configuration](config.md#the-config-screen) | vanilla | vanilla | vanilla, translated tooltips and enums | — |
| JEI categories | [compatibility](compat.md#lang-keys) and owners | R4 | JEI's | JEI's | — |
| strike card, sounding marker | [aspects](aspects.md#what-a-strike-reveals) | R3; the crucible geometry's sheets | sheet keys | on arrival; sheet turns | glyphs; band as a word |
| Spectacles card | [armor](armor.md#drawing) | R3; sheets | sheet keys | passive; sheet turns | glyphs |
| vis HUD | [wands](wands.md#display) | R3, inert line wraps at 160 | — | passive | — |
| Baffle row | [armor](armor.md#the-hud) | fixed | — | passive | pips; backed text |
| crucible, infusion, golem HUDs | their owners | R3; sheets above the caption band | sheet keys | solicited infusion readouts, golem page turns, sheet turns | glyphs |
| Hush reading | [Hush](hush.md#reading) | R3 | — | passive | — |
| status line, caption band | this spec | R5, at most 3 visible lines | — | on arrival | backed `hush3` |
| `StatusDetailsScreen` | this spec | R1 body | body → Close | title and full text | `ink1` on `brass5` |
| Voice text | [warp](warp.md#voice-lines) | R3 | — | none (the caption) | backed |
| colour drain, Chorister marks, boss bar | warp, Choristers, Eldritch tier | — | — | boss bar not narrated (inherited) | shape |
| subtitles, item tooltips | vanilla, with our keys | ≤ 32 code points; R6 | vanilla | vanilla | — |

## Lang keys

This spec's own keys are **code-defined UI keys**: their English below is final, and datagen writes them into `en_us`; translations go in `content/lang/<lang>/accessibility.json`.

| Key | Args | Text |
|---|---|---|
| `key.categories.vftv` | 0 | "Voices From The Void" |
| `key.vftv.status_details` | 0 | "Read the last message" |
| `key.vftv.card.previous_sheet`, `key.vftv.card.next_sheet` | 0 | "Previous card sheet", "Next card sheet" |
| `hud.vftv.status.more` | 1 | "More: press %s" (the details key's name) |
| `hud.vftv.card.sheet` | 4 | "Sheet %s of %s (%s, %s)" (sheet, sheets, the previous and next keys' names) |
| `screen.vftv.status_details`, `screen.vftv.close` | 0 | "The last message", "Close" |

**Key mappings,** in the `key.categories.vftv` category, registered by the `neoforge.text.client` listener and rebindable in vanilla's Controls screen: `key.vftv.status_details` (J), `key.vftv.card.previous_sheet` (Page Up) and `key.vftv.card.next_sheet` (Page Down). Each acts only in the world (no screen open); the sheet keys are consumed only while a card with more than one sheet shows.

## Fixtures

One test resource pack, generated by the build, never shipped, loaded by the harness ([quality gates, Harness work the build phase needs first](quality-gates.md#harness-work-the-build-phase-needs-first)):

- **The wide-font pack** replaces `minecraft:default` with a bitmap provider whose glyphs for U+0020–U+007E and U+00A0–U+00FF all advance **9 px** (vanilla's digits advance 6): the "wide resource-pack font" the [arcane](arcane.md#tests) and [crucible](crucible.md#the-hud) vectors assume (four digits, 36 px).
- **Every existing "long test translation" scenario** ([research](research.md#tests), [arcane](arcane.md#tests), [crucible](crucible.md#tests), [infusion](infusion.md#tests), [golems](golems.md#tests), [Hush](hush.md#tests), [foci](foci.md#tests)) means the wide-font pack, with the longest shipped `en_us` text.
- **Narration** is tested without a harness op: a unit test swaps `Narration.say`'s sink for a recording `Consumer<Component>` and checks what each act **attempts**; one E2E checks that the platform narrator emits in System mode ("Tests").
- **The second-locale ticket** adds the pseudo-locale pack (a deterministic accented, padded transform of `en_us` that keeps placeholders, fact spans, markup, protected text and literal arguments) and a partial-locale pack ("Adding a locale").

## Performance

**Heavy-base workload:** one player receiving a status line every 10 ticks while a Voice line shows, the narrator in System mode, with a crosshair card up.

**Share:** server **0** of its own: building and encoding a `StatusLine` is charged to each caller's existing share, since it replaces a message the owner already sent. Client **≤ 0.01 ms p50** for the `vftv:status` and `vftv:caption_band` layers; the Voice text's layer stays warp's, and each card's relayout on a band change stays its owner's. With this share, the declared client shares total **0.95 ms p50**, leaving **0.05 ms** of the [client envelope](quality-gates.md#whole-mod-envelope) unallocated; every allocation target stands.

**Hot paths:**
- **Client, per frame:** the band is a few integer comparisons and allocates nothing; the status line's layout is built when it arrives, the language or resources change, or the scaled screen changes, and our draw code allocates **nothing** per frame (an allocation-budget test).
- **Client, per arrival:** one wrap and one narration call.
- **Build:** the validator, the palette assertion, the state-variant check and the contrast test run in `check`; none runs in play.

## Tests

**Unit:**
- **the validator:** every rule under "Validation" rejects its bad case, including a duplicate key inside one object, an unowned key, a key in the wrong owner file and a non-catalogue language code; the placeholder rules (`%1$s %1$s` and `%1$s %2$s %2$s` in English against Args 2, mixed forms, `%d`, `%f`, a stray `%`); fact-span multisets; book markup parsed once;
- **lints:** a full-width and an Arabic-Indic digit in prose fail; a listed number word fails unless its key is allowed; `vftv:number_words/1` rejects an uppercase word, an unknown key, an unknown field and a long reason;
- **assets:** the palette's no-saturated-red assertion; the state-variant regression check with its hue-only self-test; the contrast table with its self-tests (low opacity, a passing sample beside a failing pixel in the same text region, a "uniform" region holding a second colour); subtitle coverage over `sounds.json`, with the 46 Voice line events exempt and `voice.insight` covered;
- **narration attempts:** with a recording sink in place of the platform narrator, each readout under "HUD narration" is attempted exactly once on arrival and never on a passive refresh, and an identical act makes the same attempts whatever the narrator mode;
- **the status line:** the `StatusLine` codec round-trips through the bounded `ComponentSerialization.STREAM_CODEC`, and a serialized component beyond its bound is rejected at decode; the lifetime at `notificationDisplayTime` 0.5, 1 and 2; more than 3 wrapped lines show 2 and the `more` hint;
- **the caption band:** `bandTop` and the revision for every combination of status lines (0–3) and Voice lines (0–4), the status rows' heights, and the ⌊`H` / 3⌋ limit with very tall rows; the revision rising on arrival, expiry, replacement, F1 and `voices.text`;
- **sheets:** the [crucible's capped-card vectors](crucible.md#the-hud), replayed (no row pages; the lowest scale is 79/93 under the caption band's maximum with the wide font); on a synthetic card taller than any shipped one, every entry on exactly one sheet, the sheet reset on a new target and clamped on relayout.

**GameTest:**
- each owner's refusal and status paths send a `StatusLine` whose component is translatable with its owner's key and argument count (the owners' existing refusal tests gain the assertion);
- no server path sends `ClientboundSetActionBarTextPacket` or an overlay system message of ours.

**E2E** ([quality gates, E2E](quality-gates.md#e2e-mc-agent-harness)), under the wide-font pack unless noted:
- every custom screen and HUD at 320 × 240: screenshots reviewed (no number or name cut, nothing off screen); HUD outer pose scale ≥ 0.75 in the dump, with the caption band at its maximum over the largest cards, which scale as frozen; every mod-owned tooltip within 232 px; the status line's worst messages within 3 visible lines and whole in the details view;
- **keyboard only** through each custom screen in its frozen order, including the Lathe's backward cycle, the cost cells' full breakdown, Raise rank's tooltip (a three-aspect quote with flags and spill), the Heart's viewports, both confirms' initial focus on Cancel and `StatusDetailsScreen`; in the world, Page Up and Page Down through every sheet of a paged card, and J opening the last message after it has faded;
- **narration, one scenario:** with the narrator in **System** mode, a strike's card and a status line are emitted by the platform narrator. Focus-target narration (exact values, clipped fields read in full, the book's clean prose) is reviewed through each owner's narration keys, and attempts are the unit test's;
- **captions:** the Voice text at `notificationDisplayTime` 2.0 holds twice as long; on its backing over a white sky with the vanilla text-background option at 0; above a status line, which sits above the vanilla action bar while Baffle rows show; a caption **appearing and then expiring over an otherwise unchanged target** re-lays every crosshair card both times; a new line replacing a lingering caption; the ending scene waiting for each extended caption;
- **long rendered text:** a client resource pack translating a status message to 513 characters displays it (2 lines and the hint) and shows it whole in the details view;
- **subtitles:** the longest shipped captions, with several cues at once, at 320 × 240;
- **fallback:** `fr_fr` selected with no file of ours (English, no raw key); `ar_sa` selected (English fallback, usable); a resource reload while the book and a HUD are open rebuilds every font-dependent layout.

## Contracts on sibling specs

| Needs from | What | Met at |
|---|---|---|
| every system spec | world-use feedback on the status line; readouts on their own layers | [conventions, Feedback routing](conventions.md#feedback-routing) |
| research | number words in `content/research/number_words/<code>.json`; the frozen book UI keys; confirms open on Cancel; "a long test translation" is the wide-font pack | [research, Authored content](research.md#authored-content), [Book UI keys](research.md#book-ui-keys), [GUI contract](research.md#gui-contract), [Tests](research.md#tests) |
| aspects | the frozen lang keys; strike results narrated; the client hold scaled by `notificationDisplayTime`; the strike card under the caption band | [aspects, What a strike reveals](aspects.md#what-a-strike-reveals), [Lang keys](aspects.md#lang-keys) |
| warp | the Voice text's backing, position, hold and per-locale bound; line events exempt from subtitles; Void-tongue protected | [warp, Voice lines](warp.md#voice-lines) |
| crucible | wrapped header lines, `H′` above the caption band, sheets and the capped-card vectors | [crucible, The HUD](crucible.md#the-hud) |
| Hush | the reading keeps its wrap and place under the vis frame | [Hush, Reading](hush.md#reading) |
| wands | the inert-wand line wraps at its 160-px limit | [wands, Display](wands.md#display) |
| arcane | cost cells and the status line as focus targets; pinned text colours | [arcane, The screen](arcane.md#the-screen) |
| foci | the backward socket cycle | [foci, The screen](foci.md#the-screen) |
| foci | the complete rank quote in Raise rank's tooltip, fitting R6 | [foci, The screen](foci.md#the-screen) |
| golems | page turns narrated; the card's head and foot for sheets | [golems, The golem card](golems.md#the-golem-card) |
| Eldritch tier | the Heart's panels as focusable viewports; button labels per R2 | [eldritch, The block and the screen](eldritch.md#the-block-and-the-screen) |
| essentia | no HUD layer of its own | [essentia, Inspection and teaching](essentia.md#inspection-and-teaching) |
| infusion | `InfusionReadout.solicited`; only solicited lines narrated | [infusion, Arms and revisions](infusion.md#arms-and-revisions) |
| armor | the Baffle value on the black backing; the Spectacles card's head for sheets | [armor, The HUD](armor.md#the-hud) |
| art style | the flash rule, the palette's red assertion, colour-vision sheets and the contrast table | [art style, §8](art-style.md#8-how-a-build-agent-applies-this) |
| configuration | each key's comment is its translatable tooltip; enum values translated | [configuration, The config screen](config.md#the-config-screen) |
| quality gates | `DefaultLocale`, `StringCaseLocaleUsage` and this spec's tests in the static gates; the harness loads the wide-font pack | [quality gates, Static gates](quality-gates.md#static-gates), [E2E](quality-gates.md#e2e-mc-agent-harness) |

## Production tickets

**None.** v1 ships `en_us` only, the frozen research UI and aspects keys are code-defined with final English and written by datagen, and there are no assets beyond the existing asset tickets' amended briefs. The status details screen reuses the research spec's confirm-panel, scrollbar and button sprites, so it needs no asset. The build ticket for localisation and accessibility delivers the validator, `vftv:number_words/1` and its `en_us` file, the status line, the caption band, the status details screen and key mappings, the narration helper, the wide-font pack, the palette, state-variant and contrast tests, and every test above; the harness abilities it uses are the [Build foundations ticket's](quality-gates.md#build-order-foundations-first). The first later locale starts with the second-locale ticket, under "Adding a locale".
