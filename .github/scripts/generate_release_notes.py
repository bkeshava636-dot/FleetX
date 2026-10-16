import os
import sys
import json
import subprocess
import datetime

def main():
    tag_name = os.environ.get("TAG_NAME", "v1.0.25")
    version_name = tag_name.lstrip("v")
    apk_name = os.environ.get("APK_NAME", f"FleetX-{tag_name}.apk")
    sha256 = os.environ.get("SHA256", "UNKNOWN_SHA256")
    version_code = os.environ.get("VERSION_CODE", "10025")
    try:
        file_size = int(os.environ.get("FILE_SIZE", "0"))
    except ValueError:
        file_size = 0

    # 1. Extract release notes from version.json
    release_notes = ""
    if os.path.exists("version.json"):
        try:
            with open("version.json", "r", encoding="utf-8") as f:
                data = json.load(f)
                release_notes = data.get("releaseNotes", "")
        except Exception as e:
            print(f"Warning: Could not read version.json: {e}", file=sys.stderr)

    if not release_notes or not release_notes.strip():
        release_notes = "FleetX continuous reliability, telemetry, and background tracking stability update."

    # 2. Extract git commits since previous tag
    prev_tag = ""
    try:
        prev_tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0", f"{tag_name}^"],
            text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        try:
            prev_tag = subprocess.check_output(
                ["git", "describe", "--tags", "--abbrev=0", "HEAD^"],
                text=True, stderr=subprocess.DEVNULL
            ).strip()
        except Exception:
            prev_tag = ""

    git_log = ""
    try:
        if prev_tag:
            git_log = subprocess.check_output(
                ["git", "log", f"{prev_tag}..HEAD", "--oneline", "--no-merges"],
                text=True
            ).strip()
        else:
            git_log = subprocess.check_output(
                ["git", "log", "-n", "10", "--oneline", "--no-merges"],
                text=True
            ).strip()
    except Exception as e:
        git_log = f"Changelog unavailable ({e})"

    if not git_log.strip():
        git_log = "Maintenance and bug fixes."

    now_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    now_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # 3. Format release-notes.md
    notes_content = f"""## FleetX Campus Transit Portal - Release {tag_name}

### What's New in this Release
{release_notes.strip()}

### Recent Commits ({tag_name})
```text
{git_log}
```

### Release Information
- **Version Name:** `{version_name}`
- **Version Code:** `{version_code}`
- **Release Date:** {now_utc}
- **APK Asset:** `{apk_name}`
- **SHA-256 Checksum:**
```
{sha256}
```

versionCode: {version_code}
SHA-256: {sha256}
"""
    with open("release-notes.md", "w", encoding="utf-8") as f:
        f.write(notes_content)
    print("Generated release-notes.md")

    # 4. Format update-manifest.json
    manifest = {
        "versionName": version_name,
        "versionCode": int(version_code),
        "sha256": sha256,
        "sizeBytes": file_size,
        "apkFileName": apk_name,
        "publishedAt": now_iso
    }
    with open("update-manifest.json", "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    print("Generated update-manifest.json")

if __name__ == "__main__":
    main()
