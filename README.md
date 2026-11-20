<p align="center">
  <img src="docs/brand/logo-animation.gif" width="640" alt="Sillon: a single groove spirals to an amber record label, then the name Sillon and the tagline appear" />
</p>

<p align="center">
  <strong>Your own music, the way a music app should feel.</strong><br/>
  A free, open-source player for the music library on your Plex or Jellyfin server, on Android and on Windows.
</p>

<p align="center">
  <a href="https://github.com/Wifsimster/sillon/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download-APK-E8A33D?style=for-the-badge&logo=android&logoColor=white" alt="Download the Sillon APK" /></a>
  <a href="https://github.com/Wifsimster/sillon/releases/latest"><img src="https://img.shields.io/github/v/release/Wifsimster/sillon?style=for-the-badge&label=version&color=15120F" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-15120F?style=for-the-badge" alt="Android 8.0 and later" />
</p>

<p align="center">
  <sub>WORKS WITH</sub><br/>
  <a href="https://www.plex.tv/"><img src="docs/brands/plex.svg" height="40" alt="Plex" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://jellyfin.org/"><img src="docs/brands/jellyfin.svg" height="40" alt="Jellyfin" /></a>
</p>

<p align="center">
  <img src="docs/screenshots/01-listen.jpg" width="16%" alt="Listen: shuffle, My 5 stars, never played and the mystery sleeve" />
  <img src="docs/screenshots/02-album.jpg" width="16%" alt="An album with popularity bars on each track" />
  <img src="docs/screenshots/03-player.jpg" width="16%" alt="Now playing, with stars to rate the track" />
  <img src="docs/screenshots/04-queue.jpg" width="16%" alt="The play queue, reorder by dragging" />
  <img src="docs/screenshots/05-playlists.jpg" width="16%" alt="Playlists: a grid of mosaic covers" />
  <img src="docs/screenshots/06-search.jpg" width="16%" alt="Search before typing: recently played, your classics and genres" />
</p>

*Sillon* is French for the groove of a record — hence the logo: one continuous
groove spiralling to the label (see [DESIGN.md](DESIGN.md)). You keep your music on your own
server; Sillon makes it a pleasure to play on your phone, in the car and on the
go, with no ads, no tracking and no subscription.

## Why Sillon

- 🎧 **Made for music.** Albums, artists, playlists and search, gapless playback,
  big covers, a mini-player always within reach.
- 📻 **Radios.** Your server's library radios (Library, Deep Cuts, Time
  Travel…), Plex's *Mixes for you*, a radio for every artist, and from the
  player, a radio of tracks that sound like the one playing.
- 🧭 **Never played.** A radio of the tracks you have never heard, or an album you
  have never played, drawn at random. A card shows how much is still waiting.
- 💿 **Sleeve wear.** Covers age as you play them, like a real record: yellowed
  edges, worn corners, a ring. One switch in Settings turns it off.
- 👥 **Listen together.** Several phones play the same track at the same moment,
  through your own server. Jellyfin only: Plex offers no way to do it.
- 🎁 **Mystery sleeve.** One tap plays a whole album you rarely listen to,
  hidden behind a kraft sleeve in the app, the notification and the car. It
  reveals itself after the third track, or when you ask.
- ⭐ **Your taste, front and centre.** Your star ratings on every track,
  popularity bars to spot an album's hits, and **My 5 stars**: one tap shuffles
  every track you love.
- 🚗 **In the car.** Android Auto: browse your library with its covers on the
  dashboard, pick up where you stopped from the resume card, or say
  *"play … on Sillon"*.
- 💾 **Offline.** Download albums and playlists, Wi-Fi only by default, and
  listen with no connection at all.
- 🎚️ **Even volume.** Loudness levelling with the gains your server measured
  (ReplayGain), per track or per album, so a shuffle doesn't jump from a quiet
  70s record to a loud modern master.
- 📶 **Easy on your data plan.** Original quality on Wi-Fi, AAC at 320, 192 or
  128 kbps on mobile data, chosen separately and transcoded by your server.
- 🖥️ **Plex or Jellyfin.** Sign in to either; everything works with both,
  except listening together.
- 📊 **Your server stays in sync.** What you play shows under *Now playing* on
  your server, lands in the history and counts as played, like the official apps.
- 🎮 **On your Discord profile.** On Windows and Linux, Sillon can show the
  track you're listening to on Discord, with the artist, a progress bar and,
  if you allow it, the album cover. It talks to the Discord app on your
  computer and sends nothing about your server. Off until you turn it on in
  *Settings → Discord*.
- 💌 **Music postcards.** From the player, an album or an artist, *Share a card*
  makes an image of the cover with the title, the artist and the year, square or
  story-sized, in three styles: Vinyl, Label and Polaroid. Add a few words, your
  rating, or two lines of the lyrics when your server has them. The card is drawn
  on your phone, carries no link and says nothing about your server, and it works
  offline with your downloads.
- 🔒 **Private.** No account but your server's, no analytics, no ads. Your
  server token never leaves the app.
- 🎨 **A look of its own.** Vinyl blacks, sleeve cream and label amber, titles in
  Fraunces, and a player tinted by the cover playing. Make it yours in
  Settings → Theme: light or dark, pure black, six accent colours or your
  wallpaper's (Material You). See [DESIGN.md](DESIGN.md).
- 🌍 **Open source**, in English and French.

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

### On Windows (preview)

👉 **[Download the installer](https://github.com/Wifsimster/sillon/releases/latest/download/Sillon-windows-x64.msi)**
(`Sillon-windows-x64.msi`, Windows 10 or 11, 64-bit) from the latest release
and open it. The installer isn't signed yet: Windows SmartScreen asks before
running it (*More info* → *Run anyway*). It installs for your user only, no
administrator rights needed, and comes with its own Java runtime.

The desktop app signs in the same way, and plays the same library: artists,
albums, playlists, search as you type, gapless playback, the queue (play next,
add, reorder, remove), radios, star ratings and *My 5 stars*, skipping 1-star
tracks, loudness levelling, streaming quality, lyrics, genres, sleeve wear,
downloads for offline listening, the server's activity (who is playing what),
play reports to your server, and what you play on your Discord profile. It resumes
the queue where you stopped, and speaks English and French.

It keeps itself up to date, without asking: now and then it asks GitHub
whether a new release is out, downloads the installer in the background and
checks it against the release's SHA-256. The update installs itself the next
time Sillon starts (a progress bar, then the app), and that first start shows
what is new. That request to GitHub is the
only one Sillon makes to anything but your server; *Settings → Updates* turns
it off. The one exception is *Album covers on Discord*, off by default: it looks
up the album (artist and album names only) on Apple's iTunes, to show a public
cover on Discord.

Shortcuts: `Space` play or pause, `Ctrl` + `→` / `←` next or previous track,
`Ctrl` + `F` search, `Esc` back.

Not on Windows yet: Play on Sonos, Listen together, the mystery sleeve, share
cards, listening by side and the listening cache.

## Everything it does

| | |
|---|---|
| **Library** | Artists, albums, playlists; search as you type; choice of music library when the server has several |
| **Playback** | Background play, notification and lock-screen controls, Bluetooth and headset buttons, gapless albums, shuffle, repeat, loudness levelling (per track or album) |
| **Sides** | *Listen by side*: an album played in order stops at the end of side A, like a record, until you flip it (in the app, the notification and Android Auto) |
| **Ratings** | Star ratings on tracks, popularity bars (Last.fm listeners via Plex), *My 5 stars* one-tap mix (*My favourites* on Jellyfin) |
| **Radios** | Plex library radios and artist radios (Jellyfin: library shuffle and Instant Mix), *Never played* (tracks or whole albums, Plex and Jellyfin), also in the car |
| **Surprise** | Mystery sleeve: a whole album drawn at random (rarely played first, or any album), hidden in the app, the notification, the lock screen and the car until the third track or *Reveal*; *Keep* adds it to a *Surprises* playlist; offline, drawn from your downloads |
| **Covers** | Sleeve wear: covers age with your plays (yellowed edges, worn corners, ring wear, a crease) on the album grids, the album page and the player; on by default, off in Settings → Appearance |
| **Together** | Listen together on Jellyfin (SyncPlay): shared play, pause, skip and seek; the notification, headset buttons and Android Auto control the group. Not available on Plex |
| **Discord** | On Windows and Linux: the track, artist, album and a progress bar on your Discord profile (Rich Presence), through the Discord app on the same computer; optional album covers looked up on iTunes (artist and album names only), never your server's |
| **Activity** | *Server activity*: who is playing what on your server right now (user, device, track, progress), updated as it happens; optionally a notification when someone starts listening. For the server's owner (Plex) or an administrator (Jellyfin); on Android and Windows (notifications on Android only) |
| **Sonos** | *Play on…* sends the queue to a Sonos speaker or group on your Wi-Fi, over the local network (no Sonos account, no cloud); the player, the notification, the lock screen and the volume keys drive it, and the music comes back to the phone where it stood. Plex and Jellyfin |
| **Car** | Android Auto browsing with covers (Artists, Albums, Playlists, Radios, My 5 stars), resume card, voice requests |
| **Resume** | The queue and position are remembered: *play* on a headset, Bluetooth or in the car resumes where the music stopped, even after the app was closed |
| **Offline** | Album and playlist downloads with covers, a Downloads tab that works with no network |
| **Data** | Streaming quality per network (Original / 320 / 192 / 128 kbps AAC), Wi-Fi-only downloads |
| **Plex** | PIN sign-in, owned and shared servers, automatic reconnection, play reports to the server |
| **Jellyfin** | Password or Quick Connect sign-in, HTTP or HTTPS servers, transcoding, play reports to the server, listen together (SyncPlay) |

## Identity

<p align="center">
  <img src="docs/brand/board.png" width="720" alt="Sillon brand board: logo, palette and typography" />
</p>

One continuous groove spiralling to an amber label; vinyl black `#15120F`,
groove `#2A241E`, sleeve cream `#F6EFE4`, label amber `#E8A33D` and rust
`#C2552B`; Fraunces for titles, DM Sans for text. Logo files, the animated logo
(GIF and MP4, made with [Remotion](https://www.remotion.dev/) in
[`brand/animation`](brand/animation)) and the rules are in [DESIGN.md](DESIGN.md).

## Roadmap

1. The Windows app out of preview: a signed installer, then what Android has
   and Windows doesn't yet (Play on Sonos, Listen together, the mystery sleeve,
   share cards).

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

The Windows app is Kotlin too: Compose Multiplatform for the UI,
[libmpv](https://mpv.io/) (LGPL build, loaded through JNA) for playback, and
the same Plex and Jellyfin code as Android, from the `shared` module.

## Project layout

```
shared/src/main/kotlin/ovh/battistella/sillon/     Kotlin/JVM, no Android: used by both apps
├─ data/plex/        Plex wire models, HTTP client, connection selection
├─ data/jellyfin/    Jellyfin client, mapped onto the same models
├─ data/session/     Account token + chosen server (DataStore)
├─ data/settings/    Settings, saved queue, never-played counts (DataStore)
├─ data/local/       Downloaded tracks: the table and its queries
├─ data/rating/      Star ratings: the book, the store, sending them
├─ data/wear/        Sleeve wear: play counts per album, levels, cache
├─ data/activity/    Server activity: who plays what, the live feed, who was already announced
├─ data/repository/  PlexRepository: what the UI asks for, from Plex or Jellyfin
└─ data/storage/, data/network/, playback/cache/   What each platform provides

app/src/main/java/ovh/battistella/sillon/          Android
├─ data/             Room database, storage and network for Android, genres, search page
├─ download/         Download queue and WorkManager worker
├─ playback/         Media3 service, Android Auto tree, radios, play reports
├─ together/         Listen together: clock sync, session, drift correction
├─ activity/         Server activity: the live watch, notifications, the periodic check
├─ sonos/            Play on Sonos: SSDP discovery, UPnP/SOAP control, the Sonos player
├─ ui/               Compose screens, navigation, theme, components
└─ di/               Hilt modules

desktop/src/main/kotlin/ovh/battistella/sillon/desktop/   Windows (Compose Multiplatform)
├─ player/           libmpv binding, the queue engine, play reports and resume
├─ platform/         Storage, network and the downloads file on a computer
├─ download/         Download queue
├─ update/           Release feed, changelog, download and install of updates
├─ i18n/             English and French
└─ ui/               Screens, the player bar, the queue, theme
```

## Build

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

The desktop app:

```bash
./gradlew :desktop:test       # desktop unit tests (the player tests need libmpv)
./gradlew :desktop:run        # run it (Linux or macOS: with libmpv installed)
bash scripts/fetch-libmpv.sh  # Windows: fetch libmpv-2.dll, checked against its SHA-256
./gradlew :desktop:packageReleaseMsi   # on Windows → desktop/build/compose/binaries/main-release/msi/
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
- Sonic features (*Mixes for you*, tracks that sound alike) need Plex Pass and
  *Sonic analysis* enabled on the music library; until the analysis has run,
  the track radio falls back to the artist's radio.
- *Never played* uses the play counts kept by your server (`viewCount` on Plex,
  `PlayCount` on Jellyfin). A track skipped within 30 seconds isn't offered
  again until the app restarts; nothing about skips is saved. Offline, the radio
  draws from your downloads, using the play counts saved the last time Sillon
  reached the server.
- *Listen together* uses Jellyfin's SyncPlay: your Jellyfin account must be
  allowed to use it, and gapless playback is off during a session. How it works
  and its limits: [docs/listen-together.md](docs/listen-together.md).
- *Server activity* reads the server's own list of sessions (`/status/sessions`
  on Plex, `/Sessions` on Jellyfin), which a server only gives to its owner or
  an administrator: on a server shared with you, the entry isn't offered. On
  Plex the list refreshes on the server's own play events (a WebSocket), and
  every 15 seconds or so otherwise; it shows films and series too. Notifications are
  off until you turn them on: while Sillon is open or playing they arrive as
  it happens; with Sillon closed, Android wakes it about every 15 minutes to
  look, so a short listen can be missed. Someone is announced once when they
  start, not at every track, and your own listens never are.
- A *Mystery sleeve* can't stay hidden from a listen together session, which
  shares the queue with every phone: it reveals itself when you join one, and
  can't be drawn during one.
- Loudness levelling only turns tracks down (the player can't amplify), as
  ReplayGain targets a quieter reference than most modern masters: with it on,
  turn the phone volume up a little. Plex needs *loudness analysis* enabled on
  the music library; Jellyfin computes the gains from 10.9 on.
- *Listen by side* uses the disc numbers from the server (sides A–B for disc 1,
  C–D for disc 2…) or side markers in the track titles (`A1`, `Side B`); without
  them, the album is cut at the track boundary closest to half its length.
  Albums under 12 minutes or with fewer than 4 tracks play straight through.
  Shuffle, radios and playlists are never cut, and sides are off while
  listening together (the group decides when to pause).
- *Play on…* (Sonos) uses the speakers' local UPnP control, which Sonos calls
  unsupported and lets you turn off in the Sonos app; the speaker fetches the
  music from your server itself, so it must reach it. Sides, listen together,
  the listening cache and loudness levelling are off on Sonos, and a hidden
  mystery sleeve stays on the phone. Details, formats and limits:
  [docs/sonos.md](docs/sonos.md).
- Sillon is an independent client, not affiliated with Plex Inc. or the
  Jellyfin project. Plex and Jellyfin are trademarks of their owners; the logos
  come from [simple-icons](https://simpleicons.org/) (CC0). Screenshots show
  album covers from the author's own library.
