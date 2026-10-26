# Local video playback and casting — 9 October 2026

## Implemented scope

- Home and TV Home expose an Android document-picker action for a video file.
  Ordinary documents and local HLS playlist files are not silently treated as video.
- `Open with` accepts granted `content://` video documents. HTTP/file paths are
  not a new exported streaming/file-system endpoint. First-run language/terms
  still precede playback; an incoming request remains with the Activity-scoped
  selection owner until the main screen is available.
- One immutable, bounded descriptor carries URI, title and MIME. File bytes are
  never put in a Bundle, channel-source store, backup, or whole-file memory cache.
  A selected document reuses the existing single Media3 engine and player UI.
- Only a retained SAF read grant creates a process-restoration marker. Temporary
  grants remain session-only. A video does not overwrite the last IPTV channel
  or get added to channel Favorites. Missing/revoked files fail immediately,
  not through Internet retry or channel auto-skip.
- Chromecast always receives the local, token-protected proxy URL, buffered
  metadata and the actual file MIME; it never receives an unusable phone URI.
  Normal file completion is not handled as a failed endless IPTV stream.
- DLNA reuses the same protected proxy and sends finite `videoItem` metadata
  with the actual MIME and byte-seek capability, rather than `videoBroadcast`.
  Existing live HLS/TS proxy/metadata behavior is retained.
- File responses support GET/HEAD, bounded/open-ended/suffix single ranges,
  206/416, lengths above 4GiB, and a 64KiB streaming buffer per admitted request.
  Wrong tokens/unknown resources are rejected before any provider is opened.
  Replacement/stop retire the source and close open/late descriptors. Reads of
  a previous selected file cannot be revived through its old proxy URL.

No broad storage permission, library/dependency, Cloud account or video
transcoder was added. Picker absence/known TV stub, user cancellation and
unreadable documents have explicit UI handling. Provider metadata work is on
IO with cancellation signals for cancellable provider operations.

## Honest format/receiver boundaries

Selection recognizes MP4/M4V, MOV, MKV/WebM, AVI, TS/MTS/M2TS, MPEG/VOB, 3GP,
FLV, WMV/ASF, OGV and several legacy MIME types. **Selection is not proof of
decoding support.** The container, codecs, DRM and platform decoders must be
supported by Media3/the phone; the receiver must independently support them.
Unknown or unsupported input shows an error instead of a claim that all formats
work. See [Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats)
and [Google Cast media support](https://developers.google.com/cast/docs/media).

This implementation relays original bytes. It does not convert an unsupported
codec/container, bypass DRM, mirror a desktop, or prove Hisense VIDAA acceptance.
The existing DLNA sender remains limited in remote pause/seek/position controls;
the range endpoint allows the receiver's own byte-seeking. Seamless current-
position transfer between phone and receiver is not claimed. On a file's cast
disconnect, phone playback is prepared/paused rather than unexpectedly replayed.

## Stabilization and architecture

The first compilation succeeded. Detekt rejected increased complexity/size and
new fixture/style defects; these failed runs were retained, not relabeled green.
Its limits and empty baseline were not changed. Media3 event wiring/track UI
mapping, retry-job ownership and error-policy execution now have separate
collaborators. Saved marker serialization and async restoration are outside
MainActivity/MainAppContent. Existing listener/engine lifetime is unchanged;
the retry owner remains a child of the ViewModel scope. New user selection wins
over stale metadata/restoration work.

54 focused tests passed after removing a new fixture's unavailable offline
SDK override. The initial 13 failures were missing Robolectric runtime jars,
before their test bodies ran, not production crashes. Later runs cover the
new picker UI, retry owner, file-grant failure and seven unchanged live-window
lifecycle cases; all executed host cases pass. The final quality/APK gate
passed in 2m35s: lintDebug, detekt, debug/test assembly; no new suppressions.
All twelve architecture/privacy/Premium/fixture checks pass.

## Device verification: record failures precisely

Mi A2 is Android 11 / API 30. The test uses only an offline-generated silent
MP4 in its own cache filename. The preserved-data helper saves/restores the
debug app's private files/preferences and verifies original hashes; the main
release package is not installed, cleared or modified.

The first two native cases failed **in fixture generation**, before playback
or HTTP assertions: the phone's AVC encoder rejected 64x64/flexible-YUV setup
with CodecException `0xfffffc0e`. Three original private file hashes were verified
after restoration. The generator was changed to QVGA and an advertised supported
YUV buffer format. This is not asserted to fix a production decoder problem.
The exact revised fixture passed compilation/lint in 45s, without changes to
production/host-test inputs. It then passed both native cases in 17.770s on Mi A2:
actual ContentResolver/HTTP HEAD+range+descriptor closure and actual Media3
playback/seeking/release with the previous IPTV key unchanged.

A combined Mi A2 regression subsequently passed **21/21 in 109.064s**, zero
reported failures: the two new cases, fifteen existing lifecycle/player-instance
cases and four video-fit cases. Original private file hashes (three files) were
verified after every restored-data run. Those are phone/local-proxy results,
not a test of a physical Cast/DLNA receiver or every container/codec combination.

Ignored evidence: `build/local-video-*.log`, `app/build/device-audit/`.
Recovery archives may contain credentials and must never be published.

## Remaining verification

The complete isolated gate passed in **10m50s** (227 tasks: 69 executed, eleven
from cache, 147 up-to-date): **Debug 2699, Release/Play 2306 each**, zero host
failures/errors/skips; unchanged Core 133 results reused. All **49 goldens are
unchanged**, zero added/changed. Three diagnostic background previews under
ignored build output are not source-baseline updates. Lint has zero errors /
warnings; two informational Hints concern the existing API-36 target and Compose
BOM version, not a new runtime failure. Detekt, APKs and both app/harness
benchmark Kotlin variants passed. Rules/budgets/baselines were not relaxed.

Real Chromecast/Mi TV/Hisense receiver acceptance remains separate. Compilation
and MIME/range tests do not certify every format or TV. Older measured frame
budget overruns are not claimed fixed by this feature. No release/version bump
or computer shutdown was requested for this pass.

## Follow-up stabilization

The subsequent evidence-first pass fixed three reproduced boundaries: repeat
selection identity/acknowledgment, a queued local decoder error under remote
ownership, and a throwing provider-cancellation callback skipping FD closure.
The complete gate passed with Debug 2702 / Release and Play 2308 each; 49
goldens unchanged. The final production APK passed 21/21 Mi A2 regressions in
90.551s with three original private file hashes restored. Test-only compiler
warning cleanup then passed another gate without relaxing assertions or rules.
See [the detailed follow-up evidence](LOCAL_VIDEO_STABILIZATION_2026-10-09.md)
for negative/positive results, exact cleanup coverage and receiver boundaries.

A further native-first pass found and fixed completed-file playback intent /
replay (UI and MediaSession). Both new device cases failed before the fix;
the final APK passed 23/23 Mi A2 cases. The full gate passed Debug 2704 /
Release and Play 2310 each, with 49 unchanged goldens and no relaxed rules.
See [the completion/replay evidence](LOCAL_VIDEO_COMPLETION_2026-10-09.md).

The next Cast-file lifecycle pass fixed load identity, first FINISHED handling
and matching completed-media retention across same-session resume. The full
gate passed Debug 2710 / Release and Play 2316 each. Five LocalVideo native
cases passed, including SDK Parcel plus actual Range bytes; the combined
24-case run was stopped after secure-keyguard setup failures, with original
data restored. It is not a complete native green result. See
[the Cast lifecycle evidence and limits](CAST_FILE_LIFECYCLE_2026-10-09.md).

That Cast native run later passed 24/24 after actual device unlock. A subsequent
DLNA XML/file-MIME pass fixed qualified field parsing, volume/status XML text,
provider MIME precedence and local HLS alias rejection. The full gate passed
Debug 2722 / Release and Play 2328 each, and the exact final APK passed 26/26
Mi A2 cases with original private data restored. See
[the XML/MIME evidence and TV boundaries](DLNA_XML_AND_FILE_MIME_2026-10-09.md).
