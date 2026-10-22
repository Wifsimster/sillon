# Listen together

Several phones play the same track at the same moment, for a road trip or a party.
One person starts a session and the others join it. Play, pause, skip and seek are
shared.

**Constraint:** no third-party service and no external relay. Messages only go through
the user's own server, or over the local network.

## Feasibility

### Jellyfin: yes, with SyncPlay

Jellyfin has had group playback built in since 10.7. It is called **SyncPlay**. The
server keeps the group, its queue and its state. It tells every member what to do
and *when*, on the server's own clock. Every phone talks only to the server, so
the whole feature stays on the user's own infrastructure.

What a client has to do, read from the server and jellyfin-web sources
(`jellyfin` at `master@08c2ba6`, `jellyfin-web` at `master@17aae05`, with the
defaults checked against tags 10.9.0, 10.10.0 and 10.11.0):

| Need | How | Source |
|---|---|---|
| Groups | `GET /SyncPlay/List`, `POST /SyncPlay/New {GroupName}`, `POST /SyncPlay/Join {GroupId}`, `POST /SyncPlay/Leave` | `Jellyfin.Api/Controllers/SyncPlayController.cs` |
| Queue | `POST /SyncPlay/SetNewQueue {PlayingQueue, PlayingItemPosition, StartPositionTicks}`, `Queue`, `SetPlaylistItem`, `NextItem` and `PreviousItem` with `{PlaylistItemId}` | same, bodies in `Jellyfin.Api/Models/SyncPlayDtos/` |
| Playback requests | `Unpause`, `Pause`, `Seek {PositionTicks}`, `SetShuffleMode`, `SetRepeatMode` | same |
| Readiness | `Buffering` and `Ready` with `{When, PositionTicks, IsPlaying, PlaylistItemId}` | same; checked in `MediaBrowser.Controller/SyncPlay/GroupStates/WaitingGroupState.cs` |
| Server messages | WebSocket `/socket?api_key=…&deviceId=…`: `SyncPlayGroupUpdate` (GroupJoined, UserJoined, UserLeft, GroupLeft, NotInGroup, GroupDoesNotExist, LibraryAccessDenied, StateUpdate, PlayQueue) and `SyncPlayCommand` (Unpause, Pause, Seek, Stop, each with `When` and `PositionTicks`) | `MediaBrowser.Model/SyncPlay/`, `Emby.Server.Implementations/Session/SessionManager.cs` |
| Keep-alive | The server sends `ForceKeepAlive` (60 s). The client answers `{"MessageType":"KeepAlive"}` every 30 s. A socket silent for 60 s is dropped, and its session leaves the group. | `Emby.Server.Implementations/Session/SessionWebSocketListener.cs` |
| Clock | `GET /GetUtcTime` returns `RequestReceptionTime` and `ResponseTransmissionTime`. jellyfin-web keeps 8 NTP samples and uses the one with the smallest delay. It samples every second at first, then every minute. It reports its ping with `POST /SyncPlay/Ping`. | `Jellyfin.Api/Controllers/TimeSyncController.cs`, jellyfin-web `src/plugins/syncPlay/core/timeSync/TimeSync.js` |
| Scheduling | The server schedules each `Unpause` at least `max(2 × highest ping, 500 ms)` ahead. | `Emby.Server.Implementations/SyncPlay/Group.cs`, `PlayingGroupState.cs` |
| Rights | The user's `SyncPlayAccess` policy must be `CreateAndJoinGroups` or `JoinGroups`. The user must also be able to see every item in the queue. | `Jellyfin.Api/Auth/SyncPlayAccessPolicy/SyncPlayAccessHandler.cs`, `SyncPlayManager.cs` |

No session capability is required. A user token and an open `/socket` are enough.

jellyfin-web's own drift correction (`enableSyncCorrection`) is **off by default**.
Out of the box it only relies on the scheduled commands. Sillon corrects drift itself
(see below).

### Plex: no server API

- **Watch Together** ran on plex.tv's services, never on the user's server. It was
  never a public API. On 2025-02-25 Plex announced that it would end in the apps
  moving to the "new Plex experience". It remains, for now, only in Plex's web app
  ([support article](https://support.plex.tv/articles/watch-together/)). Even if it
  were reachable, it would break the "only the user's server" rule.
- **Plex Companion** (`/player/playback/*`, `/player/timeline/*`, GDM discovery;
  [remote-control API](https://github.com/plexinc/plex-media-player/wiki/Remote-control-API))
  lets one controller drive one player. It has no group, no shared clock and no
  schedule.
- Plex Media Server has no time endpoint like `/GetUtcTime`. The `Date` header only
  has 1 s resolution. Its notification socket (`/:/websockets/notifications`) and
  `/status/sessions` report other people's playback, but they generally need the
  owner's token. That is not the case for a user of a shared server.

**Conclusion:** nothing on a Plex server can keep two phones in sync with only the
user's token. For Plex, sync has to happen between the phones themselves, on the
local network (next section). This PR does not include it. On a Plex server, the
*Listen together* screen says so plainly.

### Local network: feasible, planned for a later PR

Two Sillon phones on the same Wi-Fi can sync without any server help. Each one still
streams the music from the Plex server (or from its downloads) with its own token. The
plan reuses everything that does not depend on Jellyfin:

1. **Discovery:** the host registers `_sillon-together._tcp` with Android NSD
   (`NsdManager`, mDNS). Guests browse for it. This needs a `WifiManager.MulticastLock`
   while browsing, so the `CHANGE_WIFI_MULTICAST_STATE` permission. No location or
   nearby-devices permission is needed for NSD.
2. **Pairing:** the host shows a 4-digit code that guests must enter. This keeps
   strangers on a café Wi-Fi out. Messages stay on the local network.
3. **Transport:** the host listens on a plain TCP socket, with one JSON line per
   message. The messages mirror SyncPlay's:
   - `queue {ratingKeys, index, positionMs}`
   - `command {type, whenMs, positionMs, itemId}`
   - `ready` and `buffering`
   - `ping`
   - `members`
4. **Clock:** guests run the same NTP exchange against the host's monotonic clock
   (`ClockSync`, unchanged). The host is the reference, the way the Jellyfin server
   is.
5. **Group logic:** the host plays the role of the Jellyfin server. It waits for every
   guest's `ready`, then schedules `Unpause` at `now + max(2 × highest RTT, 500 ms)`.
   `TogetherMachine`, `CommandPlanner`, `DriftCorrector`, `TogetherPlayer` and the UI
   stay as they are. Only the transport behind `ListenTogether` changes.
6. **Same tracks:** guests must use the same Plex server as the host, because the
   `ratingKey`s are per server. If a guest can't see a track (a shared library with
   different rights), it says *Can't play this track*, as with Jellyfin.
7. **Leaving:** a guest that loses the host for 20 s leaves the session and pauses. If
   the host leaves, the session ends for everyone.

Rough size: 600 to 800 lines plus tests. Testing it needs two real phones on the same
Wi-Fi.

## What this PR does (Jellyfin)

- **`together/ClockSync`** maps the phone's monotonic clock (`elapsedRealtime`) to the
  server's UTC. It uses NTP over `/GetUtcTime`: 8 samples, the shortest round trip
  wins, every second until synced and then every minute. It needs 3 samples before
  joining.
- **`together/TogetherMachine`** is the session's state machine, pure and unit-tested:
  `Off → Connecting → InGroup → Ended → Off`. Every way out of a group ends in
  `Ended(reason)` and gives the player back to the phone.
- **`together/CommandPlanner`** turns server commands into player actions:
  - It plays at the scheduled instant.
  - It starts further into the track when a command arrives late.
  - It pauses at the instant and settles on the shared position.
  - On a seek it pauses, seeks and then reports `Ready`.
  - It ignores commands meant for another track.
- **`together/GroupRequests`** turns what the user presses into group requests. Play,
  pause, seek, next and previous are covered (previous restarts the track after 3 s,
  like everywhere else). So are picking a track, playing an album (it replaces the
  group's queue), adding tracks, shuffle and repeat.
- **`together/DriftCorrector`** handles the gap:
  - Under 25 ms it does nothing.
  - From 25 ms it changes the speed by at most ±5 %, spread over 2 s, with the pitch
    kept.
  - Once a correction has started, it continues until the gap is under 8 ms.
  - From 600 ms it seeks a little ahead of the group and starts exactly when the group
    gets there.
- **`playback/TogetherPlayer`** is the player the media session exposes. In a session,
  controls from the app, the notification, headset buttons and **Android Auto** become
  group requests. The phone only moves when the server's command comes back, like
  every other member. Android Auto therefore works as a plain member, with nothing
  specific to the car.
- During a session the player pauses at the end of each track. The group then moves
  on together: every member sends `NextItem` with the track that just ended, and the
  server acts only on the first request.
- **Screen:** *Listen together* is reached from the player (group icon) and from the
  library's ⋮ menu. It shows:
  - the sessions on the server, with a button to start one;
  - inside a session: its state, *This phone* with its own status and its gap with the
    group in ms, and the members.

### Members and their state

Jellyfin only shares participants' **names** and the **group's** state (Idle, Waiting,
Paused, Playing). It does not share each phone's own state. The screen therefore
shows:

- every member with the group's state (*Listening*, *Paused*, *Waiting for everyone to
  load*);
- this phone in detail (*Loading*, *Ready*, *In sync*, *Catching up*, *Can't play this
  track*) and its measured gap.

The screen says so in one line. Two phones signed in to the same Jellyfin account
appear as the same name twice.

### Losing the network

- OkHttp pings the socket every 10 s. A missed pong fails the socket, so a dead
  network is noticed within about 10 to 20 s.
- The server takes a session out of its group as soon as the socket closes. Sillon
  then reopens the socket and joins the same group again for up to 20 s. Meanwhile the
  screen shows *Connection lost. Trying to get back in…*
- If the connection doesn't come back, the phone leaves the session cleanly. It pauses
  the music, gives the player back, and says why: *The connection to your server was
  lost…*
- The other members see it leave as soon as the server notices: at once on a clean
  close, and within 60 s (the server's keep-alive timeout) if the network just
  vanished. Until then, a group waiting for every member to be ready can wait for that
  phone.

### Offline

Listening together needs the server. With no connection, the screen says so, and
*Start a session* is disabled. In a session, downloaded tracks are played from the
phone, which is lighter for the network, and the position still comes from the group.

## Drift: target and how it is measured

**Target:** about 100 ms or less between phones once settled.

- **Per phone.** Every 250 ms while playing, the phone computes where the group is
  now: `position in the last command + (server now − When)`. Server now comes from
  `ClockSync`. The phone then compares that with its player's position. The difference
  is the phone's **drift**. It is what the corrector acts on, and it is shown on screen
  (*Gap with the group: N ms*). It is also logged every second under the
  `SillonTogether` tag:

  ```
  adb logcat -s SillonTogether
  drift=7ms rtt=18ms offset=…ms speed=1.0
  ```

- **Between two phones.** The gap between A and B is at most
  `|drift A| + |drift B| + clock error A + clock error B`. Each clock error is at most
  half the round trip of the sample in use (`rtt/2`, also in the log). On home Wi-Fi
  with RTTs of 10 to 40 ms and drifts held under 25 ms, the budget is roughly 40 to
  90 ms.
- **What the phone can't see:** audio output latency after the player. ExoPlayer's
  position follows the `AudioTrack` timestamps, which include the device's own output
  path. A Bluetooth speaker or headset (and a car over Bluetooth) adds its own 100 to
  250 ms, which no member can measure. Phones on their own speakers, or on wired
  output, are the fair comparison.
- **Acoustic check (to do with two phones):** play a click track on both phones (a
  1 kHz beep each second, for example generated with
  `ffmpeg -f lavfi -i "sine=1000:duration=0.02,apad=pad_dur=0.98" -t 120 click.flac`
  and added to the test library). Record both with one microphone placed at the same
  distance from each, then measure the time between paired clicks (Audacity, or
  `ffmpeg -af silencedetect`). Take readings at 10 s, 1 min and 5 min, and after a
  pause, a seek and a skip.

## What was tested

- **Unit tests** (`./gradlew testDebugUnitTest`) cover:
  - the clock offset (`ClockSyncTest`);
  - the drift corrector, including a simulated catch-up from 100 ms (`DriftCorrectorTest`);
  - the session state machine (`TogetherMachineTest`);
  - commands and user controls (`CommandsTest`);
  - the server messages, with shapes copied from a real 10.10.7 server (`SyncPlayMessagesTest`).
- **Protocol, two members, real server:** a local Jellyfin 10.10.7 in Docker, with two
  demo accounts and three synthetic sine-tone tracks. It was driven in two ways:
  - by a throwaway Node script, for the setup and a first 18-check run;
  - by `SyncPlayServerTest`, which uses Sillon's own `SyncPlayApi` and parser. It is
    skipped unless `SILLON_JELLYFIN_URL` and `SILLON_JELLYFIN_USERS` are set.

  Checked: create, list, join, shared queue, `Ready` leading to `Unpause` with the same
  `When` for both, `Unpause` scheduled about 1 s ahead, pause from the other member
  with one shared position, seek, next asked by both members but applied once,
  a dropped socket reported as `UserLeft`, rejoin, leave, and an empty group
  disappearing.

  Found on the way: the server answers `NotInGroup` to a group request that races
  the join. The state machine now ignores it while connecting.
- **Not tested:** two real phones playing audio, the measured gap between them, the
  acoustic check above, Android Auto (no car or Desktop Head Unit here), a real Wi-Fi
  loss, and mixing Sillon with jellyfin-web members. jellyfin-web uses the same
  protocol, so mixing should work, but that is unverified.

## Known limits

- Gapless playback is off during a session. Each track pauses at its end until the
  group moves on, which leaves a gap of about 1 s (the server's scheduling lead).
- Someone joining a playing session pauses everyone for about a second, while the
  newcomer loads. This is how SyncPlay works.
- Speed correction changes the speed by at most ±5 % with the pitch kept. On sustained
  notes a trained ear may notice it while a correction runs (up to about 2 s).
