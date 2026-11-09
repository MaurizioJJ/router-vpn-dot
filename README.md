# Router VPN Dot

A small Android app that shows, with a dim dot in a corner of the screen, whether
your device's public IPv4 address belongs to a VPN.

It is meant for setups where the VPN runs on the **router**, not on the device. In
that case Android's own VPN icon never appears, so you can't tell from the device
whether traffic is going through the tunnel. Router VPN Dot checks the public IP the
outside world sees and asks [proxycheck.io](https://proxycheck.io) whether it is a VPN
address.

| Dot | Meaning |
|-----|---------|
| Green | The current public IP is classified as a VPN address. |
| Red | The current public IP is classified as **not** a VPN address. |
| Amber | Unknown: the check failed, is still running, or the result is stale. |

> Green is a public-database classification, not proof that your tunnel is up. It
> does not check per-app routing, DNS or IPv6.

## Requirements

- Android 8.0 (API 26) or newer. Built and tested against Android 11 (API 30).
- Permission to **display over other apps** (the dot is an overlay).
- Internet access.

## Install

Download `RouterVPNDot-<version>.apk` from the
[latest release](https://github.com/MaurizioJJ/router-vpn-dot/releases/latest), or
build it yourself (see [Build](#build)). Then:

1. Copy the APK to the device and install it (allow installs from unknown sources
   when Android asks).
2. Open **Router VPN Dot**.
3. Select **Allow display over other apps** and enable it for Router VPN Dot.
4. Go back to the app and select **Start monitoring**.
5. Optional: turn on **Resume monitoring after reboot**.

The dot appears in the top-right corner. **Stop monitoring** stops it and keeps it
stopped, even across reboots, until you start it again.

## Settings

| Setting | What it does |
|---------|--------------|
| Corner | Top right, top left, bottom right or bottom left. |
| Dot size | 4 to 20 dp. |
| Green brightness / Red-amber brightness | 5 % to 100 %. |
| Pulse | Slow pulse on green, faster on red. |
| Warning banner | Shows **VPN NOT DETECTED** or **VPN STATUS UNKNOWN** across the top of the screen when the dot isn't green. |
| Resume monitoring after reboot | Starts monitoring automatically after a reboot or app update. |
| Show diagnostics | Lists lookup errors, service events and Android's process-exit records. |

The yellow outline around the **Start/Stop monitoring** button shows how much of
today's proxycheck.io allowance has been used: no outline at the start of the day,
a full outline when the allowance is used up.

## How it checks

- Every 5 seconds the app gets the public IPv4 from `api.ipify.org`, falling back to
  `checkip.amazonaws.com`. Each check has a 10-second deadline.
- A result older than 9 seconds counts as stale, so the dot turns amber.
- Each **new** IP is sent once to `https://proxycheck.io/v3/{IP}`. While the IP stays
  the same, the saved result is reused, so normal use costs very few lookups.
- The last result survives restarts, but it is only trusted again after a fresh
  check returns the same IP.
- Failed lookups retry after 15 s, 30 s, 60 s, 5 min, then every 30 min.
- A network change triggers an immediate re-check.

## Build

If you build it yourself, Android treats your APK as a different signer from the
release APK: uninstall one before installing the other.

You need:

- JDK 17
- Android SDK with `platforms;android-30` and `build-tools;34.0.0`
- `bash` and `python3`

Install the SDK parts with `sdkmanager`:

```sh
sdkmanager "platforms;android-30" "build-tools;34.0.0"
```

Then build from the repository root:

```sh
ANDROID_SDK_ROOT=/path/to/android-sdk bash build.sh
```

On macOS with Homebrew (`brew install openjdk@17 android-commandlinetools`), for
example:

```sh
export PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools bash build.sh
```

`build.sh` runs the unit tests, then compiles, signs and verifies
`RouterVPNDot-<version>.apk` in the repository root.

### Signing key

On the first build, `build.sh` creates `local-test.jks`, a local signing key
(password `android`). It is git-ignored. **Keep a backup of it**: Android only
accepts an update signed with the same key, so if you lose it you have to uninstall
the app (losing its settings) before installing a new build.

### Optional proxycheck.io API key

Without a key, proxycheck.io allows a limited number of free lookups per day, and
the app counts its own lookups against 100 a day. With a free account you get a
higher daily allowance, and the yellow outline shows your real usage from
proxycheck.io.

1. Create an account at [proxycheck.io](https://proxycheck.io) and copy your API key.
2. Save it in a file named `proxycheck.key` in the repository root:
   ```sh
   printf '%s' 'YOUR-API-KEY' > proxycheck.key
   ```
3. Run `build.sh` again. It prints `proxycheck.io key: included`.

`proxycheck.key` is git-ignored. The key is compiled into the APK, so **don't share
an APK built with your key**.

## Privacy

- The app contacts `api.ipify.org`, `checkip.amazonaws.com` and `proxycheck.io`.
  These services see your public IP address.
- Settings and the last result are stored only on the device.
- No accounts, analytics, ads, storage access or accessibility service.

## Known limits

- The classification comes from a public database and can be wrong or outdated.
- Android may stop background services; an alarm restarts monitoring within about a
  minute, but Android can delay it.
- Some full-screen or protected video can hide overlays. Check that the dot is
  visible in the apps you use.
- Frequent IP changes use more lookups. Staying under the daily limit is not
  guaranteed.

## Source layout

| File | Role |
|------|------|
| `MonitorService.java` | Polling, classification, dot and banner overlays |
| `DetectionState.java` | Classification cache and retry backoff |
| `StartupBanner.java` | Banner shown while the first check runs |
| `MainActivity.java` | Settings and status screen |
| `BootReceiver.java`, `AutoStart.java` | Start after boot or update, recovery alarm |
| `Diagnostics.java`, `LogTimeline.java` | Event log and exit records |
| `VpnDuration.java` | "VPN detected since" duration |
| `tests/` | Plain-Java unit tests run by `build.sh` |

## License

Copyright (C) 2026 MaurizioJJ

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

This project is not affiliated with proxycheck.io, ipify or Amazon.
