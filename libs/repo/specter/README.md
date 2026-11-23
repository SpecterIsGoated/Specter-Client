# specter.stripped

Exact copies of three bundled libraries with only the files that malware scanners flag removed:

| artifact | from | removed | why it is safe |
|---|---|---|---|
| common-io 3.12.0 | com.twelvemonkeys.common:common-io:3.12.0 | `com/twelvemonkeys/io/FileSystem*`, `UnixFileSystem`, `Win32FileSystem` | Only the FileUtil free-disk-space helpers use them (they run df / dir); WebP decoding never calls those. |
| imageio-core 3.12.0 | com.twelvemonkeys.imageio:imageio-core:3.12.0 | `com/twelvemonkeys/imageio/color/icc_profiles_win.properties` | Windows system colour-profile paths for CMYK images; not used for pictures/wallpapers. |
| java-spotify-api 1.4.7 | com.github.labystudio:java-spotify-api:1.4.7 | `de/labystudio/spotifyapi/platform/linux/**`, `platform/osx/**` | Those start dbus-send / osascript. Specter reads Spotify on Windows only (SkyLyrics/SpotifyHud check the OS). |

Rebuilt 2026-11-23 by unzipping the original, deleting the files above, and re-jarring. Nothing else changed.
