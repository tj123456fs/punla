# Punla Theme Collection

## Overview

Punla's theme system can go beyond basic light and dark modes by offering expressive visual styles inspired by nature, study spaces, and modern digital aesthetics.

Every theme must preserve Punla's core usability:

- Clear text and icons
- Strong contrast for deadlines, grades, budgets, and timers
- Consistent component behavior
- Readable cards and forms
- Comfortable long-term use during study sessions

Themes may change the app's color palette, background gradient, card tint, button colors, and subtle decorative effects — layout and navigation always stay consistent.

**Contents:** [Quick Reference](#quick-reference) · [Nature & Atmospheric](#nature-and-atmospheric-themes) · [Cozy Study](#cozy-study-themes) · [Playful & Modern](#playful-and-modern-themes) · [Accessibility Notes](#accessibility-notes) · [Recommended Initial Release](#recommended-initial-release) · [Theme Behavior Guidelines](#theme-behavior-guidelines) · [Settings Layout](#suggested-settings-layout) · [Future Improvements](#future-improvements)

---

## Quick Reference

| # | Theme | Mode | Category | Best for |
|---|-------|------|----------|----------|
| 1 | Aurora Borealis | Dark | Nature | Focus sessions, nighttime study |
| 2 | Sunset Sky | Dark | Nature | Creative planning, evening review |
| 3 | Ocean Depths | Dark | Nature | Long study sessions, cool/calm |
| 4 | Forest Mist | Dark | Nature | Low-distraction study, planning |
| 5 | Lavender Night | Dark | Nature | Night study, journaling |
| 6 | Golden Dawn | Light | Nature | Morning planning, class prep |
| 7 | Coffee Shop | Dark | Cozy | Reading, note-taking, budgeting |
| 8 | Lo-fi Night | Dark | Cozy | Pomodoro sessions, late-night review |
| 9 | Paper & Ink | Light | Cozy, Minimal | Long reading, schedules, deadlines |
| 10 | Library Mode | Dark | Cozy | Reading-heavy tasks, academic planning |
| 11 | Cyber Neon | Dark | Modern | Futuristic, high-contrast fans |
| 12 | Pastel Bloom | Light | Modern | Casual planning, personal organization |
| 13 | Frost Glass | Light | Modern, Minimal | Dashboards, schedule-heavy workflows |
| 14 | Galaxy | Dark | Modern | Nighttime use, Pomodoro sessions |

Palette tables below map each color to its **Material3 / `materialkolor` role** for direct use in Punla's Compose theming system: `background` / `onBackground`, `surface` / `onSurface`, `primary`, `secondary`, `tertiary` (accent).

---

# Nature and Atmospheric Themes

## 1. Aurora Borealis
*Dark · Nature*

A calm, premium theme inspired by northern lights moving across a dark night sky.

**Visual direction:** deep navy background, teal and emerald gradients, mint highlights, frosted dark cards, soft glow around selected controls.

| Role | Hex |
|---|---|
| Background | `#071A2B` |
| Surface | `#102A3A` |
| Primary | `#58E0C2` |
| Secondary | `#7CFF9B` |
| Tertiary / Accent | `#70A9FF` |
| On Background/Surface (text) | `#F2FFFC` |

**Best for:** Focus sessions, nighttime study, and users who prefer dark but colorful interfaces.

---

## 2. Sunset Sky
*Dark · Nature*

A warm and energetic theme inspired by late-afternoon skies.

**Visual direction:** orange-to-purple gradients, coral highlights, soft pink cards, warm cream text areas, gentle glow effects.

| Role | Hex |
|---|---|
| Background | `#2B1838` |
| Surface | `#4A274F` |
| Primary | `#FF8A5B` |
| Secondary | `#FF5FA2` |
| Tertiary / Accent | `#B68CFF` |
| On Background/Surface (text) | `#FFF6F1` |

**Best for:** Creative planning, evening review sessions, and a lively but cozy interface.

---

## 3. Ocean Depths
*Dark · Nature*

A cool and refreshing theme inspired by deep water and tropical seas.

**Visual direction:** deep blue background, cyan and aqua accents, layered blue gradients, glass-like cards, gentle wave-inspired transitions.

| Role | Hex |
|---|---|
| Background | `#041F33` |
| Surface | `#0A3A52` |
| Primary | `#36D6E7` |
| Secondary | `#4BA3FF` |
| Tertiary / Accent | `#7FFFD4` |
| On Background/Surface (text) | `#EAFBFF` |

**Best for:** Long study sessions and users who prefer cool, calming colors.

---

## 4. Forest Mist
*Dark · Nature*

A quiet, grounded theme inspired by foggy forests and moss-covered trails.

**Visual direction:** muted green background, gray-green cards, moss accents, natural beige text areas, very subtle texture.

| Role | Hex |
|---|---|
| Background | `#14231C` |
| Surface | `#243A2E` |
| Primary | `#8CCF9C` |
| Secondary | `#B7C98C` |
| Tertiary / Accent | `#D2B48C` |
| On Background/Surface (text) | `#F2F6EF` |

**Best for:** Low-distraction study, planning, and users who prefer earthy colors.

---

## 5. Lavender Night
*Dark · Nature*

A soft nighttime theme built around indigo and lavender tones.

**Visual direction:** dark indigo background, lavender highlights, violet cards, pale lilac text accents, soft ambient glow.

| Role | Hex |
|---|---|
| Background | `#17152C` |
| Surface | `#292449` |
| Primary | `#B59CFF` |
| Secondary | `#D6B4FF` |
| Tertiary / Accent | `#8FB3FF` |
| On Background/Surface (text) | `#F8F5FF` |

**Best for:** Night study, journaling, and a calm personal workspace.

---

## 6. Golden Dawn
*Light · Nature*

A bright and optimistic theme inspired by early morning light.

**Visual direction:** cream and peach background, golden accents, warm white cards, soft orange buttons, gentle sunlight gradient.

| Role | Hex |
|---|---|
| Background | `#FFF4DA` |
| Surface | `#FFF9EC` |
| Primary | `#E9A23B` |
| Secondary | `#F2C66D` |
| Tertiary / Accent | `#F28C5B` |
| On Background/Surface (text) | `#3B2C1E` |

**Best for:** Morning planning, class preparation, and users who prefer warm light themes.

⚠️ See [Accessibility Notes](#accessibility-notes) — primary/secondary/accent are low-contrast against this background for small text or icons.

---

# Cozy Study Themes

## 7. Coffee Shop
*Dark · Cozy*

A cozy theme inspired by wooden tables, warm lamps, and café study sessions.

**Visual direction:** warm beige background, mocha cards, caramel accents, cream text fields, soft shadows.

| Role | Hex |
|---|---|
| Background | `#2B211C` |
| Surface | `#49372E` |
| Primary | `#D6A36B` |
| Secondary | `#B97A56` |
| Tertiary / Accent | `#F0C987` |
| On Background/Surface (text) | `#FFF7ED` |

**Best for:** Reading, note-taking, budgeting, and relaxed study sessions.

---

## 8. Lo-fi Night
*Dark · Cozy*

A moody theme inspired by late-night study playlists and neon city windows.

**Visual direction:** dark navy and purple background, muted pink accents, indigo cards, gentle animated glow, soft neon highlights.

| Role | Hex |
|---|---|
| Background | `#15172B` |
| Surface | `#292A4A` |
| Primary | `#F08BC2` |
| Secondary | `#8A7CFF` |
| Tertiary / Accent | `#65C7F7` |
| On Background/Surface (text) | `#F7F3FF` |

**Best for:** Pomodoro sessions, late-night reviews, and focused study.

---

## 9. Paper & Ink
*Light · Cozy, Minimal*

A clean theme inspired by notebooks, journals, and printed study materials.

**Visual direction:** off-white paper background, dark ink text, muted blue or green accents, thin borders, minimal shadows.

| Role | Hex |
|---|---|
| Background | `#F4EFE6` |
| Surface | `#FFFCF7` |
| Primary | `#2F5D50` |
| Secondary | `#556B7A` |
| Tertiary / Accent | `#B07A45` |
| On Background/Surface (text) | `#23201D` |

**Best for:** Long reading sessions, schedules, deadlines, and users who prefer minimal design.

---

## 10. Library Mode
*Dark · Cozy*

A classic academic theme inspired by old libraries and wooden shelves.

**Visual direction:** dark brown or olive background, parchment cards, brass accents, deep green buttons, subtle book-like texture.

| Role | Hex |
|---|---|
| Background | `#211C17` |
| Surface | `#3B3026` |
| Primary | `#C9A96E` |
| Secondary | `#6E7A4E` |
| Tertiary / Accent | `#A45C40` |
| On Background/Surface (text) | `#F5EBD7` |

**Best for:** Reading-heavy tasks, academic planning, and a traditional study atmosphere.

---

# Playful and Modern Themes

## 11. Cyber Neon
*Dark · Modern*

A high-energy theme with bright digital accents on a dark background.

**Visual direction:** near-black background, cyan and magenta highlights, thin glowing borders, bright selected states, minimal gradients.

| Role | Hex |
|---|---|
| Background | `#090A12` |
| Surface | `#171A26` |
| Primary | `#00E5FF` |
| Secondary | `#FF4FD8` |
| Tertiary / Accent | `#9DFF00` |
| On Background/Surface (text) | `#F5F7FF` |

**Best for:** Users who enjoy futuristic interfaces and high-contrast visuals.

---

## 12. Pastel Bloom
*Light · Modern*

A cheerful theme using soft pastel colors and rounded visual elements.

**Visual direction:** pale pink, mint, and blue background tones, white cards, soft shadows, rounded controls, gentle floral accents.

| Role | Hex |
|---|---|
| Background | `#FFF1F6` |
| Surface | `#FFFFFF` |
| Primary | `#E78FB3` |
| Secondary | `#8FD8C7` |
| Tertiary / Accent | `#91B8F4` |
| On Background/Surface (text) | `#3B3540` |

**Best for:** Casual planning, personal organization, and users who prefer bright friendly themes.

⚠️ See [Accessibility Notes](#accessibility-notes) — primary/secondary/accent are low-contrast against this background for small text or icons.

---

## 13. Frost Glass
*Light · Modern, Minimal*

A cool glassmorphism-inspired theme with translucent surfaces.

**Visual direction:** pale blue background, semi-transparent cards, white highlights, frosted borders, very soft shadows.

| Role | Hex |
|---|---|
| Background | `#DCEEFF` |
| Surface | `#F7FBFF` |
| Primary | `#4C8FD8` |
| Secondary | `#79B7E8` |
| Tertiary / Accent | `#A7D8FF` |
| On Background/Surface (text) | `#1F3347` |

**Best for:** A clean modern look, dashboards, and schedule-heavy workflows.

⚠️ See [Accessibility Notes](#accessibility-notes) — accent is low-contrast against this background for small text or icons.

---

## 14. Galaxy
*Dark · Modern*

A dramatic theme inspired by stars, nebulae, and deep space.

**Visual direction:** dark navy and violet background, purple and blue gradients, star-like highlights, glowing cards, bright accent buttons.

| Role | Hex |
|---|---|
| Background | `#0B1026` |
| Surface | `#1B2250` |
| Primary | `#8E7CFF` |
| Secondary | `#5AC8FA` |
| Tertiary / Accent | `#FF7BCB` |
| On Background/Surface (text) | `#F6F5FF` |

**Best for:** Nighttime use, Pomodoro sessions, and users who enjoy bold visual themes.

---

# Accessibility Notes

All 14 themes pass **WCAG AA for body text** (`text` vs. `background` and `text` vs. `surface` both exceed 10:1 — well above the 4.5:1 minimum for normal text).

The check that matters more for Punla is **UI-element contrast** (WCAG's 3:1 minimum for icons, large text, and graphical objects), since `primary`/`secondary`/`accent` get used directly as icon tints, chip backgrounds, and button labels. Three light themes fall short here:

| Theme | primary vs. bg | secondary vs. bg | accent vs. bg |
|---|---|---|---|
| Golden Dawn | 1.98:1 | 1.47:1 | 2.21:1 |
| Pastel Bloom | 2.13:1 | 1.50:1 | 1.85:1 |
| Frost Glass | 2.84:1 | 1.82:1 | 1.27:1 |

**Why this happens:** these are pastel-on-pastel palettes — the accent colors are close in lightness to the background, which reads as soft and pleasant for large fills (cards, gradients) but disappears for small text, thin icon strokes, or unfilled outline buttons.

**Recommended fix, not a palette change:** keep these three palettes as-is for backgrounds, gradients, and filled surfaces, but never place `primary`/`secondary`/`accent` colors directly as text or icon color on top of `background`/`surface`. Instead:
- Use them as **fill colors** behind a dark `onBackground`/`onSurface` label (e.g., a filled chip or button with dark text on a pastel fill).
- For deadlines, grades, budgets, and timers specifically — the values flagged as "must stay readable" in the Theme Behavior Guidelines — always use `onBackground`/`onSurface`, never a raw accent color, regardless of theme.

No dark theme in this collection has a contrast issue.

---

# Recommended Initial Release

To keep development manageable, the first release can introduce six themes:

1. Aurora Borealis
2. Sunset Sky
3. Ocean Depths
4. Coffee Shop
5. Lo-fi Night
6. Paper & Ink

This set covers a balanced mix of dark, light, cozy, colorful, and minimal styles — and sidesteps the three flagged light-theme contrast issues, so no accessibility fix is required before launch.

---

# Theme Behavior Guidelines

Every theme must define:

- App background and surface/card colors
- Primary and secondary colors
- Text and icon colors (`onBackground` / `onSurface`)
- Error, warning, and success colors
- Navigation bar appearance
- Button and input-field styles
- Optional background gradient
- Optional glow, texture, or glass effect

Decorative effects should stay subtle. Important information must always stay readable using `onBackground`/`onSurface`, especially:

- Countdown timers
- Budget amounts
- Grades
- Deadlines
- Class schedules
- Notification settings
- Map labels

---

# Suggested Settings Layout

Punla can display themes in **Settings → Appearance → Themes**.

Each theme card may show:

- Theme name
- Small visual preview
- Light or dark label
- Short mood description
- Selected indicator
- Apply button

Optional filter categories: Nature, Cozy, Modern, Minimal, Dark, Light.

---

# Future Improvements

- Automatic theme switching by time of day
- Matching Android system theme
- Animated background gradients
- User-created custom palettes
- Seasonal themes
- Theme preview before applying
- Separate background and color-palette controls
- Accessibility contrast mode
- Reduced-motion option for animated themes

---

## Design Goal

Punla's themes should help users create a study environment that feels personal, calming, and motivating — without making the app harder to use.
