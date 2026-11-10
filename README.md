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

If your router is a **UniFi gateway** running a **NordVPN WireGuard** VPN client,
the app can also switch that client to another country from the TV, for streaming
apps that refuse one country's VPN exits. See
[Switch the router's VPN server](#switch-the-routers-vpn-server).

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

## Switch the router's VPN server

Some streaming apps refuse a VPN exit in one country and accept another. The
**Router VPN server** section of the settings screen moves the router's NordVPN
tunnel to another country without opening the UniFi app.

Set up the router first ([steps 1 to 3](#what-you-need) below), then fill in the
settings once:

1. Open **Router VPN Dot** and scroll down to **Router VPN server (UniFi + NordVPN)**.
2. Select **Gateway**, type the gateway's LAN address (for example `192.168.1.1`,
   no `https://` needed) and select **Save**. The button then shows
   `Gateway: https://192.168.1.1`.
3. Select **UniFi API key**, enter the key from [step 3](#3-create-a-unifi-api-key)
   and select **Save**. The button then shows `UniFi API key: saved`.
   Typing 30+ characters with a remote is slow: on Google TV and Android TV you
   can paste it from your phone with the Google TV app's keyboard.
4. Select **Check VPN client**. The status line above the buttons shows the client
   name, its server and the tunnel state, for example:

   ```
   NordVPN-DE → server 187.13.7.194
   CONNECTED to 187.13.7.194
   ```

   If the gateway has several WireGuard VPN clients, a list opens: pick the one to
   control. The app remembers it.

To change country later:

1. Select **Switch server country** and pick a country from NordVPN's list.
2. Wait up to a minute. The app asks NordVPN for its least-loaded WireGuard server
   in that country, writes that server's IP and public key into the VPN client and
   waits for the tunnel. The status line ends with
   `connected to <server> (<IP>)`.
3. The dot turns green again once the new exit IP is classified.

Only the server (peer IP and peer public key) changes. Your private key, tunnel
address, DNS and routes stay as you set them. A client named like `NordVPN-CZ` is
renamed to match the new country (`NordVPN-DE`); any other name is left alone.

**Forget gateway** clears the address, key, saved certificate and chosen client.

If the status line shows an error:

| Message | Fix |
|---------|-----|
| `Failed: the gateway refused the API key (HTTP 401)` | Re-enter the key, or create a new one. |
| `No WireGuard VPN client on this gateway` | Create it: [step 1](#1-create-the-nordvpn-wireguard-client-on-the-gateway). |
| `Failed: …gateway certificate changed…` | You replaced the gateway or its certificate: **Forget gateway**, then set it up again. |
| `…saved, but the tunnel hasn't reported connected after 60 s` | Select **Check VPN client** after a minute; if it stays down, pick another country. |

### What you need

- A UniFi gateway running UniFi OS with the Network application (UCG Ultra/Max,
  UDM, UDR, UXG…) and its **default** site.
- A NordVPN subscription.
- The TV device on the same LAN as the gateway.

UniFi renames menus between versions. The paths below are from Network 9.x; look
for the nearest match on yours.

### 1. Create the NordVPN WireGuard client on the gateway

NordVPN doesn't hand out WireGuard config files, so you build one from its API:

1. **Get your NordLynx private key.** In the Nord Account dashboard open
   *NordVPN → Set up NordVPN manually*, verify by e-mail and **generate a new
   access token**. Then on any computer:

   ```sh
   curl -s -u token:<ACCESS_TOKEN> https://api.nordvpn.com/v1/users/services/credentials
   ```

   Copy `nordlynx_private_key` from the reply. It's the same for every NordVPN
   server. Treat it like a password.

2. **Pick a first server.** Replace `57` with a country id from
   `https://api.nordvpn.com/v1/servers/countries` (57 = Czech Republic,
   81 = Germany):

   ```sh
   curl -s -g 'https://api.nordvpn.com/v1/servers/recommendations?filters[country_id]=57&filters[servers_technologies][identifier]=wireguard_udp&limit=1'
   ```

   Note `hostname`, `station` (the server IP) and, under the `wireguard_udp`
   technology, the `public_key` value.

3. **Create the client.** UniFi Network → *Settings → VPN → VPN Client → Create
   New* → **WireGuard**, configuration **Manual**:

   | Field | Value |
   |-------|-------|
   | Name | `NordVPN-CZ` (country code at the end lets the app keep it current) |
   | Private key | `nordlynx_private_key` from step 1 |
   | Tunnel IP / Address | `10.5.0.2/32` |
   | Server address | `station` from step 2 |
   | Port | `51820` |
   | Public key | `public_key` from step 2 |
   | Primary / secondary DNS | `103.86.96.100` / `103.86.99.100` |

   Save, and check under *Settings → VPN → VPN Client* that it shows
   **Connected**.

### 2. Route the TV device through it

UniFi Network → *Settings → Routing → Policy-Based Routes* (older versions:
*Traffic Routes*) → **Create Entry**:

- Interface / next hop: the VPN client from step 1.
- Source / devices: the TV device. Give it a fixed IP first (*Client devices → the
  device → Settings → Fixed IP address*) so the rule keeps matching.
- Destination: Internet (all traffic).
- Kill switch: your choice. With it off, traffic falls back to the normal
  internet when the tunnel drops, and the dot turns red.

### 3. Create a UniFi API key

UniFi Network → *Settings → Control Plane → Integrations* → **Create API Key**.
Copy it once; UniFi doesn't show it again. Enter it in the app with the remote's
on-screen keyboard.

The key carries the full rights of the admin who created it, and it's stored on
the TV device (app backups are off, so it isn't copied off the device by Android
backup). To limit the damage if the device is lost, create the key under a
separate local admin limited to the Network application (Full Management is needed
to edit the VPN client), and delete the key in
*Integrations* when you no longer need it.

### Gateway certificate

UniFi gateways use a self-signed HTTPS certificate. The app trusts the certificate
it sees on the first connection and refuses any other one afterwards ("gateway
certificate changed"). If you replaced the certificate or the gateway, press
**Forget gateway** and set it up again.

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
- Only if you set up the router VPN switch: the UniFi API key is stored on the
  device and sent only to your gateway, and the country list and server choice
  come from `api.nordvpn.com`.
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
| `UnifiClient.java` | UniFi Network API and NordVPN server API calls for the router VPN switch |
| `RouterVpn.java` | Pure helpers for the switch (address, key and name checks) |
| `tests/` | Plain-Java unit tests run by `build.sh` |

## License

Copyright (C) 2026 MaurizioJJ

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

This project is not affiliated with proxycheck.io, ipify, Amazon, Ubiquiti (UniFi) or NordVPN.
