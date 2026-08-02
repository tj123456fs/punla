# Punla Theme Collection — Implementation Progress

This build begins the theme-system update described in `PUNLA_THEME_COLLECTION.md`.

## Implemented in this build

- Added the six presets recommended for the initial release:
  - Aurora Borealis
  - Sunset Sky
  - Ocean Depths
  - Coffee Shop
  - Lo-fi Night
  - Paper & Ink
- Preserved **Punla Classic** and the existing **Custom** seed-color option.
- Added exact intended-mode background, surface, primary, secondary, tertiary, and text colors from the theme collection.
- Added explicit light/dark foreground roles so filled buttons and chips remain readable.
- Added success and warning roles to each palette alongside the existing error color.
- Rebuilt Settings → Themes as scrollable preview cards showing:
  - theme name
  - intended mode/category
  - miniature background, surface, and accent preview
  - mood description
  - selected state
  - Apply action
- Curated dark themes now switch Punla to dark mode when applied; Paper & Ink switches to light mode. The top-bar appearance control can still override the mode afterward.
- Existing saved Ocean, Sunset, Orchid, and Slate preferences migrate to the closest new theme.
- App widgets continue resolving their colors and backgrounds through the shared palette resolver.
- Backups now preserve the selected theme, custom seed color, background style, and font choice.

## Still planned from the collection

- Forest Mist
- Lavender Night
- Golden Dawn
- Library Mode
- Cyber Neon
- Pastel Bloom
- Frost Glass
- Galaxy
- Optional theme filters
- Automatic time-of-day switching
- Theme preview without immediately applying
- Reduced-motion and accessibility-contrast controls
