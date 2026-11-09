# MetCode هواکُد

**SYNOP & METAR Encoder**

[![Latest Release](https://img.shields.io/github/v/release/Saeid008/MetCode?display_name=tag)](https://github.com/Saeid008/MetCode/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Saeid008/MetCode/total)](https://github.com/Saeid008/MetCode/releases)

MetCode is a bilingual Android application developed to assist meteorological observers in preparing SYNOP (FM-12) and METAR (FM-15) reports. It combines report encoding, station information, and supporting meteorological calculations in a single interface.

## Download

**[Download the latest MetCode APK](https://github.com/Saeid008/MetCode/releases/latest/download/MetCode.apk)**

Browse [all releases](https://github.com/Saeid008/MetCode/releases) for version history and release notes.

MetCode is distributed directly as an APK through GitHub. Depending on the Android version and device settings, you may need to allow installation from the browser or file manager used to open the downloaded APK. Keep Google Play Protect enabled and follow any scan prompts displayed during installation.

## Features

- **SYNOP Encoding (FM-12):** Assemble supported report groups using observer-entered values and code selections, including temperature, pressure, wind, cloud, visibility, present and past weather, precipitation, snow, soil, sunshine, and wind extremes.
- **METAR Encoding (FM-15):** Prepare report elements for wind, visibility, cloud, weather, temperature, dew point, and QNH. Wind speeds entered in metres per second are converted to knots for METAR output.
- **Meteorological Calculations:** Barometer corrections; QFE, QFF, and QNH; 850 hPa pressure-level height; dew point; relative humidity; wet-bulb temperature; and pressure tendency.
- **Station Database:** 419 station records containing station names, WMO and ICAO identifiers, elevations, and coordinates.
- **Observation History:** Retain station-specific temperature and QFE observations by UTC hour to assist with subsequent report fields.
- **Bilingual Interface:** English and Persian, including right-to-left layout support for Persian.
- **Configurable Appearance:** Adjustable text sizes and optional custom fonts loaded from local asset files.

> MetCode is an observer's aid. Always review generated reports against the applicable WMO and national procedures before operational transmission.

## Build from Source

### Requirements

- Android Studio
- JDK 17 or newer
- Android SDK Platform 34
- Internet access for Gradle and Android dependencies

### Build a Debug APK

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

Release builds require the maintainer's local signing configuration. The signing keystore and its passwords are intentionally not stored in this repository.

**Never commit signing keys, passwords, or local signing properties.**

## In-App Updates

MetCode checks the latest public GitHub Release for this repository and looks for an available APK asset. When a newer version is found, the app downloads the APK, validates it, and opens the Android package installer for the user to approve.

The updater uses the following APK asset naming convention:

- **MetCode.apk:** Preferred filename for current and future releases.
- **app-release.apk:** Supported as a fallback for compatibility with older releases.

When both files are attached to a release, the updater prefers `MetCode.apk`.

The repository is configured in:

```text
app/src/main/assets/app_config.txt
```

Current setting:

```properties
update_repo = Saeid008/MetCode
```

### Publishing an Update

To publish a new version that can be detected by the in-app updater:

1. Create a non-draft, non-prerelease GitHub Release with a new tag, such as `v0.1.1`.
2. Attach the signed APK using the filename `MetCode.apk`.
3. Increase Android's `versionCode` for every update. The updater does not treat an equal or lower `versionCode` as a newer version.
4. Keep the application ID (`com.metcode`) unchanged.
5. Sign every release with the same signing key used for previously installed versions.

The public GitHub Releases API does not require a GitHub access token.

## Project Structure

```text
MetCode/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/metcode/  Kotlin application code
│           ├── assets/            Language, station, update and style files
│           └── res/               Android layouts and resources
├── gradle/                        Gradle Wrapper files
└── README.md                      Project documentation
```

## Feedback

Report bugs, suggest improvements, or request new features through [GitHub Issues](https://github.com/Saeid008/MetCode/issues).

## License

Copyright © 2026. All rights reserved.

This is a public repository for source-code inspection. It is **not** released under an open-source license. No permission is granted to use, modify, or redistribute the source code or bundled assets without prior written permission from the copyright holder.
