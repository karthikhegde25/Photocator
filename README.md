<p align="center">
  <img src="docs/logo.png" alt="Photocator logo" width="128">
</p>

<h1 align="center">Photocator</h1>

<p align="center"><b>Every photo, stamped with where it happened.</b></p>

<p align="center">
  <a href="https://github.com/karthikhegde25/Photocator/releases/tag/1.0"><img src="docs/get-it-on-github.png" alt="Get it on GitHub" height="64"></a>
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL v3" src="https://img.shields.io/badge/License-GPLv3-blue.svg"></a>
  <img alt="Platform: Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84.svg">
  <img alt="Version 1.0" src="https://img.shields.io/badge/version-1.0-informational.svg">
</p>

## About

Photocator is a free, open-source Android camera that adds the place, the map, the weather and the time to every photo and video you take. Each shot carries a geotag with GPS coordinates, place name, full address, a map thumbnail, Plus Code, altitude, speed, temperature, humidity, wind and compass heading. It is useful for field work, site inspections, travel logs, deliveries, or any time you want proof of where a picture was taken.

## Features
- Live geotag over the viewfinder, with the same layout saved onto the photo
- Photo, Night and Video modes (video has the geotag burned into every frame)
- Add a geotag to photos already in your gallery, with a map picker and place search
- Review and edit before saving: address, note, position, or a strip below the photo
- 11 map styles, adjustable geotag size and position, your own watermark logos
- Zoom dial, flash, timer, grid, aspect ratio, quality, volume-button shutter or zoom
- Manual refresh of your location
- No accounts, no ads, no analytics. Your photos stay on your phone

## Download
[Download the APK directly](https://github.com/karthikhegde25/Photocator/releases/download/1.0/Photocator.apk) or see the [release page](https://github.com/karthikhegde25/Photocator/releases/tag/1.0). Requires Android 8.0 (API 26) or later.

## Build
- JDK 17, Android SDK 35. Run `./gradlew assembleDebug`, or use the GitHub Actions workflow in `.github/workflows/build.yml`.
- For signed releases add the repository secrets used by the workflow (`KEYSTORE_BASE64` and the related password/alias secrets).

## Data sources
- Map imagery: Esri and its data providers
- Addresses and place search: © OpenStreetMap contributors (Nominatim, Photon)
- Weather: Open-Meteo

## License
Copyright © 2026 Karthik Hegde.

Photocator is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
