#!/usr/bin/env python3
"""Validate the single public app catalog without loading secrets."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent


def main():
    source = (ROOT / 'catalog/apps.json').read_bytes()
    catalog = json.loads(source)
    if catalog['schema'] != 1 or not 1 <= len(catalog['apps']) <= 100:
        raise ValueError('Unsupported catalog schema or size')
    repos, packages = set(), set()
    for app in catalog['apps']:
        repo, package = app['repo'], app['packageName']
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', repo) or repo in repos:
            raise ValueError('Invalid or duplicate repository')
        if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+', package) or package in packages:
            raise ValueError('Invalid or duplicate package')
        repos.add(repo)
        packages.add(package)
        if not app['certificates'] or any(not re.fullmatch(r'[a-f0-9]{64}', cert) for cert in app['certificates']):
            raise ValueError('Invalid certificate fingerprint')
        for version in app.get('knownVersions', {}).values():
            if not isinstance(version['code'], int) or version['code'] <= 0 or not version['name']:
                raise ValueError('Invalid version metadata')
    print(f'Catalog verified: {len(repos)} applications')


if __name__ == '__main__':
    main()
