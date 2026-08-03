# Punla Theme Collection Implementation

This build implements all 14 palettes from `PUNLA_THEME_COLLECTION.md` while retaining the original **Field Notebook** theme and the existing **Custom Color** generator.

## Included curated themes

- Aurora Borealis
- Sunset Sky
- Ocean Depths
- Forest Mist
- Lavender Night
- Golden Dawn
- Coffee Shop
- Lo-fi Night
- Paper & Ink
- Library Mode
- Cyber Neon
- Pastel Bloom
- Frost Glass
- Galaxy

## Behavior

- Each theme's documented mode uses the exact background, surface, primary, secondary, tertiary, and text colors from the collection.
- A readable companion palette is derived for the opposite mode so Punla's System/Light/Dark override remains usable.
- Accent labels choose the higher-contrast text role automatically. This preserves the pastel palettes while keeping buttons, chips, and icons readable.
- Widgets and animated/static background styles use the same resolved palette as the app.
- Existing saved values migrate automatically: `ocean`, `sunset`, `orchid`, and `slate` map to their closest collection replacements.

## Settings UI

Settings → Appearance now presents full preview cards containing:

- Theme name
- Three-color preview
- Designed mode
- Category
- Mood description
- Selected indicator

## Regression coverage

`ThemeCollectionTest.kt` verifies that the catalog contains all themes and that every documented color remains exact.
