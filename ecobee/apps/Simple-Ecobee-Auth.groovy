/**
 *  Simple Ecobee Auth
 *
 *  A tiny standalone Hubitat SmartApp whose ONLY job is to (re)authorize with Ecobee
 *  and hand you a fresh refresh token for the "Simple Ecobee Thermostat" driver.
 *
 *  It exists so you can REMOVE Barry Burke's (SANdood) full Ecobee Suite and still be
 *  able to re-mint a token whenever you need one - because the initial "log in and
 *  authorize" step (OAuth authorization-code flow) can only be done by an app with a
 *  cloud callback, never by a driver alone.
 *
 *  It reuses the SAME Ecobee client_id, redirect URL, and scopes the Ecobee Suite used,
 *  so the token it produces works identically. None of the Suite's other settings
 *  (thermostat selection, hold types, helpers, etc.) are needed - this app only does auth.
 *
 *  ---------------------------------------------------------------------------------
 *  INSTALL
 *  ---------------------------------------------------------------------------------
 *  1. Hubitat -> Apps Code -> New App -> paste this file -> Save.
 *  2. Click "OAuth" (top right) -> Enable OAuth in App -> Update.   <-- REQUIRED
 *  3. Apps -> Add User App -> "Simple Ecobee Auth".
 *  4. On the app page, click the "Authorize with Ecobee" link, log in, click Allow.
 *  5. When it returns, the app shows your refresh token. Copy it into the
 *     "Ecobee refresh token" preference of the Simple Ecobee Thermostat driver.
 *  6. Click Done. Leave this app installed (it does nothing until you need to
 *     re-authorize; then just reopen it and click "Re-authorize").
 *
 *  NOTE ON TOKEN ROTATION: Authorizing here mints a NEW token and invalidates any
 *  previous one. So after you re-authorize, always re-paste the new token into the
 *  driver. Only one thing (the driver) should be actively refreshing at a time.
 */

definition(
    name:            "Simple Ecobee Auth",
    namespace:       "sriley-claude",
    author:          "Simplified from SANdood Ecobee-Suite",
    description:     "Lightweight Ecobee token minter for the Simple Ecobee Thermostat driver",
    category:        "Convenience",
    iconUrl:         "",
    iconX2Url:       "",
    singleInstance:  true,
    oauth:           true
)

preferences {
    page(name: "mainPage")
}

mappings {
    path("/callback")       { action: [GET: "callback"] }
    path("/oauth/callback") { action: [GET: "callback"] }
}

// ============================ Constants (same as the Ecobee Suite) ==========
private getApiEndpoint()   { return "https://api.ecobee.com" }
private getEcobeeApiKey()  { return "NOpc6i5ooiLLi1VPtVlJ0uv9Nh5cCfcc" }   // grandfathered client_id
private getCallbackUrl()   { return "https://cloud.hubitat.com/oauth/stateredirect" }
private getStateUrl()      { return "${getHubUID()}/apps/${app?.id}/callback?access_token=${state.accessToken}" }

// ============================ UI ============================================
def mainPage() {
    // The app needs its OWN access token so Ecobee's redirect can call back in.
    if (!state.accessToken) {
        try {
            state.accessToken = createAccessToken()
        } catch (e) {
            log.error "createAccessToken() failed - is OAuth enabled? ${e}"
        }
    }

    dynamicPage(name: "mainPage", title: "Simple Ecobee Auth", install: true, uninstall: true) {
        if (!state.accessToken) {
            section {
                paragraph "⚠️ OAuth is not enabled for this app.\n\n" +
                          "Go to Apps Code -> this app -> click 'OAuth' (top right) -> " +
                          "'Enable OAuth in App' -> Update. Then reopen this app."
            }
            return
        }

        def redirectUrl = oauthInitUrl()
        if (!state.authToken) {
            section("Step 1 - Authorize") {
                paragraph "Click below, log in to your Ecobee account, and click 'Allow'."
                href url: redirectUrl, style: "external", required: false,
                     title: "Authorize with Ecobee", description: "Tap to log in"
            }
        } else {
            section("✓ Authorized") {
                paragraph "Push the freshly minted token straight into your Simple Ecobee Thermostat " +
                          "device, or copy it manually below."
                input name: "ecobeeDriver", type: "capability.temperatureMeasurement",
                      title: "Simple Ecobee Thermostat device", multiple: false, required: false, submitOnChange: true
                if (ecobeeDriver) {
                    input name: "pushToken", type: "button", title: "Push token to driver"
                    if (state.lastPush) paragraph "Last pushed: ${state.lastPush}"
                }
            }
            section("Or copy it manually") {
                paragraph "Paste into the driver's 'Ecobee refresh token' preference, then Save Preferences:"
                paragraph "<textarea rows='3' style='width:100%;font-family:monospace' readonly " +
                          "onclick='this.select()'>${state.refreshToken}</textarea>"
                paragraph "Client ID (only needed if the driver's default differs): ${getEcobeeApiKey()}"
            }
            section("Need a new token?") {
                paragraph "Re-authorizing mints a brand-new token and invalidates this one. " +
                          "Do this only if the driver reports the token is invalid - and re-paste afterward."
                href url: redirectUrl, style: "external", required: false,
                     title: "Re-authorize with Ecobee", description: "Tap to mint a new token"
            }
        }
    }
}

// ============================ OAuth handshake ===============================
def oauthInitUrl() {
    state.oauthInitState = getStateUrl()
    def oauthParams = [
        response_type: "code",
        client_id:     getEcobeeApiKey(),
        scope:         "smartRead,smartWrite,ems",
        redirect_uri:  getCallbackUrl(),
        state:         state.oauthInitState
    ]
    return "${getApiEndpoint()}/authorize?${toQueryString(oauthParams)}"
}

def callback() {
    def code       = params.code
    def oauthState = params.state
    log.debug "callback() received (state match: ${oauthState == state.oauthInitState})"

    if (oauthState != state.oauthInitState) {
        log.warn "callback() state mismatch - ignoring"
        return fail()
    }

    def tokenParams = [
        grant_type:   "authorization_code",
        code:         code,
        client_id:    getEcobeeApiKey(),
        state:        oauthState,
        redirect_uri: getCallbackUrl()
    ]
    def tokenUrl = "${getApiEndpoint()}/token?${toQueryString(tokenParams)}"
    try {
        httpPost(uri: tokenUrl) { resp ->
            if (resp && resp.data && resp.isSuccess()) {
                state.refreshToken     = resp.data.refresh_token
                state.authToken        = resp.data.access_token
                state.authTokenExpires = now() + (resp.data.expires_in * 1000)
                log.info "Ecobee authorized - refresh token minted."
            } else {
                log.error "callback() token exchange returned no data"
            }
        }
    } catch (e) {
        log.error "callback() token exchange failed: ${e}"
    }
    return state.authToken ? success() : fail()
}

def success() {
    return render(contentType: "text/html", data:
        "<!DOCTYPE html><html><body style='font-family:sans-serif;text-align:center;padding:40px'>" +
        "<h2>✓ Ecobee connected to Hubitat!</h2>" +
        "<p>Close this window and return to the app to copy your refresh token.</p>" +
        "</body></html>")
}

def fail() {
    return render(contentType: "text/html", data:
        "<!DOCTYPE html><html><body style='font-family:sans-serif;text-align:center;padding:40px'>" +
        "<h2>Connection failed</h2>" +
        "<p>Close this window and try again. Check Logs for details.</p>" +
        "</body></html>")
}

private toQueryString(Map m) {
    return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

// ============================ Push button ===================================
def appButtonHandler(String btn) {
    if (btn == "pushToken") pushTokenToDriver()
}

private pushTokenToDriver() {
    if (!settings.ecobeeDriver) { log.warn "No driver selected to push to."; return }
    if (!state.refreshToken)    { log.warn "No refresh token to push."; return }
    try {
        settings.ecobeeDriver.setRefreshToken(state.refreshToken)
        state.lastPush = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
        log.info "Pushed refresh token to ${settings.ecobeeDriver.displayName}"
    } catch (e) {
        log.error "pushTokenToDriver() failed - does the selected device use the Simple Ecobee " +
                  "Thermostat driver (with a setRefreshToken command)? ${e}"
    }
}

// ============================ Lifecycle =====================================
def installed() { log.info "Simple Ecobee Auth installed" }
def updated()   { log.info "Simple Ecobee Auth updated" }
