# Specter (client) backlog

Owner-requested work, newest first. Client-side items only; Specter+ items (Auto RTP,
Player Bypass) live in the Specter-Plus repo's backlog.

## 2026-11-25 (from the owner)

### Donut Phone rendering bugs
- A weird **black triangle** shows in a corner when the Donut Phone is opened.
- **Black edges** peek out from the phone when any other menu is opened.
- Likely the phone's background/rounded-corner geometry or its render layer bleeding past
  its bounds (z-order / scissor clip). Fix both so nothing of the phone shows outside its frame.

### Atmosphere clouds look bad
- The clouds in the Atmosphere module look rough: **visible pixels**, generally weird-looking.
- Needs the cloud rendering/texture reworked so they read as smooth clouds, not a pixelated sheet.

### General performance (lower priority — do almost last)
- The mod overall is **heavy**; optimize it generally, not just the potato-mode path.
- Profile the hot paths (render/HUD/world FX) and trim per-frame cost across the board.

### Cross-ref: Auto RTP should keep running on these client screens
- Opening the **Donut Phone**, the **inventory**, or the **Specter settings/customize GUI**
  must NOT pause Auto RTP. The pause lives in Specter+ (`AutoRtp.java` `ourMenuOpen`); the
  client side only needs these screens recognized as "don't pause" screens. Tracked in the
  Specter-Plus backlog.
