#!/usr/bin/env python3
"""Consistent private SQLite backup for host migration; no service downtime required."""
import argparse
import os
from pathlib import Path
import sqlite3

def backup(source, destination):
    if destination.exists():
        raise FileExistsError('Backup destination already exists')
    os.umask(0o077)
    destination.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    with sqlite3.connect(source.resolve().as_uri()+'?mode=ro',uri=True) as reader,sqlite3.connect(destination) as writer:
        reader.backup(writer)
        if writer.execute('PRAGMA integrity_check').fetchone()[0]!='ok':
            raise RuntimeError('Backup integrity check failed')
    destination.chmod(0o600)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--database',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args();backup(a.database,a.output)
