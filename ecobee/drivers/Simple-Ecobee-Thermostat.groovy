/**
 *  Simple Ecobee Thermostat
 *
 *  A minimal, self-contained Hubitat driver for Ecobee thermostats.
 *  Distilled from Barry A. Burke's (SANdood) Ecobee Suite, keeping ONLY:
 *      - temperature          (indoor temperature, as a device attribute)
 *      - weatherTemperature   (outdoor temperature, as a device attribute)
 *      - setThermostatProgram (switch the active comfort program: Home/Away/Sleep/...)
 *
 *  ---------------------------------------------------------------------------------
 *  WHY THIS NEEDS A REFRESH TOKEN (READ THIS)
 *  ---------------------------------------------------------------------------------
 *  Ecobee shut down its developer program on 2024-03-28. You can no longer create
 *  an API key, and the initial "log in and authorize" step (OAuth authorization-code
 *  flow) can ONLY be completed by a full SmartApp with a cloud callback URL - a
 *  driver by itself cannot do it.
 *
 *  HOWEVER, once an authorization already exists, *refreshing* the token needs only
 *  the client_id + the current refresh_token (no app, no callback). Since you already
 *  have the full Ecobee Suite installed and authorized, we simply hand this driver the
 *  Suite's existing refresh token, and from then on this driver keeps itself alive by
 *  refreshing on its own.
 *
 *  IMPORTANT: Ecobee ROTATES the refresh token on every refresh, so only ONE thing can
 *  own it. After you hand the token to this driver, you must stop the Ecobee Suite from
 *  polling/refreshing (pause or remove it), or the two will invalidate each other.
 *
 *  ---------------------------------------------------------------------------------
 *  ONE-TIME SETUP
 *  ---------------------------------------------------------------------------------
 *  1. Get the current refresh token out of the working Ecobee Suite:
 *       a. Hubitat -> Apps -> Ecobee Suite Manager. Note the app's number in the URL
 *          (…/installedapp/configure/NNN).
 *       b. Force a fresh token: open the Suite's "Debug Dashboard" (or the
 *          "refreshAuthTokenPage") so the token is freshly minted, OR just proceed -
 *          any current token works as long as the Suite hasn't refreshed since.
 *       c. In your browser go to:  http://<your-hub-ip>/installedapp/status/NNN
 *          (replace NNN with the app number). This is the App Status page.
 *       d. Under "State Variables" (or "Application State"), find "refreshToken" and
 *          copy its value (a long string).
 *  2. STOP the Ecobee Suite from refreshing so it won't fight over the token:
 *          Apps -> Ecobee Suite Manager -> Pause (or Remove it entirely once you've
 *          confirmed this driver works).
 *  3. In Hubitat: Drivers Code -> New Driver -> paste this file -> Save.
 *  4. Devices -> Add Device -> Virtual -> pick "Simple Ecobee Thermostat".
 *  5. On the device page, paste the refresh token into the "Ecobee refresh token"
 *     preference, leave the Client ID at its default, click "Save Preferences".
 *  6. Watch the logs: it should refresh, then poll. "apiConnected" becomes "full".
 *
 *  If it ever shows "invalid_grant", the token was already rotated away (e.g. the
 *  Suite refreshed after you copied it). Grab a fresh refreshToken again (step 1),
 *  make sure the Suite is paused, and re-paste it.
 *
 *  To copy the temperatures into Hub Variables, use Rule Machine: set a hub variable
 *  = this device's "temperature" / "weatherTemperature" attribute.
 */

metadata {
    definition(name: "Simple Ecobee Thermostat", namespace: "sriley-claude", author: "Simplified from SANdood Ecobee-Suite") {
        capability "Temperature Measurement"   // provides the "temperature" attribute
        capability "Refresh"
        capability "Polling"
        capability "Sensor"

        attribute "weatherTemperature", "number"   // outdoor temperature
        attribute "thermostatProgram",  "string"   // currently active comfort program
        attribute "availablePrograms",  "string"   // list of program names you can set
        attribute "apiConnected",       "string"   // full / lost
        attribute "authStatus",         "string"   // ok / needs token / invalid

        command "refreshTokens"                      // manual token refresh (usually automatic)
        command "setRefreshToken", [[name: "token*", type: "STRING",
                 description: "Seed a new Ecobee refresh token (used by the Simple Ecobee Auth app's push button)"]]
        command "setThermostatProgram", [[name: "program*", type: "STRING",
                 description: "Comfort program name, e.g. Home, Away, Sleep"]]
    }

    preferences {
        input name: "refreshToken", type: "text", title: "Ecobee refresh token (from the Ecobee Suite's App Status page)", required: true
        input name: "clientId",     type: "text", title: "Ecobee Client ID (API key)",
              defaultValue: "NOpc6i5ooiLLi1VPtVlJ0uv9Nh5cCfcc", required: true
        input name: "holdType",     type: "enum", title: "Hold type when setting a program",
              options: ["nextTransition": "Until next scheduled activity", "indefinite": "Until I change it"],
              defaultValue: "nextTransition", required: true
        input name: "pollInterval", type: "enum", title: "Poll interval (minutes)",
              options: ["5", "10", "15", "30"], defaultValue: "10", required: true
        input name: "logEnable",    type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

// ============================ Constants =====================================
private getApiEndpoint()  { return "https://api.ecobee.com" }

// ============================ Lifecycle =====================================
def installed() {
    log.info "Simple Ecobee Thermostat installed"
    updated()
}

def updated() {
    log.info "Preferences updated"
    unschedule()

    // Seed / re-seed the working refresh token from the preference. We only take the
    // preference value when it differs from what we last seeded, so that normal token
    // rotation (stored in state) is never clobbered by the now-stale preference value.
    if (settings.refreshToken && settings.refreshToken != state.seededToken) {
        state.refreshToken = settings.refreshToken
        state.seededToken  = settings.refreshToken
        state.authToken    = null            // force a fresh refresh on next call
        log.info "New refresh token seeded from preferences."
    }

    if (state.refreshToken) {
        schedulePolling()
        runIn(3, poll)
    } else {
        sendEvent(name: "authStatus", value: "needs token")
        log.warn "No refresh token set - paste one into the preferences."
    }
}

private schedulePolling() {
    def mins = (settings.pollInterval ?: "10") as Integer
    switch (mins) {
        case 5:  runEvery5Minutes(poll);  break
        case 15: runEvery15Minutes(poll); break
        case 30: runEvery30Minutes(poll); break
        default: runEvery10Minutes(poll)
    }
    debug "Polling scheduled every ${mins} minutes"
}

// ============================ Token refresh =================================
// Called by the Simple Ecobee Auth app's "push token to driver" button (or manually).
// Seeds a new refresh token, keeps the preference in sync so updated() won't clobber it,
// then immediately refreshes + polls.
def setRefreshToken(String token) {
    if (!token?.trim()) { log.warn "setRefreshToken() called with an empty token"; return }
    state.refreshToken = token.trim()
    state.seededToken  = token.trim()
    state.authToken    = null
    device.updateSetting("refreshToken", [value: token.trim(), type: "text"])  // keep pref in sync
    log.info "Refresh token seeded via setRefreshToken()."
    unschedule()
    schedulePolling()
    runIn(2, poll)
}

def refreshTokens() {
    if (!state.refreshToken) { log.error "No refresh token set."; sendEvent(name: "authStatus", value: "needs token"); return false }
    def key = settings.clientId ?: "NOpc6i5ooiLLi1VPtVlJ0uv9Nh5cCfcc"
    debug "Refreshing access token..."
    def params = [
        uri: apiEndpoint,
        path: "/token",
        query: [grant_type: "refresh_token", refresh_token: state.refreshToken, client_id: key],
        timeout: 30
    ]
    boolean ok = false
    try {
        httpPost(params) { resp ->
            if (resp?.status == 200 && resp.data?.access_token) {
                state.authToken = resp.data.access_token
                // Ecobee rotates the refresh token - persist the new one or we'll be locked out.
                if (resp.data.refresh_token) state.refreshToken = resp.data.refresh_token
                state.authTokenExpires = now() + ((resp.data.expires_in ?: 3600) * 1000L)
                sendEvent(name: "authStatus", value: "ok")
                ok = true
                debug "Token refreshed (expires in ${resp.data.expires_in}s)."
            } else {
                log.error "refreshTokens() unexpected response: ${resp?.status} ${resp?.data}"
            }
        }
    } catch (e) {
        // A 4xx here usually means the token was already rotated away (e.g. the Suite
        // refreshed after you copied it) or revoked.
        log.error "refreshTokens() failed: ${e}. If this says invalid_grant, grab a fresh " +
                  "refreshToken from the Ecobee Suite App Status page (and pause the Suite) and re-paste it."
        sendEvent(name: "authStatus", value: "invalid - re-paste token")
    }
    return ok
}

// Ensure we have a valid (non-expired) token before an API call; refresh if needed.
private boolean ensureToken() {
    if (!state.refreshToken) { log.error "No refresh token set."; return false }
    if (!state.authToken || now() > (state.authTokenExpires ?: 0) - 60000L) {   // refresh 1 min before expiry
        return refreshTokens()
    }
    return true
}

// ============================ Polling / data ================================
def refresh() { poll() }

def poll() {
    if (!ensureToken()) return
    def body = '{"selection":{"selectionType":"registered","selectionMatch":"",' +
               '"includeRuntime":true,"includeWeather":true,"includeProgram":true}}'
    def params = [
        uri: apiEndpoint,
        path: "/1/thermostat",
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${state.authToken}"],
        query: [format: "json", body: body],
        timeout: 30
    ]
    try {
        httpGet(params) { resp ->
            if (resp?.status == 200 && resp.data?.thermostatList) {
                parseThermostat(resp.data.thermostatList[0])
                sendEvent(name: "apiConnected", value: "full")
            } else {
                log.warn "poll() unexpected response: ${resp?.status} ${resp?.data}"
                sendEvent(name: "apiConnected", value: "lost")
            }
        }
    } catch (e) {
        log.error "poll() failed: ${e}"
        sendEvent(name: "apiConnected", value: "lost")
    }
}

private parseThermostat(stat) {
    if (!stat) { log.warn "No thermostat returned - is one registered to this account?"; return }
    state.thermostatId = stat.identifier

    // Indoor temperature (Ecobee returns tenths of a degree F)
    if (stat.runtime?.actualTemperature != null) {
        def t = fromEcobeeTemp(stat.runtime.actualTemperature)
        sendEvent(name: "temperature", value: t, unit: "°${getTemperatureScale()}")
        debug "Indoor temperature: ${t}°${getTemperatureScale()}"
    }

    // Outdoor temperature from the weather forecast (also tenths of a degree F)
    def wt = stat.weather?.forecasts ? stat.weather.forecasts[0]?.temperature : null
    if (wt != null) {
        def t = fromEcobeeTemp(wt)
        sendEvent(name: "weatherTemperature", value: t, unit: "°${getTemperatureScale()}")
        debug "Outdoor temperature: ${t}°${getTemperatureScale()}"
    }

    // Available comfort programs + which one is active (used by setThermostatProgram)
    def climates = stat.program?.climates
    if (climates) {
        // map lower-case name -> climateRef, e.g. [home:home, away:away, sleep:sleep, "my program":smart1]
        state.climates = climates.collectEntries { [(it.name.toString().toLowerCase()): it.climateRef.toString()] }
        def names = climates.collect { it.name }.join(", ")
        sendEvent(name: "availablePrograms", value: names)

        def curRef = stat.program?.currentClimateRef?.toString()
        def curClimate = climates.find { it.climateRef.toString() == curRef }
        if (curClimate) sendEvent(name: "thermostatProgram", value: curClimate.name)
    }
}

// Ecobee temperatures are integers in tenths of a degree Fahrenheit.
private fromEcobeeTemp(raw) {
    BigDecimal f = (raw as BigDecimal) / 10.0
    if (getTemperatureScale() == "C") {
        return ((f - 32) / 1.8).setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    return f.setScale(1, BigDecimal.ROUND_HALF_UP)
}

// ============================ setThermostatProgram ==========================
def setThermostatProgram(String program) {
    if (!ensureToken()) return
    if (!state.thermostatId) { log.warn "No thermostat id yet - polling first."; poll(); return }
    if (!state.climates)     { log.warn "No program list yet - polling first."; poll(); return }

    // Accept either the display name ("Home") or the raw climateRef ("home", "smart1")
    def key = program?.toString()?.toLowerCase()
    def ref = state.climates[key]
    if (!ref && state.climates.values().contains(key)) ref = key
    if (!ref) {
        log.warn "Unknown program '${program}'. Available: ${state.climates.keySet().join(', ')}"
        return
    }

    def ht = settings.holdType ?: "nextTransition"
    // Resume any existing hold first, then hold the requested comfort program.
    def body = '{"functions":[' +
               '{"type":"resumeProgram","params":{"resumeAll":"true"}},' +
               '{"type":"setHold","params":{"holdClimateRef":"' + ref + '","holdType":"' + ht + '"}}' +
               '],"selection":{"selectionType":"thermostats","selectionMatch":"' + state.thermostatId + '"}}'

    debug "setThermostatProgram(${program}) -> climateRef=${ref}, holdType=${ht}"
    if (sendJson(body)) {
        log.info "Set program to '${program}'"
        runIn(8, poll)   // pick up the change
    } else {
        log.warn "Failed to set program to '${program}'"
    }
}

// ============================ Shared POST to Ecobee =========================
private boolean sendJson(String jsonBody) {
    if (!ensureToken()) return false
    def params = [
        uri: apiEndpoint,
        path: "/1/thermostat",
        headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.authToken}"],
        body: jsonBody,
        timeout: 30
    ]
    boolean result = false
    try {
        httpPost(params) { resp ->
            def code = resp?.data?.status?.code
            if (resp?.status == 200 && code == 0) {
                result = true
            } else {
                log.warn "sendJson() API status=${code} (${resp?.data?.status?.message})"
            }
        }
    } catch (e) {
        log.error "sendJson() failed: ${e}"
    }
    return result
}

// ============================ Helpers =======================================
private debug(msg) { if (settings.logEnable != false) log.debug msg }
