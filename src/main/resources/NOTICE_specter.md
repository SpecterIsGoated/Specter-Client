# Specter Client — license and third-party notices

Specter Client is free software, licensed under the **GNU General Public License, version 3 only**
(`GPL-3.0-only`); the full text is in `LICENSE`. Anyone who receives a Specter jar is entitled to its
source code under the same license.

## Built on, or containing parts of

| Work | License | How Specter uses it |
|---|---|---|
| [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) | GPL-3.0 | Specter is a modified fork of Meteor Client's framework (settings, events, rendering, config, HUD and command systems, under `com.jfant.specter.core`, Copyright (c) Meteor Development); Meteor's own modules, HUD elements and most commands were removed and its packages renamed. |
| [java-spotify-api](https://github.com/LabyStudio/java-spotify-api) by LabyStudio | GPL-3.0 | Bundled (jar-in-jar) to read what the Spotify desktop app is playing. |
| Anubis client by 4ldenz | GPL-3.0-only | Player Bypass, Media Base Finder (with the goliath table in `DonutGoliaths`) and parts of Prime Chunk Finder follow its techniques and code. |
| SoberClient by Violet | GPL-3.0-only | Tunnel Hole ESP follows its shaft finder; Base ESP's built-chunk count follows its Block Entity Debug; Anti Vanish's known-staff check and the staff names in `StaffTeams` follow its Anti Vanish probe and staff list; Anticheat Alerts reads the same feed format as its Anti Cheat module. |
| [Pixelify Sans](https://github.com/eifetx/Pixelify-Sans) | SIL Open Font License 1.1 | HUD card font; license in `assets/specter/font/pixelify-sans-ofl.txt`. |
| [Caveat](https://github.com/googlefonts/caveat) by Pablo Impallari | SIL Open Font License 1.1 | Sky Lyrics handwriting; license in `assets/specter/font/caveat-ofl.txt`. |
| [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans) by Tokotype | SIL Open Font License 1.1 | Clean theme font; license in `assets/specter/font/plus-jakarta-sans-ofl.txt`. |

## Online services (nothing of theirs is shipped)

Lyrics are fetched while the game runs, for the player's own screen, from [lrclib.net](https://lrclib.net) and,
when the `netease-word-timing` setting is on, NetEase Cloud Music (an unofficial endpoint). The lyrics themselves
belong to their writers and publishers; Specter only caches them locally.

Anticheat Alerts reads a public third-party feed (by default anticheat.imjules.be) with a plain request that carries
nothing about the player.

Minecraft is a trademark of Mojang. Specter is not affiliated with Mojang, Microsoft, Meteor Development, Spotify,
NetEase or DonutSMP.
