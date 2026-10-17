<p align="center">
  <img src="docs/logo.svg" width="112" alt="Sillon logo: a record seen from above" />
</p>

<h1 align="center">Sillon</h1>

<p align="center">
  <strong>Your own music, the way a music app should feel.</strong><br/>
  A free, open-source Android player for the music library on your Plex server.
</p>

<p align="center">
  <a href="https://github.com/Wifsimster/sillon/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download-APK-E8A33D?style=for-the-badge&logo=android&logoColor=white" alt="Download the Sillon APK" /></a>
  <a href="https://github.com/Wifsimster/sillon/releases/latest"><img src="https://img.shields.io/github/v/release/Wifsimster/sillon?style=for-the-badge&label=version&color=1C1B1F" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-1C1B1F?style=for-the-badge" alt="Android 8.0 and later" />
  <img src="https://img.shields.io/badge/Plex-compatible-E5A00D?style=for-the-badge&logo=plex&logoColor=white" alt="Works with Plex" />
</p>

<p align="center">
  <img src="docs/screenshots/01-albums.jpg" width="19%" alt="Albums: a grid of covers" />
  <img src="docs/screenshots/02-album.jpg" width="19%" alt="An album with popularity bars on each track" />
  <img src="docs/screenshots/03-player.jpg" width="19%" alt="Now playing, full-screen cover" />
  <img src="docs/screenshots/04-playlists.jpg" width="19%" alt="Playlists and the My 5 stars mix" />
  <img src="docs/screenshots/05-search.jpg" width="19%" alt="Search as you type: artists, albums, tracks" />
</p>

*Sillon* is French for the groove of a record. You keep your music on your own
server; Sillon makes it a pleasure to play on your phone, in the car and on the
go, with no ads, no tracking and no subscription.

## Why Sillon

- 🎧 **Made for music.** Albums, artists, playlists and search, gapless playback,
  big covers, a mini-player always within reach.
- ⭐ **Your taste, front and centre.** Your star ratings on every track,
  popularity bars to spot an album's hits, and **My 5 stars**: one tap shuffles
  every track you love.
- 🚗 **In the car.** Android Auto: browse your library on the dashboard, or say
  *"play … on Sillon"*.
- 💾 **Offline.** Download albums and playlists, Wi-Fi only by default, and
  listen with no connection at all.
- 📶 **Easy on your data plan.** Original quality on Wi-Fi, AAC at 320, 192 or
  128 kbps on mobile data, chosen separately and transcoded by your server.
- 📊 **Plex stays in sync.** What you play shows under *Now playing* on your
  server, lands in the history and counts as played, like the official apps.
- 🔒 **Private.** No account but your Plex one, no analytics, no ads. Your
  server token never leaves the app.
- 🌍 **Open source**, in English and French, with Material You colours.

## Get it

👉 **[Download the APK](https://github.com/Wifsimster/sillon/releases/latest/download/app-release.apk)**
from the latest release. Open the link on your phone, allow *install from
unknown sources* when Android asks, and open the file. No store account and no
GitHub account needed.

Then:

1. **Sign in with Plex**, in the browser or by typing the code shown at
   [plex.tv/link](https://plex.tv/link) from another device.
2. **Pick your server.** Sillon finds the best way to reach it: your home
   network first, then the internet, then the Plex relay.
3. **Play.**

Every release ships a `SHA256SUMS.txt` to check the download against what CI
built: `sha256sum -c SHA256SUMS.txt`. Want the tip of `main`? The
[*latest* pre-release](https://github.com/Wifsimster/sillon/releases/tag/latest)
is rebuilt on every push.

## Everything it does

| | |
|---|---|
| **Library** | Artists, albums, playlists; search as you type; choice of music library when the server has several |
| **Playback** | Background play, notification and lock-screen controls, Bluetooth and headset buttons, gapless albums, shuffle, repeat |
| **Ratings** | Star ratings on tracks, popularity bars (Last.fm listeners via Plex), *My 5 stars* one-tap mix |
| **Car** | Android Auto browsing (Artists, Albums, Playlists, My 5 stars) and voice requests |
| **Offline** | Album and playlist downloads with covers, a Downloads tab that works with no network |
| **Data** | Streaming quality per network (Original / 320 / 192 / 128 kbps AAC), Wi-Fi-only downloads |
| **Plex** | PIN sign-in, owned and shared servers, automatic reconnection, play reports to the server |

## Roadmap

1. **Jellyfin** servers, next to Plex.
2. ReplayGain loudness levelling, Plex mixes and radios (Plex Pass).
3. A Windows desktop app.

Ideas and bug reports are welcome in the
[issues](https://github.com/Wifsimster/sillon/issues).

## Tech stack

| Concern     | Choice |
|-------------|--------|
| Language    | Kotlin 2.0 |
| UI          | Jetpack Compose + Material 3 |
| Playback    | AndroidX **Media3** (ExoPlayer, HLS, MediaLibraryService for Android Auto) |
| Plex API    | OkHttp + kotlinx.serialization |
| Storage     | Room (downloads), DataStore (session and settings, excluded from backups) |
| Background  | WorkManager (downloads) |
| DI          | Hilt |
| Images      | Coil |

`minSdk 26` (Android 8.0) · `targetSdk 36` · single-activity, MVVM.
The build, CI and release setup come from
[Ondes](https://github.com/Wifsimster/ondes), the podcast player of the same author.

## Project layout

```
app/src/main/java/ovh/battistella/sillon/
├─ data/plex/        Plex wire models, HTTP client, connection selection
├─ data/session/     Account token + chosen server (DataStore)
├─ data/settings/    Streaming quality, download preferences (DataStore)
├─ data/local/       Downloaded tracks (Room)
├─ data/repository/  PlexRepository: what the UI asks for
├─ download/         Download queue and WorkManager worker
├─ playback/         Media3 service, Android Auto tree, play reports to Plex
├─ ui/               Compose screens, navigation, theme, components
└─ di/               Hilt module
```

## Build

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and the Android SDK. Every push builds debug and release APKs
in GitHub Actions; pushes to `main` are released with semantic-release
(Conventional Commits).

## Notes

- Only HTTPS connections are used. Plex issues `*.plex.direct` certificates for
  every server address, and the token must not travel in clear text. If your
  server has *Secure connections* set to *Disabled*, set it to *Preferred*.
- On Wi-Fi with *Original* quality, tracks play as the original files. Formats
  Android can't decode natively (ALAC on older devices, for example) play once a
  transcoded quality is chosen.
- Sillon is an independent client, not affiliated with Plex Inc. Screenshots
  show album covers from the author's own library.
