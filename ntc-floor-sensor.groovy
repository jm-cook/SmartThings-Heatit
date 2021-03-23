/**
 *  Heatit Z-Relay - NTC Floor sensor
 *
 *  Copyright 2021 Jeremy Cook
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
    definition(name: "NTC Floor Sensor", namespace: "jc", author: "Jeremy Cook", ocfDeviceType: "oic.d.thermostat") {
        capability "Health Check"
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
    }

}

def installed() {
    configure()
}

def updated() {
    configure()
}

def configure() {
    // Device-Watch simply pings if no device events received for checkInterval duration 1 hour
    sendEvent(name: "checkInterval", value: 1 * 60 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: parent.hubID, offlinePingable: "1"])
    refresh()
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
    log.debug "SensorMultilevelReport from NTC floor sensor ${cmd}"

    def map = [:]
    switch (cmd.sensorType) {
        case 1:
            //log.debug "temperature"
            map.name = "temperature"
            def cmdScale = cmd.scale == 1 ? "F" : "C"
            map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
            map.unit = getTemperatureScale()
            //log.debug "temperature ${map.value}"
            break
        default:
            map.descriptionText = cmd.toString()
    }
    sendEvent(map)
    return 0
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    [:]
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    refresh()
}

def refresh() {
    parent.ChildRefresh(device.deviceNetworkId)
}
