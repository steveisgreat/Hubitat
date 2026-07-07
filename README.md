# Hubitat Drivers

A collection of custom Hubitat device drivers, maintained by Steve Riley.

Each driver is a single Groovy file (no extension). Install it in Hubitat via
**Drivers Code → New Driver → Import**, pasting the driver's raw import URL below.

## Drivers

| Driver | Description | Import URL |
|---|---|---|
| **Samsung Frame TV (Local LAN)** | Cloud-free control of a Samsung Frame / Tizen TV over the local network. Wake-on-LAN for power on (primary + optional standby MAC), WebSocket `KEY_POWER` for off and arbitrary remote keys, HTTP status polling, and an auto MAC-lookup command. | [SamsungFrameTvSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/SamsungFrameTvSR) |
| **WLED SR Simple** | Simple WLED driver (no segments) — color, level, effects, and switch control. | [WLEDSimpleSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/WLEDSimpleSR) |
| **OmniLogic Heater (sr)** | Hayward OmniLogic pool/spa heater control. | [OmniLogicHeaterSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/OmniLogicHeaterSR) |
| **OmniLogic Light (sr)** | Hayward OmniLogic pool/spa light control. | [OmniLogicLightSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/OmniLogicLightSR) |
| **OmniLogic VSP Pump (sr)** | Hayward OmniLogic variable-speed pump control. | [OmniLogicVSPPumpSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/OmniLogicVSPPumpSR) |

## Samsung Frame TV — quick start

1. On the TV, enable **Network → Expert Settings → Power On with Mobile** and **IP Remote**, and give the TV a static IP / DHCP reservation.
2. Import `SamsungFrameTvSR`, create a virtual device of type **Samsung Frame TV (Local LAN)**, and enter the TV's IP and MAC.
3. First command triggers an **Allow** prompt on the TV — accept it once to pair; the token is stored automatically.

See the header comments in [SamsungFrameTvSR](https://raw.githubusercontent.com/steveisgreat/Hubitat/main/SamsungFrameTvSR) for full setup notes.
