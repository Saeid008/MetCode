# MetCode

**SYNOP & METAR Encoder · هواکُد**

[![Latest release](https://img.shields.io/github/v/release/Saeid008/MetCode?display_name=tag)](https://github.com/Saeid008/MetCode/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Saeid008/MetCode/total)](https://github.com/Saeid008/MetCode/releases)

MetCode is an Android application for meteorological observers. It brings SYNOP (FM-12) and METAR (FM-15) report encoding, station information, and supporting meteorological calculations into one bilingual interface.

## Download

**[Download the latest APK](https://github.com/Saeid008/MetCode/releases/latest/download/app-release.apk)**

Browse [all releases](https://github.com/Saeid008/MetCode/releases) for version history and release notes.

## Features

- **SYNOP encoding (FM-12):** assemble supported report groups from observer-entered values and code selections, including temperature, pressure, wind, cloud, visibility, weather, precipitation, snow, soil, sunshine, and wind extremes.
- **METAR encoding (FM-15):** prepare report elements for wind, visibility, cloud, weather, temperature/dew point, and QNH. Wind speed entered in metres per second is converted to knots for METAR output.
- **Meteorological calculations:** barometer corrections; QFE, QFF, and QNH; 850 hPa pressure-level height; dew point; relative humidity; wet-bulb temperature; and pressure tendency.
- **Station database:** 419 station records with names, WMO and ICAO identifiers, elevation, and coordinates.
- **Observation history:** retain station-specific temperature and QFE observations by UTC hour to assist with later report fields.
- **Bilingual interface:** English and Persian, with right-to-left layout for Persian.
- **Configurable appearance:** text sizes and optional custom fonts are read from local asset files.

> MetCode is an observer's aid. Always review generated reports against the applicable WMO and national procedures before operational transmission.

## Build from source

### Requirements

- Android Studio
- JDK 17 or newer
- Android SDK Platform 34
- Internet access for Gradle and Android dependencies

### Build a debug APK

Clone the repository:

```bash
git clone https://github.com/Saeid008/MetCode.git
cd MetCode
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
bash gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release builds require the maintainer's local signing configuration. The signing keystore and its passwords are intentionally not stored in this repository. **Never commit signing keys, passwords, or local signing properties.**

## In-app updates

The app checks the latest public GitHub Release for this repository and looks for an APK asset. When a newer version is available, it downloads the APK and opens the Android package installer for the user to approve.

The repository is configured in:

```text
app/src/main/assets/app_config.txt
```

Current setting:

```properties
update_repo = Saeid008/MetCode
```

For an update to install successfully:

1. Publish a non-draft, non-prerelease GitHub Release with a tag such as `v0.1.1`.
2. Attach the signed APK as a release asset (the current updater selects an asset whose name ends in `.apk`).
3. Increase Android's `versionCode` for every update.
4. Keep the application ID (`com.metcode`) and the same signing key used for the installed app.

The public releases API does not require a GitHub access token.

## Project layout

- `app/src/main/java/com/metcode/` — Kotlin application code
- `app/src/main/assets/` — language tables, station/update settings, style configuration, and splash-screen images
- `app/src/main/res/` — Android layouts and resources
- `gradle/` — Gradle Wrapper files
- `README.md` — project documentation

## Feedback

Report bugs and suggest improvements through [GitHub Issues](https://github.com/Saeid008/MetCode/issues).

## License

Copyright © 2026 Saeid. All rights reserved.

This is a public repository for source-code inspection. It is **not** released under an open-source license. No permission is granted to use, modify, or redistribute the source code or bundled assets without prior written permission from the copyright holder.
