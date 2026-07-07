# Hubitat

Custom Hubitat apps and drivers, maintained by Steve Riley.

Each project lives in its own top-level folder, with `drivers/` and/or `apps/`
subfolders as needed. To install a driver or app in Hubitat, open **Drivers Code
/ Apps Code → New → Import** and paste its raw import URL (linked below).

## Projects

| Project | What it is | Type |
|---|---|---|
| [`samsung-frame-tv/`](samsung-frame-tv/) | Local-LAN control of a Samsung Frame / Tizen TV (no cloud) | Driver |
| [`wled-simple/`](wled-simple/) | Simple whole-strip WLED controller | Driver |
| [`omnilogic/`](omnilogic/) | Full Hayward OmniLogic pool/spa integration (fork) | App + drivers |

---

### samsung-frame-tv

Cloud-free control of a Samsung Frame / Tizen Smart TV entirely over your local
network — no Samsung or SmartThings account required.

- **Switch**: power **on** via Wake-on-LAN (primary MAC, plus an optional
  alternate/standby MAC for TVs that wake on a different interface), power
  **off** via the TV's local WebSocket (`KEY_POWER`).
- **`sendKey`** for any Tizen remote key (`KEY_VOLUP`, `KEY_HOME`, `KEY_MUTE`, …)
  and **`artMode`** to toggle the Frame's Art Mode.
- Secure WebSocket pairing with automatic token capture/reuse (approve the
  on-screen prompt once).
- HTTP status polling keeps the switch state in sync with the real TV.
- **`findAlternateMac`** auto-discovers the TV's MAC(s) from its local API and
  fills the preference fields for you.

Import URL: `https://raw.githubusercontent.com/steveisgreat/Hubitat/main/samsung-frame-tv/drivers/SamsungFrameTvSR`

**Quick start:** On the TV, enable *Network → Expert Settings → Power On with
Mobile* and *IP Remote*, and give it a static IP / DHCP reservation. Import the
driver, create a virtual device of type **Samsung Frame TV (Local LAN)**, enter
the TV's IP and MAC, then send any command once and approve the **Allow** prompt
on the TV to pair. See the header comments in the driver for full notes.

### wled-simple

Control a [WLED](https://kno.wled.ge/) LED controller over your LAN (whole-strip,
no per-segment control). A trimmed-down fork of bryan@joyful.house's driver.

- **Switch**, **Switch Level**, and full **Color Control** (hue/saturation/level).
- **`setEffect`** (effect ID, speed, intensity, palette), with effect and palette
  names resolved live from the device.
- **`setPreset`** by number and **`setPresetByName`** for saved presets.
- Configurable transition time and refresh interval; connects via the WLED
  device URL.

Import URL: `https://raw.githubusercontent.com/steveisgreat/Hubitat/main/wled-simple/drivers/WLEDSimpleSR`

### omnilogic

A full Hayward **OmniLogic** integration: a parent SmartApp plus child device
drivers. Fork of
[maartenvantjonger/omnilogic-smartapp](https://github.com/maartenvantjonger/omnilogic-smartapp)
with the heater, light, and VSP pump drivers replaced by customized (SR)
versions. Install the parent app and device handlers from the
[`omnilogic/`](omnilogic/) folder; the child devices are created and polled by
the parent app, not installed individually.

Customized drivers in this fork:

- **OmniLogic Heater** — thermostat (heat/off) with temperature measurement; can
  hold the last valid reading so temperatures aren't shown as invalid while the
  filter pump is off.
- **OmniLogic Light** — switch with rich show control: `setLightShow` (numeric
  show/speed/brightness), `setLightShowFixed` (named solid colors), and
  `setLightShowMulti` (named animated shows).
- **OmniLogic VSP** — variable-speed filter pump as a switch + level (level maps
  to pump speed); spillover-aware.

The remaining drivers (pump, relay, chlorinator, super-chlorinator, temperature
sensor) and the parent app are unchanged from upstream. The original layout
(`devicetypes/` + `smartapps/`) is preserved to keep diffs against upstream clean.
