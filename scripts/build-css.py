#!/usr/bin/env python3
"""Build the committed stylesheet with the official, pinned standalone CLI."""

import argparse
import os
import platform
import subprocess
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = '3.4.17'
OUTPUT = ROOT / 'src/main/resources/static/css/app.css'
SOURCES = ['colors.css', 'responsive.css']


def compiler() -> Path:
    """Use a supplied executable, or cache the official release for this host."""
    if os.environ.get('TAILWINDCSS_BIN'):
        return Path(os.environ['TAILWINDCSS_BIN']).expanduser().resolve()
    systems = {'Linux': 'linux', 'Darwin': 'macos', 'Windows': 'windows'}
    arches = {'x86_64': 'x64', 'AMD64': 'x64', 'aarch64': 'arm64', 'arm64': 'arm64'}
    system = systems.get(platform.system())
    arch = arches.get(platform.machine())
    if not system or not arch or (system == 'windows' and arch != 'x64'):
        raise SystemExit('Set TAILWINDCSS_BIN to a Tailwind CSS 3.4.17 standalone executable for this host.')
    asset = f'tailwindcss-{system}-{arch}' + ('.exe' if system == 'windows' else '')
    executable = ROOT / 'target/frontend-tools' / VERSION / asset
    if not executable.exists():
        executable.parent.mkdir(parents=True, exist_ok=True)
        url = f'https://github.com/tailwindlabs/tailwindcss/releases/download/v{VERSION}/{asset}'
        print(f'Downloading official Tailwind CSS {VERSION}: {asset}', flush=True)
        with urllib.request.urlopen(url, timeout=60) as response:
            content = response.read()
        temporary = executable.with_suffix('.download')
        temporary.write_bytes(content)
        temporary.chmod(0o755)
        temporary.replace(executable)
    return executable


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='fail if the committed stylesheet is stale')
    args = parser.parse_args()
    cli = compiler()
    css = '@tailwind base;\n@tailwind components;\n@tailwind utilities;\n'
    css += '\n'.join((OUTPUT.parent / source).read_text(encoding='utf-8') for source in SOURCES)
    with tempfile.TemporaryDirectory(prefix='mvnbuilder-css-') as directory:
        source = Path(directory) / 'input.css'
        built = Path(directory) / 'app.css'
        source.write_text(css, encoding='utf-8')
        subprocess.run([str(cli), '-c', str(ROOT / 'tailwind.config.cjs'),
                        '-i', str(source), '-o', str(built), '--minify'], cwd=ROOT, check=True)
        content = built.read_bytes()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != content:
            raise SystemExit('app.css is stale. Run: python3 scripts/build-css.py')
        print(f'app.css is current ({len(content):,} bytes).')
    else:
        OUTPUT.write_bytes(content)
        print(f'Built {OUTPUT.relative_to(ROOT)} ({len(content):,} bytes).')


if __name__ == '__main__':
    main()
