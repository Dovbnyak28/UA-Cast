# Cast SDK looks up the options provider by reflection.
-keep class com.uacastplayer.cast.CastOptionsProvider { *; }

# Snapshot cache models are (de)serialized by hand-written binary/JSON codecs;
# field names/order must survive obfuscation for compatibility across releases.
-keep class com.uacastplayer.data.snapshot.** { *; }

# media3 decoder extensions and nextlib register renderers via reflection.
-keep class androidx.media3.decoder.** { *; }
-keep class io.github.anilbeesetti.nextlib.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**
