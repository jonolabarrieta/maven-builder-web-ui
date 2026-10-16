#!/usr/bin/env python3
"""Restore frontend dependencies from the pinned, integrity-checked npm archives."""

import base64
import hashlib
import io
import json
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    packages = json.loads((ROOT / 'frontend/vendor-lock.json').read_text(encoding='utf-8'))
    for package in packages:
        with urllib.request.urlopen(package['tarball'], timeout=60) as response:
            content = response.read()
        digest = 'sha512-' + base64.b64encode(hashlib.sha512(content).digest()).decode('ascii')
        if digest != package['integrity']:
            raise SystemExit(f"Integrity check failed for {package['name']}@{package['version']}")
        destination = ROOT / 'src/main/resources/static/vendor' / package['name'] / package['version']
        destination.mkdir(parents=True, exist_ok=True)
        with tarfile.open(fileobj=io.BytesIO(content), mode='r:gz') as archive:
            # Read only the two named regular files; never extract archive paths.
            for source, target in package['files'].items():
                member = archive.getmember(source)
                if not member.isfile():
                    raise SystemExit(f'Expected a regular file: {source}')
                with archive.extractfile(member) as resource:
                    (destination / target).write_bytes(resource.read())
        print(f"Restored {package['name']}@{package['version']}")


if __name__ == '__main__':
    main()
