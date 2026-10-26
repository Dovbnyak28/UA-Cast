#!/usr/bin/env python3
"""Create original synthetic media/packet fixtures. The CLI encoder is a test tool, never shipped."""
import argparse
import base64
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def unhex_dump(value):
    output = bytearray()
    for line in value.splitlines():
        if ": " in line:
            hexadecimal = line.split(": ", 1)[1].split("  ", 1)[0].replace(" ", "")
            output.extend(bytes.fromhex(hexadecimal))
    return bytes(output)


def generate(ffmpeg):
    probe = ffmpeg.with_name("ffprobe.exe" if ffmpeg.suffix == ".exe" else "ffprobe")
    directory = ROOT / "ffmpegdecoder/src/androidTest/assets/fixtures"
    directory.mkdir(parents=True, exist_ok=True)
    video = [
        ("h264-bframes", "video/avc", "libx264", "mp4", ["-bf", "2", "-g", "10"]),
        ("mpeg2-bframes", "video/mpeg2", "mpeg2video", "ts", ["-bf", "2", "-g", "10"]),
        ("vp8", "video/x-vnd.on2.vp8", "libvpx", "webm", ["-deadline", "realtime"]),
        ("vp9", "video/x-vnd.on2.vp9", "libvpx-vp9", "webm", ["-deadline", "realtime"]),
    ]
    audio = [
        ("mp2", "audio/mpeg-L2", "mp2", "mp2", []),
        ("ac3", "audio/ac3", "ac3", "ac3", []),
        ("eac3", "audio/eac3", "eac3", "eac3", []),
        ("dts", "audio/vnd.dts", "dca", "dts", ["-strict", "-2"]),
    ]
    for name, mime, codec, suffix, options in video + audio:
        target = directory / f"{name}.{suffix}"
        is_video = mime.startswith("video/")
        source = "testsrc2=size=128x96:rate=10" if is_video else "sine=frequency=997:sample_rate=48000"
        command = [str(ffmpeg), "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", source]
        command += (["-frames:v", "30", "-an", "-c:v", codec, "-pix_fmt", "yuv420p"] if is_video
                    else ["-t", "1", "-ac", "2", "-c:a", codec, "-b:a", "384k"])
        subprocess.run(command + options + [str(target)], check=True, timeout=60)
        result = subprocess.run([str(probe), "-v", "error", "-show_streams", "-show_packets",
                                 "-show_data", "-of", "json", str(target)],
                                check=True, capture_output=True, timeout=30)
        parsed = json.loads(result.stdout)
        stream = parsed["streams"][0]
        frames = json.loads(subprocess.run([str(probe), "-v", "error", "-show_frames", "-of", "json", str(target)],
                            check=True, capture_output=True, timeout=30).stdout)["frames"]
        extra = unhex_dump(stream.get("extradata", ""))
        packets = []
        for index, packet in enumerate(parsed["packets"]):
            data = unhex_dump(packet["data"])
            if len(data) != int(packet["size"]):
                raise ValueError("Packet hexdump does not match its declared length")
            packets.append({"data": base64.b64encode(data).decode("ascii"),
                            "timeUs": round(float(packet.get("pts_time", index / 10)) * 1_000_000)})
        fixture = {"name": name, "mime": mime, "codec": codec, "synthetic": True,
                   "width": stream.get("width", -1), "height": stream.get("height", -1),
                   "sampleRate": int(stream.get("sample_rate", 0)), "channels": stream.get("channels", 0),
                   "extraData": base64.b64encode(extra).decode("ascii"), "packets": packets,
                   "expectedFrames": len(frames),
                   "expectedSamples": sum(int(frame.get("nb_samples", 0)) for frame in frames)}
        (directory / f"{name}.json").write_text(json.dumps(fixture, separators=(",", ":")) + "\n", encoding="utf-8")
        print(f"{name}: {len(packets)} packets, {len(frames)} reference frames")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", type=Path, required=True)
    generate(parser.parse_args().ffmpeg)
