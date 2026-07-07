/**
 *  Samsung Frame TV (Local LAN) Driver for Hubitat
 *
 *  Controls a Samsung Frame / Tizen Smart TV entirely over the local network.
 *  NO Samsung / SmartThings cloud account is used.
 *
 *    - Power ON  : Wake-on-LAN magic packet sent to the TV's MAC address.
 *    - Power OFF : "KEY_POWER" sent over the TV's local WebSocket remote API
 *                  (wss://<ip>:8002/api/v2/channels/samsung.remote.control).
 *    - Status    : HTTP GET http://<ip>:8001/api/v2/  (succeeds only when the TV is on).
 *
 *  FIRST-TIME PAIRING
 *  ------------------
 *  The first time a command is sent, the TV displays an "Allow / Deny" prompt on
 *  screen. Choose Allow. The TV returns a token which this driver stores and
 *  reuses for all future commands. (Make sure the TV is ON for pairing.)
 *
 *  PREREQUISITES (on the TV)
 *  -------------------------
 *  Settings > General > External Device Manager > Device Connection Manager
 *    - Access Notification: On (first pairing) / Off (after)
 *  Settings > General > Network > Expert Settings
 *    - Power On with Mobile:  On   (enables Wake-on-LAN)
 *    - IP Remote:             On
 *  Give the TV a static IP / DHCP reservation on your router.
 *
 *  Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(
        name:      "Samsung Frame TV (Local LAN)",
        namespace: "sriley-claude",
        author:    "Steve Riley",
        importUrl: "https://raw.githubusercontent.com/steveisgreat/Hubitat/main/samsung-frame-tv/drivers/SamsungFrameTvSR.groovy"
    ) {
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Configuration"

        // Send an arbitrary remote key, e.g. KEY_VOLUP, KEY_HOME, KEY_MUTE
        command "sendKey", [[name: "Key*", type: "STRING", description: "e.g. KEY_POWER, KEY_VOLUP, KEY_HOME"]]
        // Toggle Art Mode on/off (Frame TVs). Sends KEY_POWER as a short click,
        // which on a Frame toggles between Art Mode and TV when already awake.
        command "artMode"
        // Query the TV (must be ON) for its MAC and auto-fill the alternate MAC field.
        command "findAlternateMac"

        attribute "token", "string"
    }

    preferences {
        input name: "tvIp",      type: "string", title: "TV IP Address",  description: "e.g. 192.168.1.50", required: true
        input name: "tvMac",     type: "string", title: "TV MAC Address", description: "for Wake-on-LAN, e.g. AA:BB:CC:DD:EE:FF", required: true
        input name: "tvWolMac",  type: "string", title: "Alternate / Standby MAC (optional)", description: "Some Samsung TVs wake on a different MAC than the running one. If WoL is unreliable, enter it here.", required: false
        input name: "wolPort",   type: "enum",   title: "Wake-on-LAN Port", options: ["7": "7", "9": "9"], defaultValue: "9", required: true
        input name: "tvPort",    type: "enum",   title: "WebSocket Port", options: ["8002": "8002 (wss, 2018+ models)", "8001": "8001 (ws, older models)"], defaultValue: "8002", required: true
        input name: "pollMins",  type: "enum",   title: "Status Polling", options: ["0": "Disabled", "1": "1 min", "5": "5 min", "10": "10 min", "30": "30 min"], defaultValue: "5"
        input name: "logEnable", type: "bool",   title: "Enable debug logging", defaultValue: true
    }
}

// ---------------------------------------------------------------------------
//  Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    log.info "Samsung Frame TV driver installed"
    configure()
}

def updated() {
    log.info "Samsung Frame TV driver updated"
    configure()
}

def configure() {
    unschedule()
    if (logEnable) log.debug "configure(): ip=${tvIp} mac=${tvMac} port=${tvPort}"

    // Hubitat's LAN stack keys devices by a hex Device Network ID.
    if (tvMac) {
        def dni = tvMac.replaceAll("[:\\-.]", "").toUpperCase()
        if (device.deviceNetworkId != dni) {
            device.setDeviceNetworkId(dni)
            if (logEnable) log.debug "Set DNI to ${dni}"
        }
    }

    def mins = (pollMins ?: "5") as Integer
    if (mins > 0) {
        if (mins == 1)       runEvery1Minute("refresh")
        else if (mins == 5)  runEvery5Minutes("refresh")
        else if (mins == 10) runEvery10Minutes("refresh")
        else                 runEvery30Minutes("refresh")
        if (logEnable) log.debug "Polling every ${mins} min"
    }

    if (logEnable) runIn(1800, "logsOff")
    runIn(2, "refresh")
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ---------------------------------------------------------------------------
//  Switch capability
// ---------------------------------------------------------------------------

def on() {
    if (!tvMac) {
        log.warn "on() requires a TV MAC address to be configured"
        return
    }

    // Send a Wake-on-LAN magic packet to the primary MAC and, if configured,
    // the alternate standby MAC (some Samsung TVs wake only on the latter).
    sendWol(tvMac)
    if (tvWolMac?.trim()) sendWol(tvWolMac)

    // The magic packet is fire-and-forget; verify actual state shortly after.
    runIn(6, "refresh")
}

// Build and broadcast a raw WoL magic packet: 6x 0xFF followed by the MAC
// repeated 16 times, sent as a UDP broadcast on the configured WoL port.
private void sendWol(String rawMac) {
    def mac = rawMac.replaceAll("[:\\-.]", "").toUpperCase()
    if (mac.length() != 12) {
        log.warn "sendWol(): invalid MAC '${rawMac}'"
        return
    }
    def port = (wolPort ?: "9")
    def cmd = "FFFFFFFFFFFF" + (mac * 16)
    if (logEnable) log.debug "sendWol(): broadcasting magic packet for ${mac} to 255.255.255.255:${port}"

    sendHubCommand(new HubAction(
        cmd,
        Protocol.LAN,
        [type:               HubAction.Type.LAN_TYPE_UDPCLIENT,
         destinationAddress: "255.255.255.255:${port}",
         encoding:           HubAction.Encoding.HEX_STRING]))
}

def off() {
    if (logEnable) log.debug "off(): sending KEY_POWER over WebSocket"
    sendKey("KEY_POWER")
    runIn(6, "refresh")
}

// ---------------------------------------------------------------------------
//  Remote key commands (WebSocket)
// ---------------------------------------------------------------------------

def artMode() {
    // On a Frame TV that is already awake, a short KEY_POWER click toggles Art Mode.
    if (logEnable) log.debug "artMode(): toggling Art Mode via KEY_POWER"
    sendKey("KEY_POWER")
}

def sendKey(String key) {
    if (!tvIp) {
        log.warn "sendKey(): TV IP address not configured"
        return
    }
    if (logEnable) log.debug "sendKey(${key})"
    state.pendingKey = key
    openSocket()
}

private void openSocket() {
    def port = (tvPort ?: "8002")
    def scheme = (port == "8002") ? "wss" : "ws"
    // Base64 of the app name shown on the TV during pairing.
    def name64 = "SGViaXRhdA=="   // "Hubitat"
    def tok = device.currentValue("token")

    def url = "${scheme}://${tvIp}:${port}/api/v2/channels/samsung.remote.control?name=${name64}"
    if (tok) url += "&token=${tok}"

    if (logEnable) log.debug "Connecting WebSocket: ${url.replaceAll('token=.*', 'token=***')}"

    try {
        interfaces.webSocket.connect(url, ignoreSSLIssues: true, perMessageDeflate: false)
    } catch (e) {
        log.error "WebSocket connect failed: ${e.message}"
    }
}

// ---------------------------------------------------------------------------
//  WebSocket callbacks
// ---------------------------------------------------------------------------

def webSocketStatus(String status) {
    if (logEnable) log.debug "webSocketStatus: ${status}"
    if (status?.startsWith("failure") || status?.startsWith("closing")) {
        // A refused/closed connection while trying to send power-off usually
        // means the TV is already off. Reconcile state on the next poll.
        state.pendingKey = null
    }
}

def parse(String message) {
    if (logEnable) log.debug "parse: ${message}"
    def json
    try {
        json = new JsonSlurper().parseText(message)
    } catch (e) {
        log.warn "parse: non-JSON message: ${message}"
        return
    }

    // The TV signals a ready channel with event ms.channel.connect and,
    // on first pairing, includes the token to store and reuse.
    if (json?.event == "ms.channel.connect") {
        def newToken = json?.data?.token
        if (newToken && newToken.toString() != device.currentValue("token")) {
            log.info "Received new pairing token from TV"
            sendEvent(name: "token", value: newToken.toString())
        }
        sendPendingKey()
    } else if (json?.event == "ms.channel.unauthorized") {
        log.warn "TV denied the connection. Approve the 'Allow' prompt on the TV screen and retry."
        state.pendingKey = null
        closeSocket()
    }
}

private void sendPendingKey() {
    def key = state.pendingKey
    if (!key) { closeSocket(); return }

    def payload = JsonOutput.toJson([
        method: "ms.remote.control",
        params: [
            Cmd:          "Click",
            DataOfCmd:    key,
            Option:       "false",
            TypeOfRemote: "SendRemoteKey"
        ]
    ])
    if (logEnable) log.debug "Sending key payload: ${payload}"
    try {
        interfaces.webSocket.sendMessage(payload)
    } catch (e) {
        log.error "sendMessage failed: ${e.message}"
    }
    state.pendingKey = null
    // Give the TV a moment to act on the key, then close the socket cleanly.
    runIn(2, "closeSocket")
}

def closeSocket() {
    try {
        interfaces.webSocket.close()
    } catch (ignored) { }
}

// ---------------------------------------------------------------------------
//  Refresh / status
// ---------------------------------------------------------------------------

def findAlternateMac() {
    if (!tvIp) {
        log.warn "findAlternateMac(): TV IP address not configured"
        return
    }
    log.info "findAlternateMac(): querying http://${tvIp}:8001/api/v2/ (TV must be ON)"
    try {
        httpGet([uri: "http://${tvIp}:8001/api/v2/", timeout: 5]) { resp ->
            def dev = resp?.data?.device
            if (logEnable) log.debug "Device info: ${dev}"

            // Collect every MAC-looking value the TV reports.
            def macs = [dev?.wifiMac, dev?.mac, dev?.ethernetMac]
                .findAll { it }
                .collect { it.toString().toUpperCase() }
                .unique()

            if (!macs) {
                log.warn "findAlternateMac(): TV returned no MAC address. Model may not expose it; use the TV's About screen or your router instead."
                return
            }

            def norm = { it?.replaceAll("[:\\-.]", "")?.toUpperCase() }
            def primary = norm(tvMac)

            // If no primary MAC is set yet, adopt the first reported one.
            if (!tvMac?.trim()) {
                device.updateSetting("tvMac", [value: macs[0], type: "string"])
                log.info "findAlternateMac(): set TV MAC Address to ${macs[0]}"
            }

            // The alternate is any reported MAC that differs from the primary.
            def alt = macs.find { norm(it) != primary && norm(it) != norm(macs[0]) } ?:
                      macs.find { norm(it) != primary }

            if (alt && norm(alt) != primary) {
                device.updateSetting("tvWolMac", [value: alt, type: "string"])
                log.info "findAlternateMac(): set Alternate / Standby MAC to ${alt}"
            } else {
                log.info "findAlternateMac(): TV reports a single MAC (${macs.join(', ')}); no distinct alternate found. Leaving Alternate MAC blank is fine."
            }
        }
    } catch (e) {
        log.warn "findAlternateMac(): lookup failed (${e.message}). Is the TV ON and reachable at ${tvIp}?"
    }
}

def refresh() {
    if (!tvIp) return
    if (logEnable) log.debug "refresh(): polling http://${tvIp}:8001/api/v2/"
    try {
        httpGet([uri: "http://${tvIp}:8001/api/v2/", timeout: 5]) { resp ->
            // Any successful response means the TV network interface is fully on.
            updateSwitch("on")
        }
    } catch (java.net.SocketTimeoutException | org.apache.http.conn.ConnectTimeoutException e) {
        updateSwitch("off")
    } catch (e) {
        // Connection refused / no route → TV is off or in deep standby.
        if (logEnable) log.debug "refresh(): ${e.class.simpleName} - treating as off"
        updateSwitch("off")
    }
}

private void updateSwitch(String value) {
    if (device.currentValue("switch") != value) {
        if (logEnable) log.debug "switch -> ${value}"
        sendEvent(name: "switch", value: value, descriptionText: "${device.displayName} is ${value}")
    }
}
