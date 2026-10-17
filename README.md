<p align="center">
  <img src="docs/logo.svg" width="112" alt="Sillon logo: a record seen from above" />
</p>

<h1 align="center">Sillon</h1>

<p align="center">
  <strong>Your own music, the way a music app should feel.</strong><br/>
  A free, open-source Android player for the music library on your Plex or Jellyfin server.
</p>

<p align="center">
  <a href="https://github.com/Wifsimster/sillon/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download-APK-E8A33D?style=for-the-badge&logo=android&logoColor=white" alt="Download the Sillon APK" /></a>
  <a href="https://github.com/Wifsimster/sillon/releases/latest"><img src="https://img.shields.io/github/v/release/Wifsimster/sillon?style=for-the-badge&label=version&color=1C1B1F" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-1C1B1F?style=for-the-badge" alt="Android 8.0 and later" />
</p>

<p align="center">
  <sub>WORKS WITH</sub><br/>
  <a href="https://www.plex.tv/"><img src="docs/brands/plex.svg" height="40" alt="Plex" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://jellyfin.org/"><img src="docs/brands/jellyfin.svg" height="40" alt="Jellyfin" /></a>
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
- 📻 **Radios.** Your server's library radios (Library, Deep Cuts, Time
  Travel…) and a radio for every artist, one tap away.
- ⭐ **Your taste, front and centre.** Your star ratings on every track,
  popularity bars to spot an album's hits, and **My 5 stars**: one tap shuffles
  every track you love.
- 🚗 **In the car.** Android Auto: browse your library on the dashboard, or say
  *"play … on Sillon"*.
- 💾 **Offline.** Download albums and playlists, Wi-Fi only by default, and
  listen with no connection at all.
- 🎚️ **Even volume.** Loudness levelling with the gains your server measured
  (ReplayGain), per track or per album, so a shuffle doesn't jump from a quiet
  70s record to a loud modern master.
- 📶 **Easy on your data plan.** Original quality on Wi-Fi, AAC at 320, 192 or
  128 kbps on mobile data, chosen separately and transcoded by your server.
- 🖥️ **Plex or Jellyfin.** Sign in to either; everything below works with both.
- 📊 **Your server stays in sync.** What you play shows under *Now playing* on
  your server, lands in the history and counts as played, like the official apps.
- 🔒 **Private.** No account but your server's, no analytics, no ads. Your
  server token never leaves the app.
- 🌍 **Open source**, in English and French, with Material You colours.

## Get it

👉 **[Download the APK](https://github.com/Wifsimster/sillon/releases/latest/download/app-release.apk)**
from the latest release. Open the link on your phone, allow *install from
unknown sources* when Android asks, and open the file. No store account and no
GitHub account needed.

Then:

1. **Pick Plex or Jellyfin.**
2. **Sign in.**
   - *Plex*: in the browser, or by typing the code shown at
     [plex.tv/link](https://plex.tv/link) from another device. Then pick your
     server; Sillon finds the best way to reach it (home network, internet,
     Plex relay).
   - *Jellyfin*: type your server's address, then your user name and password,
     or approve the **Quick Connect** code from another Jellyfin app.
3. **Play.**

Every release ships a `SHA256SUMS.txt` to check the download against what CI
built: `sha256sum -c SHA256SUMS.txt`. Want the tip of `main`? The
[*latest* pre-release](https://github.com/Wifsimster/sillon/releases/tag/latest)
is rebuilt on every push.

## Everything it does

| | |
|---|---|
| **Library** | Artists, albums, playlists; search as you type; choice of music library when the server has several |
| **Playback** | Background play, notification and lock-screen controls, Bluetooth and headset buttons, gapless albums, shuffle, repeat, loudness levelling (per track or album) |
| **Ratings** | Star ratings on tracks, popularity bars (Last.fm listeners via Plex), *My 5 stars* one-tap mix (*My favourites* on Jellyfin) |
| **Radios** | Plex library radios and artist radios (Jellyfin: library shuffle and Instant Mix), also in the car |
| **Car** | Android Auto browsing (Artists, Albums, Playlists, Radios, My 5 stars) and voice requests |
| **Offline** | Album and playlist downloads with covers, a Downloads tab that works with no network |
| **Data** | Streaming quality per network (Original / 320 / 192 / 128 kbps AAC), Wi-Fi-only downloads |
| **Plex** | PIN sign-in, owned and shared servers, automatic reconnection, play reports to the server |
| **Jellyfin** | Password or Quick Connect sign-in, HTTP or HTTPS servers, transcoding, play reports to the server |

## Roadmap

1. Sonically similar tracks and *Mixes for you* (Plex Pass sonic analysis).
2. A Windows desktop app.

Ideas and bug reports are welcome in the
[issues](https://github.com/Wifsimster/sillon/issues).

## Tech stack

| Concern     | Choice |
|-------------|--------|
| Language    | Kotlin 2.0 |
| UI          | Jetpack Compose + Material 3 |
| Playback    | AndroidX **Media3** (ExoPlayer, HLS, MediaLibraryService for Android Auto) |
| Server APIs | Plex and Jellyfin, over OkHttp + kotlinx.serialization |
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
├─ data/jellyfin/    Jellyfin client, mapped onto the same models
├─ data/session/     Account token + chosen server (DataStore)
├─ data/settings/    Streaming quality, download preferences (DataStore)
├─ data/local/       Downloaded tracks (Room)
├─ data/repository/  PlexRepository: what the UI asks for, from Plex or Jellyfin
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

- **Plex** is only reached over HTTPS. Plex issues `*.plex.direct`
  certificates for every server address, and the token must not travel in
  clear text. If your server has *Secure connections* set to *Disabled*, set
  it to *Preferred*.
- **Jellyfin** servers can be reached over HTTP, as they usually are on a home
  network (`http://192.168.1.10:8096`). Without a scheme, Sillon tries HTTPS
  first. Jellyfin has no star ratings and no popularity figures: *My 5 stars*
  becomes *My favourites*, and tracks show no popularity bars.
- On Wi-Fi with *Original* quality, tracks play as the original files. Formats
  Android can't decode natively (ALAC on older devices, for example) play once a
  transcoded quality is chosen.
- Loudness levelling only turns tracks down (the player can't amplify), as
  ReplayGain targets a quieter reference than most modern masters: with it on,
  turn the phone volume up a little. Plex needs *loudness analysis* enabled on
  the music library; Jellyfin computes the gains from 10.9 on.
- Sillon is an independent client, not affiliated with Plex Inc. or the
  Jellyfin project. Plex and Jellyfin are trademarks of their owners; the logos
  come from [simple-icons](https://simpleicons.org/) (CC0). Screenshots show
  album covers from the author's own library.
