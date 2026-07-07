# Simple Ecobee for Hubitat

A minimal, self-contained Hubitat integration for Ecobee thermostats. It keeps only
three things:

- **`temperature`** – indoor temperature, as a device attribute
- **`weatherTemperature`** – outdoor temperature, as a device attribute
- **`setThermostatProgram(program)`** – switch the active comfort program (Home / Away / Sleep / …)

It's a slimmed-down alternative to Barry A. Burke's (SANdood) full
[Ecobee Suite](https://github.com/SANdood/Ecobee-Suite) for people who only need a couple
of values and a program switch, without the thousands of lines of app + helper code.

## Why the unusual auth

Ecobee shut down its developer program on **2024-03-28**. You can no longer create an API
key, and the initial "log in and authorize" step (OAuth authorization-code flow) can only be
done by an app with a cloud callback — never by a driver alone.

This project works around that in two pieces:

| File | Type | Job |
|------|------|-----|
| `Simple-Ecobee-Auth.groovy` | App | Does the one-time OAuth login and mints a **refresh token**. Reuses the same grandfathered client_id / redirect / scopes the Ecobee Suite uses. |
| `Simple-Ecobee-Thermostat.groovy` | Driver | Uses that refresh token to keep itself authenticated (the refresh grant needs only client_id + refresh_token — no app/callback), then polls temperatures and sets programs. |

> **Token rotation:** Ecobee rotates the refresh token on every refresh, so only one thing
> should actively refresh at a time. Re-authorizing in the app mints a new token and
> invalidates the old one — re-push/re-paste it into the driver afterward.

## Setup

1. **Driver:** Hubitat → *Drivers Code* → *New Driver* → paste `Simple-Ecobee-Thermostat.groovy` → Save.
2. **App:** Hubitat → *Apps Code* → *New App* → paste `Simple-Ecobee-Auth.groovy` → Save →
   click **OAuth** (top right) → *Enable OAuth in App* → Update.
3. Devices → *Add Device* → *Virtual* → **Simple Ecobee Thermostat**.
4. Apps → *Add User App* → **Simple Ecobee Auth** → **Authorize with Ecobee** → log in → Allow.
5. Back in the app, pick your Simple Ecobee Thermostat device and click **Push token to driver**
   (or copy the token into the driver's *Ecobee refresh token* preference manually).
6. The driver refreshes and polls; `apiConnected` becomes `full`.

To copy the temperatures into Hub Variables, use Rule Machine: set a hub variable equal to
the device's `temperature` / `weatherTemperature` attribute.

## Credit

Derived from the API flows in Barry A. Burke's (SANdood) Ecobee Suite. The original source is
**not** included in this repository.
