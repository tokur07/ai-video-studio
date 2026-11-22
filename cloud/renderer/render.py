import argparse
import json
import os
import subprocess
import sys


def parse_args():
    parser = argparse.ArgumentParser(description="AI Video Studio FFmpeg Renderer")
    parser.add_argument("--manifest", required=False, default="", help="Path ke file JSON manifest proyek")
    parser.add_argument("--output", required=False, default="output.mp4", help="Path file MP4 keluaran")
    parser.add_argument("--resolution", required=False, default="1080p", choices=["720p", "1080p"])
    parser.add_argument("--aspect-ratio", required=False, default="9:16", choices=["9:16", "16:9", "1:1"])
    parser.add_argument("--burn-subtitles", action="store_true", help="Aktifkan burn-in subtitle")
    parser.add_argument("--mix-audio", action="store_true", help="Aktifkan mixing audio")
    parser.add_argument("--dry-run", action="store_true", help="Validasi argumen dan manifest tanpa mengeksekusi FFmpeg")
    return parser.parse_args()


def target_dimensions(resolution: str, aspect_ratio: str):
    short_side = 720 if resolution == "720p" else 1080
    long_side = round(short_side * 16 / 9)
    if long_side % 2 != 0:
        long_side += 1

    if aspect_ratio == "16:9":
        return long_side, short_side
    if aspect_ratio == "1:1":
        return short_side, short_side
    # Default 9:16 portrait
    return short_side, long_side


def main():
    args = parse_args()
    width, height = target_dimensions(args.resolution, args.aspect_ratio)

    manifest_data = {}
    if args.manifest and os.path.exists(args.manifest):
        with open(args.manifest, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)

    plan = {
        "status": "ready",
        "output": args.output,
        "width": width,
        "height": height,
        "aspect_ratio": args.aspect_ratio,
        "burn_subtitles": args.burn_subtitles,
        "mix_audio": args.mix_audio,
        "scenes_count": len(manifest_data.get("scenes", [])) if isinstance(manifest_data, dict) else 0,
    }
    print(json.dumps(plan))

    if args.dry_run:
        return 0

    cmd = [
        "ffmpeg",
        "-y",
        "-f", "lavfi",
        "-i", f"color=c=0x171329:s={width}x{height}:d=5",
        "-f", "lavfi",
        "-i", "anullsrc=r=44100:cl=stereo",
        "-shortest",
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        args.output,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr or "FFmpeg execution failed\n")
        return proc.returncode
    return 0


if __name__ == "__main__":
    sys.exit(main())
