# Session 35D — Scroll-aware animation smoothing

Punla **v2.9.3 / versionCode 27** refines the Session 35B performance behavior so animated atmospheric backgrounds no longer visibly stop and restart while content is scrolled.

## What changed

- Removed the hard background pause during active nested scrolling.
- Animated backgrounds now run on a virtual animation clock that remains continuous across scroll gestures.
- During active scrolling, the virtual clock eases toward ~32% playback speed instead of stopping.
- Background frame publication is capped to about 12 FPS while scrolling, preserving most of the frame budget for list/touch rendering.
- Entering and leaving scroll mode uses an exponential ~180 ms easing transition so motion ramps smoothly rather than snapping between states.
- Large frame-time gaps after app sleep/backgrounding are clamped so the atmosphere cannot leap forward on resume.
- Full-screen Campus Map still disables decorative background animation completely because MapLibre owns the interaction surface there.

## Why

Session 35B prioritized foreground scrolling by freezing the decorative background. That reduced jank, but the freeze itself could be noticeable and the resume transition felt abrupt. Session 35D keeps the performance benefit while maintaining continuous visual motion.

## Safety

- No Room/database migration.
- No backup format change.
- No new dependency.
- No changes to Study, attendance, notifications, or System Health behavior.
