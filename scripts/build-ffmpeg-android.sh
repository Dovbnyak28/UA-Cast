#!/usr/bin/env bash
# Independently build the decoder libraries, never copying a GPL wrapper or an AAR.
# Inputs are pinned/hashed in ffmpegdecoder/native-sources.json. No downloads here.
set -euo pipefail

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an extracted Android NDK}"
: "${FFMPEG_SOURCE:?Set FFMPEG_SOURCE to the verified FFmpeg source directory}"
: "${FFMPEG_BUILD_ROOT:?Set FFMPEG_BUILD_ROOT to a dedicated generated-build directory}"
abi="${1:-arm64-v8a}"
jobs="${NATIVE_BUILD_JOBS:-4}"
case "$abi" in
  arm64-v8a) arch=aarch64; target=aarch64-linux-android24 ;;
  armeabi-v7a) arch=arm; target=armv7a-linux-androideabi24 ;;
  x86_64) arch=x86_64; target=x86_64-linux-android24 ;;
  *) echo "Unsupported ABI: $abi" >&2; exit 2 ;;
esac
case "$(uname -s)" in
  MINGW*|MSYS*) host=windows-x86_64; extension=.exe
    # GNU make cannot reliably address whitespace-containing source directories.
    ndk="$(cygpath -ms "$ANDROID_NDK_HOME")"
    source="$(cygpath -ms "$FFMPEG_SOURCE")"
    mkdir -p "$FFMPEG_BUILD_ROOT"
    build_root="$(cygpath -ms "$FFMPEG_BUILD_ROOT")"
    make_program="$ndk/prebuilt/windows-x86_64/bin/make.exe" ;;
  Linux*) host=linux-x86_64; extension=
    ndk="$ANDROID_NDK_HOME"; source="$FFMPEG_SOURCE"; build_root="$FFMPEG_BUILD_ROOT"
    make_program=make ;;
  *) echo "Unsupported build host" >&2; exit 2 ;;
esac
if [[ "$source$build_root$ndk" == *' '* ]]; then
  echo "Native make inputs must have whitespace-free paths (Windows short paths are supported)" >&2
  exit 2
fi
toolchain="$ndk/toolchains/llvm/prebuilt/$host"
if [[ ! -f "$source/RELEASE" ]] || [[ "$(< "$source/RELEASE")" != 6.1.6 ]]; then
  echo "Expected the pinned FFmpeg 6.1.6 source tree" >&2
  exit 2
fi
script_root="$(cd "$(dirname "$0")" && pwd)"
if ! grep -F '@$$@.rsp' "$source/ffbuild/library.mak" >/dev/null; then
  # Build-only change supplied with the corresponding source; avoids Windows command truncation.
  patch -d "$source" -p1 --forward < "$script_root/patches/ffmpeg-6.1.6-windows-response.patch"
fi
mkdir -p "$build_root/$abi/objects" "$build_root/$abi/install"
cd "$build_root/$abi/objects"

# Match the existing audio/video feature set using LGPL-compatible built-in decoders.
# Networking/demuxing stays in Media3. No x264/x265, GPL/nonfree, external TLS or encoders.
decoders="aac,ac3,eac3,dca,mlp,truehd,mp1,mp2,mp3,vorbis,opus,flac,alac,pcm_mulaw,pcm_alaw,amrnb,amrwb,h264,hevc,mpeg1video,mpeg2video,vp8,vp9"
bash "$source/configure" \
  --prefix="$build_root/$abi/install" \
  --target-os=android --arch="$arch" --enable-cross-compile \
  --cc="$toolchain/bin/clang$extension --target=$target" \
  --cxx="$toolchain/bin/clang++$extension --target=$target" \
  --ar="$toolchain/bin/llvm-ar$extension" \
  --nm="$toolchain/bin/llvm-nm$extension" \
  --ranlib="$toolchain/bin/llvm-ranlib$extension" \
  --strip="$toolchain/bin/llvm-strip$extension" \
  --sysroot="$toolchain/sysroot" \
  --extra-cflags="-fPIC" --extra-ldflags="-Wl,-z,max-page-size=16384" \
  --enable-shared --disable-static --enable-pic --enable-small \
  --disable-gpl --disable-nonfree --disable-version3 --disable-autodetect \
  --disable-programs --disable-doc --disable-network --disable-avdevice \
  --disable-avformat --disable-avfilter --disable-postproc --disable-everything \
  --enable-swresample --enable-swscale --enable-decoder="$decoders" \
  --enable-pthreads --disable-x86asm

# Fail before compiling if optional dependency selection changed the resulting licence.
grep -F 'CONFIG_GPL 0' config.h >/dev/null
grep -F 'CONFIG_NONFREE 0' config.h >/dev/null
grep -F 'CONFIG_VERSION3 0' config.h >/dev/null
if [[ "$host" == windows-x86_64 ]]; then
  # configure runs under MSYS, whereas the NDK's make resolves native Windows paths.
  # Normalize only generated build metadata, never the corresponding upstream source.
  msys_source="$(cygpath -u "$source")"
  sed -i "s|$msys_source|$source|g" Makefile ffbuild/config.mak
  make_shell="$(cygpath -ms "$(command -v sh)")"
  "$make_program" -j"$jobs" "SHELL=$make_shell"
  "$make_program" V=1 install "SHELL=$make_shell"
else
  "$make_program" -j"$jobs"
  "$make_program" install
fi
echo "Independent LGPL build complete: $abi"
