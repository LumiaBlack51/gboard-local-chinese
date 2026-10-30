#!/usr/bin/env python3
"""Reduce Weasel candidate painting work without changing composition behavior."""
import argparse
from pathlib import Path
import shutil
import yaml


def configure(root):
    path = root / 'weasel.custom.yaml'
    settings = yaml.safe_load(path.read_text('utf-8-sig')) if path.exists() else {}
    settings = settings or {}
    if not isinstance(settings, dict):
        raise ValueError('weasel.custom.yaml must contain a mapping')
    patch = settings.setdefault('patch', {})
    if not isinstance(patch, dict):
        raise ValueError('weasel.custom.yaml patch must contain a mapping')
    key = 'style/layout/shadow_radius'
    if patch.get(key) == 0:
        return False
    backup = path.with_name(path.name + '.before-low-latency')
    if path.exists() and not backup.exists():
        shutil.copy2(path, backup)
    # Radius zero disables the blur work for all candidate-window shadows.
    # Keep inline_preedit: some web editors rely on real composition text.
    patch[key] = 0
    root.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.low-latency.tmp')
    try:
        temporary.write_text(yaml.safe_dump(settings, allow_unicode=True, sort_keys=False), encoding='utf-8')
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
    return True


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--rime', required=True, type=Path)
    args = parser.parse_args()
    print('Weasel shadow disabled; redeploy to apply.' if configure(args.rime)
          else 'Weasel shadow is already disabled.')
