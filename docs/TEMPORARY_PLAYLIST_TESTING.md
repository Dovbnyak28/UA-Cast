# Private temporary playlist test

The optional Gradle property `uacast.temporaryPlaylist` embeds a local M3U file
as `assets/temporary-playlist.m3u8` **only in debug APKs**. It never registers the
asset with release, Play or benchmark variants. The source file remains outside
the repository; generated copies are under ignored `app/build/`. Do not share
these debug APKs: embedded playlists can contain private stream credentials.

Example (use an absolute local path):

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest "-Puacast.temporaryPlaylist=C:/private/playlist.m3u8"
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.uacastplayer.playlist.TemporaryPlaylistInstrumentedTest -e temporaryPlaylist true com.uacastplayer.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The opt-in test imports through the real file/controller/repository pipeline,
preserves existing saved sources, checks all imported stream addresses against
the fixture, samples up to ten streams, and exercises player lifecycle. It
requires moving video, not just HTTP success. Results expose only channel
indices, timings, dimensions and numeric error/status codes. Optional
`-e temporaryPlaylistIndices 0,10,100` selects zero-based source-file indices.
Normal CI runs skip this external-network test. A successful test means the
reported sample and lifecycle checks passed, **not that every channel works**.

`-e temporaryPlaylistTimeoutMs 60000` extends an individual playback probe for
diagnosis. To inspect bounded manifest/first-segment responses separately, select
the method `#inspectSelectedManifestsWithoutPlayback` with
`-e temporaryPlaylistNetworkProbe true` and the desired indices. It reports HTTP
status and PMT-declared codecs, not the private addresses. A valid manifest or
PMT does not prove that its media can be decoded.

The imported source remains in the separate `.debug` app for manual testing.
Remove "Temporary device test" through the playlist UI when finished. Its private
copied file remains in debug app storage until that app is uninstalled/cleared.
Building debug again **without** the property removes the embedded asset,
including after a previous opted-in build; it does not delete data on a phone.
Never upload fixture-bearing APKs or unfiltered Media3 logcat output to CI/GitHub.
Downstream Android asset/APK tasks may also keep local Gradle build-cache copies;
use this option only in a private local build, never with a shared/remote cache.
