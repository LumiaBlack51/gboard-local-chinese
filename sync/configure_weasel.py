#!/usr/bin/env python3
"""Reduce Weasel painting work and optionally tune one app's preedit mode."""
import argparse
from pathlib import Path
import re
import shutil
import yaml


def configure(root, app=None, inline_preedit=None):
    if app is not None:
        if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_. -]*\.exe', app, re.IGNORECASE):
            raise ValueError('app must be an executable name, not a path')
        app = app.lower()
        if not isinstance(inline_preedit, bool):
            raise ValueError('app preedit mode must be a boolean')
    path = root / 'weasel.custom.yaml'
    settings = yaml.safe_load(path.read_text('utf-8-sig')) if path.exists() else {}
    settings = settings or {}
    if not isinstance(settings, dict):
        raise ValueError('weasel.custom.yaml must contain a mapping')
    patch = settings.setdefault('patch', {})
    if not isinstance(patch, dict):
        raise ValueError('weasel.custom.yaml patch must contain a mapping')
    desired = {'style/layout/shadow_radius': 0}
    if app:
        desired['app_options/' + app + '/inline_preedit'] = inline_preedit
    if all(key in patch and patch[key] == value for key, value in desired.items()):
        return False
    suffix = '.before-app-preedit' if app else '.before-low-latency'
    backup = path.with_name(path.name + suffix)
    if path.exists() and not backup.exists():
        shutil.copy2(path, backup)
    # Radius zero disables the blur work for all candidate-window shadows.
    # Keep global inline_preedit: some web editors rely on real composition text.
    # App-specific changes are opt-in so they can be compared in the affected UI.
    patch.update(desired)
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
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--external-preedit-for', metavar='APP.EXE')
    modes.add_argument('--inline-preedit-for', metavar='APP.EXE')
    args = parser.parse_args()
    app = args.external_preedit_for or args.inline_preedit_for
    inline = bool(args.inline_preedit_for) if app else None
    print('Weasel settings updated; redeploy to apply.' if configure(args.rime, app, inline)
          else 'Weasel settings are already applied.')
