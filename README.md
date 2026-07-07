# Hubitat Drivers

A collection of custom Hubitat device drivers, maintained by Steve Riley.

Each driver is a single Groovy file (no extension). Install it in Hubitat via
**Drivers Code → New Driver → Import**, pasting the driver's raw import URL below.

## Standalone drivers

These install and run on their own.

### Samsung Frame TV (Local LAN) — [`SamsungFrameTvSR`](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/SamsungFrameTvSR)

Cloud-free control of a Samsung Frame / Tizen Smart TV entirely over your local
network — no Samsung or SmartThings account required.

- **Switch** capability: power **on** via Wake-on-LAN (primary MAC, plus an
  optional alternate/standby MAC for TVs that wake on a different interface),
  power **off** via the TV's local WebSocket (`KEY_POWER`).
- **`sendKey`** command for any Tizen remote key (`KEY_VOLUP`, `KEY_HOME`,
  `KEY_MUTE`, …) and **`artMode`** to toggle the Frame's Art Mode.
- Secure WebSocket pairing with automatic token capture/reuse (approve the
  on-screen prompt once).
- HTTP status polling keeps the switch state in sync with the real TV.
- **`findAlternateMac`** command auto-discovers the TV's MAC(s) from its local
  API and fills the preference fields for you.

### WLED SR Simple — [`WLEDSimpleSR`](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/WLEDSimpleSR)

Control a [WLED](https://kno.wled.ge/) LED controller over your LAN (whole-strip,
no per-segment control). A trimmed-down fork of bryan@joyful.house's driver.

- **Switch**, **Switch Level**, and full **Color Control** (hue/saturation/level).
- **`setEffect`** (effect ID, speed, intensity, palette), with effect and
  palette names resolved live from the device.
- **`setPreset`** by number and **`setPresetByName`** for saved presets.
- Configurable transition time and refresh interval; connects via the WLED
  device URL.

## OmniLogic pool/spa integration — [`OmniLogic/`](OmniLogic/)

A full Hayward **OmniLogic** integration: a parent SmartApp plus child device
drivers. It is a fork of
[maartenvantjonger/omnilogic-smartapp](https://github.com/maartenvantjonger/omnilogic-smartapp)
with the heater, light, and VSP pump drivers replaced by customized (SR)
versions. Install the parent app and device handlers from the
[`OmniLogic/`](OmniLogic/) folder; the child devices are created and polled by
the parent app, not installed individually.

Customized drivers in this fork:

- **OmniLogic Heater** — thermostat (heat/off) with temperature measurement;
  can hold the last valid reading so temperatures aren't shown as invalid while
  the filter pump is off.
- **OmniLogic Light** — switch with rich show control: `setLightShow` (numeric
  show/speed/brightness), `setLightShowFixed` (named solid colors), and
  `setLightShowMulti` (named animated shows).
- **OmniLogic VSP** — variable-speed filter pump as a switch + level (level maps
  to pump speed); spillover-aware.

The remaining drivers (pump, relay, chlorinator, super-chlorinator, temperature
sensor) and the parent app are unchanged from upstream.

## Samsung Frame TV — quick start

1. On the TV, enable **Network → Expert Settings → Power On with Mobile** and **IP Remote**, and give the TV a static IP / DHCP reservation.
2. Import `SamsungFrameTvSR`, create a virtual device of type **Samsung Frame TV (Local LAN)**, and enter the TV's IP and MAC.
3. First command triggers an **Allow** prompt on the TV — accept it once to pair; the token is stored automatically.

See the header comments in [SamsungFrameTvSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/SamsungFrameTvSR) for full setup notes.
