# MetCode

**SYNOP & METAR Encoder**

MetCode is an Android application designed to assist meteorological observers with station data calculations and the preparation of standardized SYNOP and METAR reports.

The application combines meteorological calculations, observation input, station information, and report encoding in a single interface.

## Features

### Meteorological Calculations
- QFE, QFF, and QNH pressure calculations.
- 850 hPa pressure-level height calculation.
- Dew point and relative humidity calculations.
- Pressure tendency calculations.

### SYNOP and METAR
- SYNOP report generation.
- METAR report generation.
- Meteorological observation input and code selection.
- Station identification and station-code management.

### Application Features
- English and Persian interface.
- Observation history.
- Station database.
- In-app update checking through GitHub Releases.

The generated reports should always be reviewed by the observer before operational transmission.

## Project Information

| Item | Value |
|---|---|
| Application name | MetCode |
| Persian name | هواکُد |
| Package name | `com.metcode` |
| Platform | Android |
| Minimum Android version | Android 5.0 (API 21) |
| Programming language | Kotlin |
| Build system | Gradle |
| Current development version | 0.1.0 |

## Project Structure

```text
MetCode/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── assets/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── metcode/
│   │       ├── res/
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
├── .gitignore
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle
└── README.md
```

## Requirements

To build MetCode, you need:

- Android Studio.
- Android SDK.
- A compatible JDK.
- An internet connection for downloading Gradle dependencies.

## Building the Application

Clone the repository:

```bash
git clone https://github.com/Saeid008/MetCode.git
```

Enter the project directory:

```bash
cd MetCode
```

Build the debug APK on Windows:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK will be available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Releases and Updates

Official application releases are published through GitHub Releases.

Each release should include a signed APK and release notes describing the changes.

MetCode includes an in-app update mechanism that checks the latest GitHub Release, downloads its APK asset, and opens the Android package installer.

The update repository is configured in:

```text
app/src/main/assets/app_config.txt
```

Set the repository value to:

```text
update_repo=Saeid008/MetCode
```

No GitHub access token is required for checking releases of a public repository.

## Versioning

MetCode uses semantic versioning:

```text
MAJOR.MINOR.PATCH
```

Examples:

- `0.1.0` — Initial development release.
- `0.1.1` — Bug fixes.
- `0.2.0` — New features.
- `1.0.0` — First stable release.

The Android `versionCode` must increase with every published update.

## Issues and Contributions

Bug reports, feature suggestions, and technical discussions are welcome through GitHub Issues.

Pull requests may be submitted for review. All contributions are subject to review and acceptance by the project maintainer.

Before third-party code contributions are merged, a separate contribution agreement may be required.

## Copyright and Repository Use

Copyright (c) 2026 Saeid. All rights reserved.

This repository is publicly available for source-code inspection and discussion. It is not distributed under an open-source software license.

No license is granted to reuse, copy, modify, redistribute, or incorporate this source code into other projects without prior written permission from the copyright holder.

GitHub's platform permissions for viewing and forking public repositories remain applicable.

**Project:** MetCode
**Maintainer:** Saeid (@Saeid008)