# Sillon

**An open-source Android music player for your own Plex Media Server.**

Sillon — French for the *groove* of a record — streams the music library of
your Plex server: sign in with your Plex account, pick your server, browse
artists and albums, and listen with the screen off.

> Early days: this is the first skeleton. See [Roadmap](#roadmap).

## What works today

- 🔑 **Plex sign-in** with a PIN — in the browser, or by typing the code at
  [plex.tv/link](https://plex.tv/link) from another device. No password ever
  goes through the app.
- 🖥️ **Server choice** among the servers you own or that are shared with you,
  with automatic connection selection: LAN first, then internet, then the Plex
  relay. If the saved address stops answering (you left home), Sillon finds a
  working one by itself.
- 🎤 **Browse** artists, albums and playlists, and **search** as you type, with
  server-resized cover art. Pick the music library when the server has several.
- 🎧 **Playback** with Media3/ExoPlayer: background playback, notification and
  lock-screen controls, Bluetooth/headset buttons, gapless album playback,
  shuffle and repeat.
- 📊 **Plays reported to Plex**: the track shows under *Now playing* on the
  server dashboard, lands in the history and counts as played — whether
  playback was started from the app, the notification or a headset.
- 🚗 **Android Auto**: browse artists, albums and playlists in the car, and
  play by voice ("play … on Sillon").
- 🌍 English and French, Material You.

## Roadmap

1. Offline downloads (WorkManager) and transcoding on mobile data.
2. ReplayGain loudness levelling, Plex mixes/radios (Plex Pass).
3. A Windows desktop app (Tauri), later.

## Tech stack

| Concern     | Choice |
|-------------|--------|
| Language    | Kotlin 2.0 |
| UI          | Jetpack Compose + Material 3 |
| Playback    | AndroidX **Media3** (ExoPlayer + MediaSession) |
| Plex API    | OkHttp + kotlinx.serialization |
| Settings    | DataStore (account token, chosen server — excluded from backups) |
| DI          | Hilt |
| Images      | Coil |

`minSdk 26` (Android 8.0) · `targetSdk 36` · single-activity, MVVM.
The build, CI and release setup come from
[Ondes](https://github.com/Wifsimster/ondes).

## Project layout

```
app/src/main/java/ovh/battistella/sillon/
├─ data/plex/        Plex wire models, HTTP client, connection selection
├─ data/session/     Account token + chosen server (DataStore)
├─ data/repository/  PlexRepository — what the UI asks for
├─ playback/         Media3 service and the controller bridge
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
- Tracks are played as the original files (direct play). Formats Android can't
  decode natively (ALAC on older devices, for example) aren't transcoded yet.
- Sillon is an independent client, not affiliated with Plex Inc.
