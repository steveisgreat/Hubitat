# Auto Clear Debug

A simple [Hubitat](https://hubitat.com/) app that automatically turns **off** a device's
debug logging a set time (default 1 hour) after it was turned **on**.

## Why

Hubitat has no universal "debug flag". By convention a driver's debug switch is a boolean
preference named `logEnable` (this app also checks other common names). Many drivers already
auto-disable debug logging after ~30 minutes on their own, but some don't — leaving debug logs
running forever. This app watches the devices you choose and clears their debug logging on a
timer so you don't have to remember to.

## Install

1. In the Hubitat web UI, go to **Apps Code** → **New App**.
2. Paste the contents of [`Auto-Clear-Debug.groovy`](Auto-Clear-Debug.groovy) and **Save**.
3. Go to **Apps** → **Add User App** → select **Auto Clear Debug**.
4. Choose the devices to watch, set the timeout (default 60 minutes), and tap **Done**.

## How it works

Every few minutes (configurable) the app checks each selected device's debug preference.
When it sees debug switch **on**, it starts a timer; when the timer expires it sets the
preference back to **off**. If debug is turned off manually (or by the driver) before the
timer fires, the timer simply clears.

## Settings

| Setting | Description | Default |
| --- | --- | --- |
| Devices to watch | The devices whose debug logging should auto-clear | — |
| Timeout | Minutes after debug turns on before it's turned off | 60 |
| Check interval | How often the app polls the devices | 5 minutes |
| Debug preference name(s) | Comma-separated preference names to look for | `logEnable,debugEnable,debugOutput,debugLogging` |

## Limitations

- **Devices only.** A normal Hubitat app cannot read or change another *app's* preferences,
  so this manages devices, not other apps.
- Precision is bounded by the check interval (e.g. with a 5-minute check, the turn-off can
  be up to 5 minutes late).
- Relies on the device driver exposing its debug switch as a readable boolean preference
  (`getSetting`). If a driver uses a non-standard name, add it to the preference-names list.

## License

MIT
