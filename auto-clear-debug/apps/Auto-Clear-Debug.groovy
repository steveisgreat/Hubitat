/**
 *  Auto Clear Debug
 *
 *  Automatically turns OFF a device's debug logging a set time (default 1 hour)
 *  after it was turned ON.
 *
 *  Hubitat has no universal "debug flag". By convention a driver's debug switch is a
 *  boolean preference named "logEnable" (this app also checks other common names).
 *  This app watches the selected devices; when it sees the debug preference switch to
 *  ON it starts a timer, and turns it back OFF when the timer expires.
 *
 *  IMPORTANT LIMITATION - APPS:
 *  A normal Hubitat app cannot read or change ANOTHER app's preferences. There is no
 *  supported API for it. So this manages DEVICES only. Covering other *apps* is only
 *  possible by driving the hub's local admin web interface over HTTP (login required).
 *  Ask and I can add that as an option.
 *
 *  Author: Steve
 */

definition(
    name:        "Auto Clear Debug",
    namespace:   "steveisgreat",
    author:      "Steve",
    description: "Turns off device debug logging automatically, a set time after it is enabled.",
    category:    "Utility",
    iconUrl:     "",
    iconX2Url:   "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Auto Clear Debug", install: true, uninstall: true) {
        section("Devices to watch") {
            // "capability.*" lists every device on the hub so you can pick any of them.
            input name: "watchedDevices", type: "capability.*",
                  title: "Select devices whose debug logging should auto-clear",
                  multiple: true, required: false
        }
        section("Behavior") {
            input name: "timeoutMinutes", type: "number",
                  title: "Turn debug OFF this many minutes after it turns ON",
                  defaultValue: 60, required: true
            input name: "checkEvery", type: "enum", title: "Check devices every",
                  options: ["1":"1 minute","5":"5 minutes","10":"10 minutes","15":"15 minutes"],
                  defaultValue: "5", required: true
            input name: "settingNames", type: "text",
                  title: "Debug preference name(s) to look for (comma separated)",
                  defaultValue: "logEnable,debugEnable,debugOutput,debugLogging",
                  required: true
        }
        section("Logging") {
            input name: "appDebug", type: "bool",
                  title: "Enable debug logging for THIS app", defaultValue: true
        }
        section("Status") {
            paragraph statusText()
        }
    }
}

def installed() {
    logDebug "installed"
    initialize()
}

def updated() {
    logDebug "updated"
    unschedule()
    initialize()
}

def initialize() {
    state.timers = state.timers ?: [:]   // key "deviceId:settingName" -> epoch millis first seen ON
    def mins = (checkEvery ?: "5").toInteger()
    switch (mins) {
        case 1:  runEvery1Minute("checkDevices");   break
        case 10: runEvery10Minutes("checkDevices"); break
        case 15: runEvery15Minutes("checkDevices"); break
        default: runEvery5Minutes("checkDevices")
    }
    checkDevices()
}

def checkDevices() {
    def names     = (settingNames ?: "logEnable").tokenize(",")*.trim().findAll { it }
    def timeoutMs = ((timeoutMinutes ?: 60) as Long) * 60000L
    def now       = now()
    def timers    = state.timers ?: [:]

    watchedDevices?.each { dev ->
        names.each { name ->
            def key = "${dev.id}:${name}"
            def val = readBool(dev, name)

            if (val == true) {
                if (!timers[key]) {
                    timers[key] = now
                    logDebug "${dev.displayName} '${name}' turned ON - starting ${timeoutMinutes} min timer"
                } else if (now - (timers[key] as Long) >= timeoutMs) {
                    logDebug "${dev.displayName} '${name}' timer expired - turning OFF"
                    turnOff(dev, name)
                    timers.remove(key)
                }
            } else if (timers[key]) {
                // It was turned off manually (or by the driver) before our timer fired.
                logDebug "${dev.displayName} '${name}' already OFF - clearing timer"
                timers.remove(key)
            }
        }
    }
    state.timers = timers
}

// Reads a device driver preference as a boolean. Returns null if the setting
// does not exist / cannot be read (that device/name is then simply ignored).
private readBool(dev, name) {
    try {
        def v = dev.getSetting(name)
        if (v == null) return null
        return (v == true || v.toString() == "true")
    } catch (e) {
        logDebug "Could not read setting '${name}' on ${dev.displayName}: ${e.message}"
        return null
    }
}

private turnOff(dev, name) {
    try {
        dev.updateSetting(name, [value: "false", type: "bool"])
        log.info "Auto Clear Debug: turned OFF '${name}' on ${dev.displayName}"
    } catch (e) {
        log.warn "Auto Clear Debug: failed to turn off '${name}' on ${dev.displayName}: ${e.message}"
    }
}

private statusText() {
    def timers = state.timers ?: [:]
    if (!timers) return "No debug logging currently being timed."
    def timeoutMs = ((timeoutMinutes ?: 60) as Long) * 60000L
    def lines = timers.collect { key, started ->
        def remainMin = Math.max(0, (timeoutMs - (now() - (started as Long))) / 60000L as Long)
        "${key} - ~${remainMin} min remaining"
    }
    "Currently timing:\n" + lines.join("\n")
}

private logDebug(msg) {
    if (appDebug != false) log.debug "Auto Clear Debug: ${msg}"
}
