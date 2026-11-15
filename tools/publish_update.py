#!/usr/bin/env python3
"""Publie les APK signés pour les mises à jour dans l'app (dossier updates/ du dépôt).

Utilisation, après `./gradlew assembleRelease` :
    python3 tools/publish_update.py "Nouveautés du launcher" ["Nouveautés du module"]

Copie app-release.apk et notifications-release.apk dans updates/, puis écrit
updates/latest.json (versions, adresses, empreintes SHA-256) que l'app lit sur GitHub.
L'adresse de base doit correspondre à UpdateRepository.MANIFEST_URL.
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE_URL = "https://raw.githubusercontent.com/baptistesadox-dotcom/App-t-l-phone/ccr-9ef49c26-q77is6/updates"
APPS = {
    "launcher": (ROOT / "app/build/outputs/apk/release", "Epure.apk"),
    "module": (ROOT / "notifications/build/outputs/apk/release", "Epure-Notifications.apk"),
}


def entry(build_dir: Path, name: str, notes: str) -> dict:
    metadata = json.loads((build_dir / "output-metadata.json").read_text())
    element = metadata["elements"][0]
    source = build_dir / element["outputFile"]
    target = ROOT / "updates" / name
    target.parent.mkdir(exist_ok=True)
    shutil.copyfile(source, target)
    data = target.read_bytes()
    return {
        "versionCode": element["versionCode"],
        "versionName": element["versionName"],
        "url": f"{BASE_URL}/{name}",
        "sha256": hashlib.sha256(data).hexdigest(),
        "size": len(data),
        "notes": notes,
    }


def main() -> None:
    notes = sys.argv[1:] + ["", ""]
    manifest_path = ROOT / "updates/latest.json"
    previous = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    manifest = {}
    for (key, (build_dir, name)), note in zip(APPS.items(), notes):
        if not (build_dir / "output-metadata.json").exists():
            sys.exit(f"APK introuvable dans {build_dir} : lancer ./gradlew assembleRelease")
        new = entry(build_dir, name, note)
        old = previous.get(key)
        # Même version qu'avant : on garde les notes déjà publiées.
        if old and old.get("versionCode") == new["versionCode"] and not note:
            new["notes"] = old.get("notes", "")
        manifest[key] = new
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    for key, value in manifest.items():
        print(f"{key}: {value['versionName']} ({value['versionCode']}), {value['size'] // 1024} Ko, {value['sha256'][:12]}…")


if __name__ == "__main__":
    main()
