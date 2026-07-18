# Cast SDK looks up the options provider by reflection.
-keep class com.uacastplayer.cast.CastOptionsProvider { *; }

# No -keep needed for cache/snapshot models: every codec (EpgSnapshotCodec,
# PlaylistSnapshotCodec, favorites/MiniJson, etc.) reads/writes fields by hand,
# with no reflection or serialization library involved, so obfuscated field
# names never leak into persisted data. Don't re-add a keep rule here without
# first checking a codec actually needs one.

# media3 decoder extensions and nextlib register renderers via reflection.
-keep class androidx.media3.decoder.** { *; }
-keep class io.github.anilbeesetti.nextlib.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**
