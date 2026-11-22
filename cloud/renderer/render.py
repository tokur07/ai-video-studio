import argparse
import subprocess
import tempfile
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="Render slideshow video with FFmpeg")
    parser.add_argument("--images", nargs="+", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--audio")
    parser.add_argument("--seconds-per-image", type=float, default=5.0)
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    args = parser.parse_args()

    images = [Path(item).resolve() for item in args.images]
    for image in images:
        if not image.is_file():
            raise FileNotFoundError(f"Image not found: {image}")

    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as temp_dir:
        concat_file = Path(temp_dir) / "images.txt"
        with concat_file.open("w", encoding="utf-8") as stream:
            for image in images:
                safe_path = str(image).replace("'", "'\\''")
                stream.write(f"file '{safe_path}'\n")
                stream.write(f"duration {args.seconds_per_image}\n")
            safe_path = str(images[-1]).replace("'", "'\\''")
            stream.write(f"file '{safe_path}'\n")

        video_filter = (
            f"scale={args.width}:{args.height}:force_original_aspect_ratio=decrease,"
            f"pad={args.width}:{args.height}:(ow-iw)/2:(oh-ih)/2,"
            "format=yuv420p"
        )
        command = [
            "ffmpeg", "-y", "-f", "concat", "-safe", "0",
            "-i", str(concat_file)
        ]
        if args.audio:
            audio = Path(args.audio).resolve()
            if not audio.is_file():
                raise FileNotFoundError(f"Audio not found: {audio}")
            command.extend(["-i", str(audio), "-shortest"])

        command.extend([
            "-vf", video_filter, "-c:v", "libx264",
            "-preset", "veryfast", "-crf", "23",
            "-movflags", "+faststart", str(output)
        ])
        subprocess.run(command, check=True)
    print(f"Created: {output}")

if __name__ == "__main__":
    main()
