# OneUI Mods Manager

[日本語](README.md) · English

An Android app for browsing OneUI-Mods apps and updating them from GitHub Releases.

## Features

- Search and filter installed and uninstalled apps.
- Check up to four apps in parallel and compare installed versions with public GitHub releases.
- Switch between stable releases and prereleases with **Include prereleases**.
- Read release notes, download APKs, and update through Android's installation confirmation screen.
- Prepare multiple updates and install them one at a time.
- Verify package names, signing certificates, SHA-256 hashes, and versions.
- Japanese and English UI, light and dark themes, and an in-app license list.
- Fetch additions and changes from the public catalog without updating the manager itself.

## Requirements

Android 16 or 17. The manager itself does not require root or Vector. Each module has its own supported OS versions and installation requirements; check its README.

## Installation and updates

Install the signed `*-release.apk` from [Releases](https://github.com/OneUI-Mods/OneUIModsManager/releases). The first time you install an update through this app, Android requires **Allow from this source**.

Choose an app under **Apps**, read its release notes, and download it. Under **Updates**, you can prepare all available updates together. Android asks you to confirm each installation. After updating a module, follow that app's instructions for reloading its target processes. The manager never automatically restarts the device.

You can also install and update APKs with the same signing identity through the [OneUI Mods F-Droid repository](https://oneui-mods.github.io/fdroid/). This is a separate distribution source from submissions to the official F-Droid repository.

Prereleases are included by default. Turn the setting off to show only stable candidates. An installed prerelease is never automatically downgraded to an older stable release.

## App catalog updates

Catalog entries contain the app name, package name, repository, and public signing certificates. Versions, release notes, publication dates, prerelease status, and APKs come directly from each repository's GitHub Releases. The catalog does not need editing for every release.

The manager fetches [`catalog/apps.json`](catalog/apps.json) from this repository. You do not need to reinstall the manager when an app is added or its package name changes. **Check for updates** fetches the latest catalog and release information, as does the automatic startup check.

When offline, the manager uses the last downloaded catalog. Before the first successful fetch, it uses the bundled catalog. Catalog registration is documented in the [developer guide (Japanese)](docs/development.md#カタログ).

## Network access and privacy

The app downloads only the public GitHub catalog, release information, and APKs. GitHub login is not required. Installed-app detection happens on the device; app lists and settings are not uploaded. There are no analytics or ads.

The automatic startup check runs only when at least six hours have passed since the previous completed check. You can disable it in settings. It is not a periodic background polling service. When offline, cached information is shown. When GitHub's rate limit is reached, the app shows when another attempt is possible.

## Limitations

- Only apps registered in the OneUI-Mods catalog are supported.
- Releases are skipped when the downloadable, verifiable APK cannot be uniquely identified.
- Installation cannot proceed if an APK's SHA-256 cannot be verified or its signing certificate does not match.
- An interrupted download starts again from the beginning.
- When build metadata accompanies an APK, updates with the same display version can also be compared. Older releases without metadata are compared by tag; unusual version formats may leave the result undecided.
- Some devices and installers may require additional confirmation steps in this early version.

## Development and license

This README covers usage. Implementation and verification details are in the [developer guide (Japanese)](docs/development.md), and distribution steps are in the [release guide (Japanese)](docs/releasing.md).

The app is licensed under the MIT License. Dependency licenses are available under **Settings → Licenses**.

## String compatibility

Handling of certain whitespace and empty strings in search, release information, and version comparison has been aligned with the previous behavior.

[Build environment / ビルド環境](docs/build-environment.md)
