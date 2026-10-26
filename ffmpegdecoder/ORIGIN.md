# FFmpeg decoder wrapper — staged replacement

Java/JNI wrapper sources originate from AndroidX Media (Apache-2.0),
https://github.com/androidx/media/pull/1591, immutable contribution commit
`ddf578a27a97e6296062d1997ba41784b0dcf497` in `rabbitknight/media`.
Original AOSP copyright headers are retained. The contribution is not an
upstream production release; integration and destructive native tests are
required before it replaces NextLib. Local modifications must be recorded here.

FFmpeg and any native dependencies retain separate licences. This module is
not a legal assertion that corresponding-source/relinking requirements are
already complete. The application is not relicensed under GPL.

No NextLib Java classes or GPL JNI wrapper are copied into this module.
It is wired through Media3 DefaultRenderersFactory in the application. Hardware
remains the first choice, with both software renderers available as extensions.
Publication remains blocked until the separate release-compliance gate is approved.

Local changes (2026-10-11): use a literal ProRes MIME type for compatibility
with the pinned Media3 API; use AndroidX Nullable instead of Checker Framework
annotations. Build metadata pins existing app annotation/Kotlin versions.

Local changes (2026-10-12): the video decoder no longer uses SimpleDecoder's
one-input/one-output model. It has bounded input/output pools, one native-owner
worker, retained EAGAIN input, generation-based flush and explicit delayed-frame
EOS draining. Interrupted release waits for the worker before freeing native
resources. JNI validates direct-buffer capacities, zeroes FFmpeg padding,
checks allocation/field lookup failures, bounds dimensions, clears failed cached
swscale contexts and uses actual resampled byte counts (not sample capacity).
Native constructor failures release resources/workers. Trace cleanup is in a
finally block. MPEG layer I/II use their actual built-in decoder names.

FFmpeg is independently compiled from 6.1.6 sources with GPL, nonfree and
version3 disabled; native VP8/VP9 replace external libvpx. No AAR binaries are
reused. The build-only response-file/header-list patch in scripts/patches avoids
Windows command-line truncation and is part of the corresponding source inputs.
The complete source/relink distribution and performance acceptance remain
release prerequisites, not established merely by the local smoke tests.

Synthetic media fixtures are generated from test patterns and sine waves by
scripts/generate-decoder-fixtures.py. The standalone CLI encoder is NOT part of
the module/APK dependency graph. Android tests force software decoding and check
frame/sample counts, EOS, buffer ownership, failures and Media3 lifecycle paths.
