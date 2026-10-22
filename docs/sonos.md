# Sonos: feasibility and design

*Play on…* sends Sillon's queue to a Sonos speaker or group on the same Wi-Fi. The speaker
fetches the music from your Plex or Jellyfin server itself; Sillon becomes the remote.

## Conclusion

**Local control (UPnP/SOAP on the LAN) is feasible and is what Sillon uses.** It needs no
account, no cloud, no relay and no third-party service, so it fits Sillon's rule that nothing
leaves the phone except towards the user's own server (and here, towards the user's own
speakers). Checked on 2026-10-22 against two Sonos Era 100 SL on firmware 97.1 (S2): SSDP
discovery, `ZoneGroupTopology` and the AVTransport actions below all answer on port 1400.

The **Sonos Control API** (cloud) is ruled out: it needs a Sonos developer integration, OAuth,
and goes through `api.ws.sonos.com`, i.e. Sonos's cloud between the phone and the speaker
([docs.sonos.com/docs/control](https://docs.sonos.com/docs/control),
[authorize](https://docs.sonos.com/docs/authorize)). Local control works, so the cloud API is
not needed.

## How it works

| Step | UPnP |
|---|---|
| Find speakers | SSDP `M-SEARCH` for `urn:schemas-upnp-org:device:ZonePlayer:1` on 239.255.255.250:1900 (Wi-Fi multicast lock) |
| Rooms and groups | `ZoneGroupTopology#GetZoneGroupState` on the first speaker that answers; hidden members (Sub, surrounds, stereo pair) are left out |
| Send the queue | On the group **coordinator**: `RemoveAllTracksFromQueue`, `AddMultipleURIsToQueue` (16 tracks per call, DIDL-Lite metadata), `SetAVTransportURI x-rincon-queue:RINCON_…#0`, `Seek TRACK_NR`, `Seek REL_TIME`, `SetPlayMode`, `Play` |
| Controls | `Play`, `Pause`, `Next`, `Previous`, `Seek`, `SetPlayMode`; queue edits with `AddMultipleURIsToQueue`, `RemoveTrackRangeFromQueue`, `ReorderTracksInQueue` |
| Volume | `GroupRenderingControl#SetGroupVolume` / `SetGroupMute`: the phone's volume keys move the group's volume |
| State | `GetTransportInfo`, `GetPositionInfo`, `GetMediaInfo` every second; `GetTransportSettings` and `GetGroupVolume` every 5 s |

Groups: every command goes to the group's coordinator, as the Sonos app does. A group shows as
"Living room + Kitchen".

**State by polling, not GENA events.** GENA (`SUBSCRIBE` to `/MediaRenderer/AVTransport/Event`)
would need Sillon to run an HTTP server on the phone for the speaker to call back, with an open
port and a callback URL to keep alive across Wi-Fi changes. A poll of three small SOAP calls a
second, only while a Sonos session is on, is simpler and good enough for a player screen. This
is the choice to revisit if battery use shows up.

Sillon's media session switches its player to a `SonosPlayer` (a Media3 `SimpleBasePlayer`): the
notification, the lock screen, the player screen, Bluetooth buttons and the volume keys drive
the speaker, and the speaker's state (track, position, pause) comes back to them.

**Back to the phone** pauses the speaker and carries on on the phone at the speaker's track and
position. When the queue was not changed on Sonos, the phone keeps its own queue (a long queue
sent in part comes back whole).

**Losing the speaker.** Sillon lets go and brings the music back to the phone, paused, with a
message, when the speaker stops answering for ~8 s, when it doesn't start within 20 s (usually
it can't reach the server), or when something else plays on it (the Sonos app, AirPlay, another
controller): then the speaker is left alone, not paused.

## The stream

The speaker fetches each URL itself.

**Formats.** Sonos plays MP3, AAC and Ogg Vorbis up to 320 kbps; FLAC and ALAC up to 24 bit;
WAV and AIFF at 16 bit; mono or stereo; nothing above 48 kHz
([music library formats](https://support.sonos.com/en/article/supported-audio-formats-for-sonos-music-library),
[streaming formats](https://docs.sonos.com/docs/supported-audio-formats)). Opus, WMA, DSD, APE,
WavPack are not listed. Sillon decides per track (`SonosFormats.plan`):

| File | Sent as |
|---|---|
| FLAC / ALAC ≤ 24 bit, ≤ 48 kHz | the original file |
| WAV / AIFF 16 bit ≤ 48 kHz | the original file |
| MP3 / AAC / Vorbis ≤ 320 kbps (VBR averages up to 352) | the original file |
| Hi-res (88.2 kHz and up, 32 bit), lossless of unknown rate, Opus, WMA, DSD… | a server transcode to MP3 320 kbps, plain HTTP |

The codec, sample rate and bit depth come from the server: Plex's audio stream
(`samplingRate`, `bitDepth`), Jellyfin's `MediaSources[].MediaStreams[]` (`fields=MediaSources`).
The transcode is one progressive file, not HLS: Plex `music/:/transcode/universal/start.mp3`
with `protocol=http`; Jellyfin `Audio/{id}/stream.mp3?static=false&audioCodec=mp3`.

**Downloads and the listening cache are not used on Sonos**: they live on the phone, and the
speaker can't read them. Offline (no Wi-Fi), no speaker is found and the picker says so.

**Can the speaker reach the server?** Sillon gives the speaker the address the phone uses. A
server on the LAN (`http://192.168.x.x:32400`, a Jellyfin on the LAN) works. A remote address
works if the speaker can resolve and reach it; a `*.plex.direct` address needs the router to let
that DNS name resolve to a private IP (DNS rebinding protection may block it), and a Plex relay
is slow and may not hold a whole album. When the speaker doesn't start within 20 s, Sillon says
it may not reach the server and the music comes back to the phone.

**Tokens.** The URLs carry a token, as every Plex/Jellyfin stream URL does; they go only to the
speaker, on the LAN, over HTTP (or HTTPS if the server address is HTTPS).
- **Plex:** each Sonos session asks the server for a **transient (delegation) token**
  (`GET /security/token?type=delegation&scope=all`, as python-plexapi's `createToken` does:
  [server.py](https://github.com/pkkid/python-plexapi/blob/master/plexapi/server.py)) and puts
  only that token in the speaker's URLs. It is **not limited to the session**: `scope=all`
  covers the whole server with the user's rights, and it lasts **about 48 hours** (or until the
  server restarts); Plex offers no way to revoke it earlier. Its lifetime is not documented by
  Plex: 48 h is the figure commonly reported, not confirmed. **The account token never goes to
  a speaker**: if the server refuses a transient token (an old server, a shared server that
  doesn't delegate, a timeout), the session doesn't start and Sillon says why.
- **Jellyfin:** Jellyfin has no short-lived stream token, so each Sonos session signs the
  speaker in as **a device of its own**: Quick Connect is started as device `<Sillon's id>-sonos`
  (shown as *Sonos (Sillon)* in *Dashboard → Devices*), approved at once with the phone's token
  (`POST /QuickConnect/Initiate`, `POST /QuickConnect/Authorize?code=…`,
  `POST /Users/AuthenticateWithQuickConnect`). Only that token goes in the speaker's URLs, and
  it is revoked (`POST /Sessions/Logout` with it) when the Sonos session ends: back to the phone,
  another room, the speaker lost or taken over, or the playback service stopping. Revoking it by
  hand leaves the phone signed in. Limits:
  - it has the same rights as the user (an administrator's token if the user is one), for as
    long as the session lasts;
  - if the app is killed outright, it stays valid until the next Sonos session signs the same
    device in again (Jellyfin signs a device's earlier sessions out when it authenticates again;
    from Jellyfin's source, not checked on a live server) or until it is revoked by hand;
  - **Quick Connect must be on** (*Dashboard → General*). When it is off, Sonos is refused on
    Jellyfin with a message: Sillon's own token is never handed to a speaker.
- The speaker may keep the last URLs in its queue until something else is played. Anyone who
  can read the Sonos queue on the LAN (the Sonos app, a UPnP tool) can read them: a transient
  Plex token for up to ~48 h, a Jellyfin speaker token until it is revoked.

## Risks

- **UPnP is not an official API.** Sonos calls it "the unsupported UPnP protocol" in its own
  settings, where it can be turned off (on by default)
  ([connection security settings](https://support.sonos.com/en-us/article/adjust-connection-security-settings)).
  In August 2025 Sonos advised turning it off for security
  ([advisory 2025-0002](https://www.sonos.com/zh-cn/security-advisory-2025-0002)). A user who
  does, or a future firmware that drops it, loses *Play on…*: no speaker is found. SoCo
  ([soco.readthedocs.io](https://soco.readthedocs.io/en/latest/)) and node-sonos
  ([github.com/bencevans/node-sonos](https://github.com/bencevans/node-sonos)) depend on the
  same API and would break at the same time.
- Sonos's "Authentication" setting (LAN APIs, off by default) is not tested.
- The local WebSocket API on port 1443 used by Home Assistant
  ([sonos-websocket](https://github.com/jjlawren/sonos-websocket)) is undocumented by Sonos and
  needs an API key; not used.
- The Plex transcode path (`start.mp3`, `protocol=http`) and the Jellyfin `stream.mp3`
  transcode are used only for files Sonos can't play; they were not tested on a real speaker
  if the library tested has none.

## Living with the rest of Sillon

| Feature | On Sonos |
|---|---|
| Listen together | Not available: it plays on each phone. The picker says so; joining a group brings the music back to the phone. |
| Sides (A/B) | Off: the speaker plays the record through. The plan is cleared when the queue goes to Sonos. |
| Mystery sleeve | **Not on Sonos.** The Sonos app and any controller on the LAN show the queue; the album can't be hidden there. While a sleeve is hidden, the speakers can't be picked, and a sleeve started during a Sonos session plays on the phone. Once revealed, it can go to Sonos. |
| Listening cache | Not used: the speaker fetches from the server. |
| Volume levelling (ReplayGain) | Not applied: the speaker's own volume applies. |
| Play counts | Reported to the server by Sillon, as on the phone. |
| Android Auto | Not concerned. |

## Android permissions

- `CHANGE_WIFI_MULTICAST_STATE` (normal, no prompt): the multicast lock for SSDP
  ([MulticastLock](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock)).
- `NEARBY_WIFI_DEVICES` is **not** needed: it gates Wi-Fi Aware, P2P, RTT and local-only hotspot,
  not sockets ([Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)).
- **Local network protection.** Android 16 offers it for testing only
  (`adb shell am compat enable RESTRICT_LOCAL_NETWORK <package>`). It becomes mandatory for apps
  targeting SDK 37 (Android 17), which must declare and request `ACCESS_LOCAL_NETWORK` at run
  time; it covers TCP to the LAN and SSDP
  ([local network permission](https://developer.android.com/privacy-and-security/local-network-permission)).
  Sillon targets SDK 36, so nothing is asked today. **Raising `targetSdk` to 37 must add that
  permission and a prompt before the first search**, or *Play on…* finds nothing.

## What Plex and Jellyfin offer for Sonos

- **Plex** is a music service inside the Sonos app (SMAPI), streaming up to 16 bit / 44.1 kHz
  ([support.sonos.com/en/services/plex](https://support.sonos.com/en/services/plex)). Plexamp can
  cast to Sonos, through Plex's cloud; Plex staff call it "somewhat flaky, due to the way it has
  to live in the cloud due to Sonos architecture" and said they considered removing it (May 2025,
  [forums.plex.tv](https://forums.plex.tv/t/plexamp-cannot-cast-to-sonos/919368)). Sillon's
  path is local, sends the original file up to 24/48, and needs no Plex cloud.
- **Jellyfin** has no Sonos integration. DLNA left the base install in 10.9 for a plugin
  ([jellyfin.org DLNA](https://jellyfin.org/docs/general/post-install/networking/dlna/)), and the
  current Sonos app no longer browses UPnP media servers
  ([forum.jellyfin.org](https://forum.jellyfin.org/t-finding-jellyfin-music-from-sonos-app)). No
  Jellyfin SMAPI bridge was found; bonob does it for Subsonic servers, and must be exposed to the
  internet since Sonos's 2024 app ([github.com/simojenki/bonob](https://github.com/simojenki/bonob)).
  On Jellyfin, Sillon is one of the few ways to play a library on Sonos without a bridge.
