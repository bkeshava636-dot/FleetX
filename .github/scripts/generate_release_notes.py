import os
import sys
import json
import datetime

def main():
    tag_name = os.environ.get("TAG_NAME", "v1.0.45")
    version_name = tag_name.lstrip("v")
    apk_name = os.environ.get("APK_NAME", f"FleetX-{tag_name}.apk")
    sha256 = os.environ.get("SHA256", "UNKNOWN_SHA256")
    version_code = os.environ.get("VERSION_CODE", "10045")
    try:
        file_size = int(os.environ.get("FILE_SIZE", "0"))
    except ValueError:
        file_size = 0

    # 1. Extract clean user-facing release notes from version.json
    release_notes = ""
    if os.path.exists("version.json"):
        try:
            with open("version.json", "r", encoding="utf-8") as f:
                data = json.load(f)
                file_version = data.get("versionName", "")
                if file_version == version_name or not release_notes:
                    release_notes = data.get("releaseNotes", "")
        except Exception as e:
            print(f"Warning: Could not read version.json: {e}", file=sys.stderr)

    if not release_notes or not release_notes.strip():
        release_notes = (
            "• Performance improvements and stability enhancements.\n"
            "• Optimized live bus tracking and faster startup responsiveness."
        )

    now_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    now_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # 2. Format release-notes.md (Concise, understandable, no commit hashes or dev jargon)
    notes_content = f"""### What's New in FleetX {tag_name}
{release_notes.strip()}

### Verification
- **Version:** `{version_name}` ({version_code})
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
    print("Generated clean release-notes.md")

    # 3. Format update-manifest.json
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

    # 4. Format release-version.json for FleetX/main/version.json
    releases_repo = os.environ.get("RELEASES_REPO", "bkeshava636-dot/FleetX")
    version_payload = {
        "versionCode": int(version_code),
        "versionName": version_name,
        "downloadUrl": f"https://github.com/{releases_repo}/releases/download/{tag_name}/{apk_name}",
        "sha256": sha256,
        "releaseNotes": release_notes.strip(),
        "publishedAt": now_iso
    }
    with open("release-version.json", "w", encoding="utf-8") as f:
        json.dump(version_payload, f, indent=2)
    print("Generated release-version.json")

if __name__ == "__main__":
    main()
