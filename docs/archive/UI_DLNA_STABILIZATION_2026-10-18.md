# DLNA controls and player metadata stabilization — 2026-10-18

## Confirmed and repaired

1. **DLNA readback interrupted a new volume gesture.** `DlnaVolumeRow` cleared its
   draft whenever the repository published a volume. A response to an earlier SOAP
   command could therefore move the thumb during a second drag and discard its
   pending command. The draft now belongs only to the active gesture and is cleared
   when committed. Outside a gesture, the UI follows the repository, including a
   renderer refusing or clamping the requested volume.
2. **A volume gesture could cross renderer ownership.** Replacing the connected TV
   while dragging, with the same reported volume, retained the old slider draft.
   Releasing sent that value to the new target. The slider composition is now keyed
   by the AVTransport and RenderingControl endpoints. The regression test changes
   TV during a held pointer gesture and verifies that no command is sent. Commands
   also carry the device captured by the displayed UI; the repository checks both
   endpoints before publishing an optimistic value or starting SOAP. A second test
   invokes an old UI callback after reconnect, before UI recomposition could help.
3. **Large text squeezed DLNA connection status.** At 320 dp width and 200% font
   scale, the inline Stop button left the Ukrainian status on five lines, Russian
   on twelve, Spanish on six. The card now puts Stop below its status when the
   width available relative to font scale is insufficient. Regular text retains
   the compact inline layout. No animation, polling or image dependency was added.
4. **Player metadata displayed incorrect codec/layout names.** `audio/mpeg` was
   called MP2 instead of MP3; MPEG layer I had no label; SSA/ASS subtitles were
   labeled SRT. Audio layouts other than mono/stereo/5.1/7.1 were called stereo.
   Labels now distinguish the formats, positive nonstandard channel counts are
   called multichannel, and invalid channel counts do not create a measured badge.

## Evidence and limits

- Before fixing volume ownership, both new pointer-interaction tests failed.
- Before the responsive card, three of four localized large-text tests failed.
- Before the codec correction, the new MPEG/subtitle label test failed.
- The new Ukrainian large-text reference image was reviewed visually. The previous
  54 reference images were not re-recorded.
- The suspected file-seek permission-loss problem was **not reproduced**: both
  cancellation-on-disable and completed-gesture tests passed before any production
  change. The seek implementation was retained.
- Final unit regression: **2755 Debug + 2338 Release tests**, zero failures/errors.
  Debug includes **55 screenshot references** and the stale-callback regression.
  Detekt and architecture/locale checks pass. App Debug lint has zero errors and
  warnings, with two pre-existing hints. Debug APK and AndroidTest APK assembly pass.
  The final combined Gradle gate completed successfully in 7m 16s.
- `app/build/outputs/apk/debug/app-universal-debug.apk` is the locally built test APK
  (`com.uacastplayer.debug`, version name unchanged at 0.9.7). It was not installed
  on a physical device or published. Instrumented tests were compiled, not run.
- The pre-existing packaging warning about unstripped `libffmpegJNI.so` remains;
  this pass does not claim to resolve native symbol/size optimization.
- Reproduction and verification logs are local, ignored files under `build/legal-audit/`:
  `ui-stabilization-before-2026-10-18.log`, `ui-metadata-before-2026-10-18.log`,
  `ui-stabilization-targeted-2026-10-18.log`, `ui-large-text-after-2026-10-18.log`
  and `ui-stabilization-final-gate-2026-10-18.log`.
- ADB currently lists no connected phone or TV. This pass does not establish
  physical-device acceptance or resolve Hisense network/protocol interoperability.
- Native decoder licensing/publication prerequisites from the previous migration
  report remain separate. No release, version bump or GitHub publication is made
  by this stabilization pass.
