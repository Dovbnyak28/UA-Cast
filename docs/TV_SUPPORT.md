# Android TV and UA-Cast phone remote

Implemented on 2026-09-30. This is Android TV / Google TV support (Android 7+, the existing
minSdk 24), **not** a Hisense VIDAA system remote. The same universal APK installs on the phone
and Android TV. Install it on both devices; the TV uses its own saved playlists.

The TV launcher has a dedicated 16:9 filtered HD bitmap banner, generated from editable
vector artwork, with a large UA CAST name and unified TV/play/cast mark. Phone icons remain
unchanged. HD rendering/native checks are recorded in
[TV_BANNER_HD_VERIFICATION_2026-09-30.md](TV_BANNER_HD_VERIFICATION_2026-09-30.md).
Initial design, banner renders, automated
checks and actual Xiaomi OEM-launcher acceptance are recorded in
[TV_LAUNCHER_BANNER_VERIFICATION_2026-09-30.md](TV_LAUNCHER_BANNER_VERIFICATION_2026-09-30.md).

## Connect a phone

1. Open UA-Cast on the TV. Choose the remote icon in the top bar, or **Pair phone** in the player.
2. On the phone, open the remote icon and choose **Remote** or **Touchpad**.
3. Use the same local network (TV Ethernet + phone Wi-Fi also works). Enter the IPv4 address,
   port and eight-digit code displayed by UA-Cast on the TV. No automatic device discovery yet.
4. Press **Connect**. The TV pairing dialog closes on successful authentication. The phone can
   switch between remote buttons and touchpad without reconnecting.

Authentication can take about 13 seconds on an older Xiaomi / Android 9 crypto provider.
It has a bounded 40-second budget; do not repeatedly press Connect while it says Connecting.
This does not lower PBKDF2's 160,000 iterations. Waiting for a command acknowledgement still
has a five-second bound, and the server's worker/admission limits are unchanged.

The code expires after five minutes. **New code** replaces the listener and retires the previous
connection. Close the phone remote or background either application to end control. Activity
recreation also ends the remote connection; pair again after a language/configuration change.
A connection error is retryable: open pairing on the TV and use its current address/code again.
An idle connection expires after five minutes without commands, rather than retaining a socket
forever. Public IPs, DNS names, IPv6 and VPN/cellular endpoints are intentionally unsupported.

The phone observes a receiver socket closing even without a button press and returns to a
retryable pairing state. A single owned idle reader does not send heartbeats or keep the
five-minute TV idle timer alive. A silent network black hole without TCP EOF/RST is different:
the next command's acknowledgement deadline detects it; immediate idle detection of every
Wi-Fi/power loss is not claimed. See
[REMOTE_EOF_STABILIZATION_2026-09-30.md](REMOTE_EOF_STABILIZATION_2026-09-30.md).

## Controls

- TV: D-pad / OK traverses channels, favorites, sources, settings and player controls. Visible
  focus rings, larger text, a navigation rail and overscan padding replace the phone layout.
- The player reuses the existing PlayerHost/PlayerViewModel/Media3 owner. It has a TV overlay,
  previous/next channel, play/pause, favorites, aspect ratio, audio/subtitle selection, EPG and
  sleep timer. Exit closes playback instead of leaving a phone mini-player behind.
- Single-line fields use up/down to leave the field; left/right remain text cursor navigation.
  Read-only Terms/EPG rows are focusable to make their lazy lists scrollable with D-pad.
- Phone buttons: directions, OK, Back, play/pause, previous/next and media volume.
- Touchpad: swipe moves **focus**, tap selects. This is not a free mouse cursor and cannot
  operate the TV home screen, other applications, system settings or the system keyboard.
- Parental PIN, playlist persistence, favorite sorting and update/install rules stay in the
  existing shared application pipeline. The phone tour is not shown on the TV grid.

## Ownership and security

`core.remote` contains only the command whitelist and gesture policy. `data.remote` owns
framing, crypto and bounded socket handling. The `remote` ViewModels own the phone/TV jobs and
state. `ui.remote`, `ui.tv` and the TV player own presentation. No data-to-UI dependency or
UI-to-ProxyServer dependency is introduced.

The receiver starts only after an explicit pairing action and binds a specific private LAN
IPv4 address on an ephemeral port, not `0.0.0.0`. Pairing and commands use AES-256-GCM with a
fresh per-connection salt, PBKDF2 key derivation, random nonces and authenticated sequence
numbers. The pairing code is neither persisted nor logged. This is a short-lived PIN-based
local protocol, not a claim of independent security certification.

There are two client workers, no executor backlog, bounded command channels and a 256-byte
frame limit checked before allocation. A global authentication-attempt budget and absolute
handshake deadline prevent byte-dribbling from retaining a worker indefinitely. All sockets,
including pending handshakes, close on receiver stop. Session generations, connection IDs and
command age checks reject stale input. Commands dispatch only to this Activity or its own
registered Compose dialog; no accessibility service or privileged system input injection.

## Verification and remaining acceptance

Automated tests cover encrypted loopback delivery, wrong codes, replay/tampering, malformed
frames, slow handshakes, connection replacement, stop/cancellation, ViewModel destruction,
phone mode selection/gestures, small-phone pairing, TV D-pad focus/search/favorite sorting and
dialog input cleanup. Native-rendered screenshot baselines cover TV channels and phone controls.
Samsung SM-M156B / Android 16 passed nine instrumented regressions covering the phone
chooser, pairing form, actual touch dispatch, Android crypto/socket delivery, Activity lifecycle,
connection replacement/cancellation and application launch. A physical Xiaomi Android 9 TV
also passed all five targeted native crypto/lifecycle tests after correcting the handshake
budget. Real Samsung-to-Xiaomi LAN pairing and DOWN/OK delivery were observed separately.
Full Ukrainian TV rail labels were checked on the actual 720p display and with a strict layout
test; the phone rail width and labels remain unchanged.
An opt-in physical-TV acceptance additionally passed moving-video rendering, pause/resume,
Activity background/foreground/recreation, 30 rapid channel replacements and Back-exit cleanup.
Real touchpad swipes/taps selected TV Favorites; switching to D-pad stayed connected and UP/OK
selected Channels. These LAN UI observations are separate from automated loopback coverage.

After direct computer Wi-Fi access became available, the latest debug revision passed another
five native crypto/lifecycle tests on Xiaomi, including receiver EOF without a button press,
and the moving-video/lifecycle/30-switch/Back-exit acceptance passed again. The new banner was
observed on the actual launcher in normal and focused states after refreshing its stale cache
without clearing data. This pass did not repeat separate phone-to-TV LAN control; exact current
artifact/evidence: [XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md](XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md).

**Still required before claiming production TV certification:** finish codec/audio and provider
stream acceptance, physical OEM-remote usability, clean-install language/Terms, TV keyboard/
permission/install windows, Ethernet/Wi-Fi reconnection and prolonged playback. Manual UI
observations and loopback tests are not substitutes for these checks. Current physical-device
results and limitations are recorded in [XIAOMI_TV_VERIFICATION_2026-09-30.md](XIAOMI_TV_VERIFICATION_2026-09-30.md).

On 2026-10-02, Mi A2 Android 11 additionally passed 20 isolated native tests: artwork refresh,
all confirmation/PIN and empty-modal routes, remote lifecycle and platform crypto. The full
host regression and signed APK build also passed. This is phone-side/native-window acceptance,
not a new physical Xiaomi TV or LAN control run. See
[STABILIZATION_AUDIT_2026-10-02.md](STABILIZATION_AUDIT_2026-10-02.md).
