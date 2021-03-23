/**
 *  Heatit Z-Relay
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
	definition (name: "Heatit Z-Relay", namespace: "jc", author: "Jeremy Cook", cstHandler: true, ocfDeviceType: "oic.d.thermostat") {
		capability "Power Meter"
		capability "Energy Meter"
        capability "Sensor"
        capability "Switch"
		//capability "Temperature Measurement"
		//capability "Thermostat Heating Setpoint"
		capability "Thermostat Mode"
		capability "Thermostat Operating State"
        command "readparams"
		fingerprint mfr: "019B", prod: "0003", model: "020D", deviceJoinName: "Z-Relay Thermostat Module"
	}
    
        preferences {

        input (
                title: "Heatit Z-Relay",
                description: "Z-Relay Thermostat Module",
                image: "https://kodeo-heatit.imgix.net//451-heatit-z-relay-box-no-cover-front-visible-pcb.jpg",
                //url: "",
                type: "paragraph",
                element: "paragraph"
        )
        

        preferenceOptions.each { num, param ->
            input (
                    title: "${num}. ${param.name}",
                    description: param.description,
                    type: "paragraph",
                    element: "paragraph"
            )

            input (
                    name: "param${num}",
                    title: null,
                    description: "Default: $param.defaultDescription",
                    type: param.type,
                    options: param.options,
                    range: param.range,
                    //defaultValue: param.defaultValue,
                    // Per the documentation: Setting a default value for an input may render that selection in the mobile app, but the user still needs to enter data in that field. It’s recommended to not use defaultValue to avoid confusion.
                    required: param.required,
                    displayDuringSetup: false
            )
        }
        
        
        appConfigurationOptions.each { name, param ->
        
            /*
            input (
                    title: "$param.title",
                    //description: param.description,
                    type: "paragraph",
                    element: "paragraph"
            )
            */
            

            input (
                    name: "${name}",
                    title: "$param.title",
                    description: "$param.description",
                    type: param.type,
                    options: param.options,
                    range: param.range,
                    //defaultValue: param.defaultValue,
                    // Per the documentation: Setting a default value for an input may render that selection in the mobile app, but the user still needs to enter data in that field. It’s recommended to not use defaultValue to avoid confusion.
                    required: param.required,
                    displayDuringSetup: false
            )
        }

        }

}

private static getROOT_ENDPOINT() {0}
private static getTHERMOSTAT_ENDPOINT() {1}
private static getTEMPERATURE_1_ENDPOINT() {2}
private static getTEMPERATURE_2_ENDPOINT() {3}
private static getFLOOD_ENDPOINT() {4}

private static ENDPOINT_TEMPERATURE_ID(num) {"ntc-$num"}
private static ENDPOINT_TEMPERATURE_DH() {"NTC Floor Sensor"}
private static ENDPOINT_TEMPERATURE_LABEL(num) {"Floor Sensor $num"}

private static ENDPOINT_FLOOD_ID(num) {"flood"}
private static ENDPOINT_FLOOD_DH() {"Thermofloor Flood Sensor"}
private static ENDPOINT_FLOOD_LABEL() {"Flood Sensor"}

private static getDeviceTiles() {
    [
            [name: "NTC1",  label: ENDPOINT_TEMPERATURE_LABEL(1),  id: ENDPOINT_TEMPERATURE_ID(1), dh: ENDPOINT_TEMPERATURE_DH(), ep: getTEMPERATURE_1_ENDPOINT()],
            [name: "NTC2",  label: ENDPOINT_TEMPERATURE_LABEL(2),  id: ENDPOINT_TEMPERATURE_ID(2), dh: ENDPOINT_TEMPERATURE_DH(), ep: getTEMPERATURE_2_ENDPOINT()],
            [name: "FLOOD", label: ENDPOINT_FLOOD_LABEL(),         id: ENDPOINT_FLOOD_ID(),        dh: ENDPOINT_FLOOD_DH(), ep: getFLOOD_ENDPOINT()],
    ]
}


def findChildDeviceByID(String deviceId) {
    return childDevices.find{it.deviceNetworkId == "${device.deviceNetworkId}-${deviceId}"}
}

def findChildDeviceByName(String name) {
    def tile = deviceTiles.find{it.name == name}
    if (tile == null) return null
    def childId = tile.id
    return findChildDeviceByID(childId)
}

def findChildByEndpoint(Integer ep ) {
    def tile = deviceTiles.find{it.ep == ep}
    if (tile == null) return null
    def childId = tile.id
    return findChildDeviceByID(childId)
}

def createChildDevice(String name) {
    def tile = deviceTiles.find{it.name == name}
    def childId = tile.id
    def childLabel = tile.label
    def dh = tile.dh
    logger( "Create ${name}")

    def label = "$device.displayName $childLabel"
    def dni = "${device.deviceNetworkId}-$childId"


    if (findChildDeviceByID(childId)) {
        logger( "$label already exists", "warn")
        return
    }

    try {
        addChildDevice(dh, dni, device.hub.id, [completedSetup: true, label: "${label}", isComponent: false])
        sendEvent(name: "exists$name", value: "yes", displayed: false)
    } catch (e) {
        logger( "Failed to add $label - $e", "warn")
    }
}

def removeChildDevice(String name) {
    def tile = deviceTiles.find{it.name == name}
    def childId = tile.id
    def childLabel = tile.label
    def dh = tile.dh

    def label = "$device.displayName $childLabel"
    def dni = "${device.deviceNetworkId}-$childId"

    if (!findChildDeviceByID(childId)) {
        logger( "$label does not exist")
        return
    }

    try {
        deleteChildDevice(dni)
        sendEvent(name: "exists$name", value: "no", displayed: false)
        logger( "Removed $label")
    } catch (e) {
        logger( "Failed to remove $label - $e", "warn")
    }
}

def installed() {
    initialize()
    response(writeparams())
}

def updated() {
    logger( "Updated", "trace")
    initialize()

    // Logging Level:
    state.loggingLevelIDE = (settings.loggingLevelIDE) ? settings.loggingLevelIDE.toInteger() : 3
    logger("Floor sensor 1 ${temp_1}","debug")
    logger("Floor sensor 2 ${temp_2}","debug")
    logger("Flood sensor ${flood}","debug")
    

    response(writeparams())
    


    //response(configure())
}

def initialize() {
    //state.loggingLevelIDE =  5
    capability("Temperature Measurement")
    capability("Thermostat Heating Setpoint")
    sendEvent(name:"supportedThermostatModes",    value: ['heat', 'off'], displayed: false)
    // Device-Watch simply pings if no device events received for 32min(checkInterval)
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    runEvery1Minute("poll")
    
    // Set up child devices according to choices in the app
    setupChild(temp_1, "NTC1")
    setupChild(temp_2, "NTC2")
    setupChild(flood, "FLOOD")

    logger("Initialized", "info")
    //readparams()
    return null
}

/*
 * Create or remove child device depending on user options for installed devices when set/unset
 */
def setupChild(flag, name) {
  if (flag && !findChildDeviceByName(name)) { createChildDevice(name) }
  if (!flag && findChildDeviceByName(name)) { removeChildDevice(name) }
}

def writeparams() {
    def cmds = []

    preferenceOptions.each { num, param ->
        def paramNum = num as int
        def paramSize = param.size as int
        def paramValue = settingsParam(num)
        cmds << zwave.configurationV3.configurationSet(parameterNumber: paramNum, size: paramSize, scaledConfigurationValue: paramValue).format()
    }
    //logger("writeparams ${cmds}")
    sendCommands(cmds)
    //return delayBetween(cmds, standardDelay)
    return null
}

def writeLEDParam( value) {
   if (value != state.LEDstate) {
       logger("writeLEDParam setting LED to ${value}", "debug") 
       def cmds = []
       def paramSize = 1
       def paramNum = 1
       cmds << zwave.configurationV3.configurationSet(parameterNumber: paramNum, size: paramSize, scaledConfigurationValue: value).format()
       cmds << zwave.configurationV3.configurationGet(parameterNumber: paramNum).format()
       sendCommands(cmds)
   }
}

def readparams() {
    def cmds = []
    preferenceOptions.each { paramNumber, param ->
        cmds << zwave.configurationV3.configurationGet(parameterNumber: paramNumber).format()
    };
    sendCommands(cmds)

    return 0
}

def settingsParam(num) {
    settings."param${num}" != null ? settings."param${num}" as int : preferenceOptions[num].defaultValue as int
}


def ChildRefresh(String childNetworkId) {
    def tile = deviceTiles.find{it.id == childNetworkId}
    if (tile == null) return null
    def commands = [
            multiEncap(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x01), tile.ep)
    ]
    sendCommands(commands)
}

// parse events into attributes
def parse(String description) {
	logger( "Parsing '${description}'" )

    def result = null

    def cmd = zwave.parse(description, getCommandClassVersions())
    if (cmd) {
        result = zwaveEvent(cmd)
    }  else {
        logger("parse(): Could not parse raw message: ${description}","error")
    }
    
    // TODO: handle 'power' attribute
	// TODO: handle 'temperature' attribute
	// TODO: handle 'heatingSetpoint' attribute
	// TODO: handle 'thermostatMode' attribute
	// TODO: handle 'supportedThermostatModes' attribute
	// TODO: handle 'thermostatOperatingState' attribute
    
    result
}

private static def getCommandClassVersions() {
    [
            0x5E:2, //COMMAND_CLASS_ZWAVEPLUS_INFO
            0x55:2, //COMMAND_CLASS_TRANSPORT_SERVICE
            0x98:1, //COMMAND_CLASS_SECURITY
            0x9F:1, //COMMAND_CLASS_SECURITY_2
            0x85:2, //COMMAND_CLASS_ASSOCIATION
            0x59:1, //COMMAND_CLASS_ASSOCIATION_GRP_INFO
            0x20:2, //COMMAND_CLASS_BASIC
            0x70:3, //COMMAND_CLASS_CONFIGURATION
            0x5A:1, //COMMAND_CLASS_DEVICE_RESET_LOCALLY
            0x7A:4, //COMMAND_CLASS_FIRMWARE_UPDATE_MD
            0x72:2, //COMMAND_CLASS_MANUFACTURER_SPECIFIC
            0x60:3, //COMMAND_CLASS_MULTI_CHANNEL //60:4?
            0x8E:3, //COMMAND_CLASS_MULTI_CHANNEL_ASSOCIATION
            0x73:1, //COMMAND_CLASS_POWERLEVEL        
            0x25:1, //COMMAND_CLASS_SWITCH_BINARY
            0x40:2, //COMMAND_CLASS_THERMOSTAT_MODE
            0x42:1, //COMMAND_CLASS_THERMOSTAT_OPERATING_STATE
            0x43:2, //COMMAND_CLASS_THERMOSTAT_SETPOINT
            0x6C:1, //COMMAND_CLASS_SUPERVISION
            0x86:2, //COMMAND_CLASS_VERSION
            0x32:3, //COMMAND_CLASS_METER

            //0x5B:1, //COMMAND_CLASS_CENTRAL_SCENE
            //0x31:5, //COMMAND_CLASS_SENSOR_MULTILEVEL
            //0x56:1, //COMMAND_CLASS_CRC_16_ENCAP
            //0x71:3, //COMMAND_CLASS_NOTIFICATION
            //0x75:2, //COMMAND_CLASS_PROTECTION
            //0x22:1, //COMMAND_CLASS_APPLICATION_STATUS
    ]
}

private getStandardDelay() {
	1000
}

def getModeMap() { [
	"off": 0,
	"heat": 1
]}

// handle commands
def setHeatingSetpoint(degrees) {
	setHeatingSetpoint(degrees.toDouble(), standardDelay)
}

def setHeatingSetpoint(Double degrees, Integer delay = 3000) {
	logger( "setHeatingSetpoint($degrees, $delay)", "trace")
	def deviceScale = state.scale ?: 1
	def deviceScaleString = deviceScale == 2 ? "C" : "F"
    def locationScale = getTemperatureScale()
	def p = (state.precision == null) ? 1 : state.precision

    def convertedDegrees = degrees
    
	sendCommands([
		zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: deviceScale, precision: p, scaledValue: convertedDegrees).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format()
	])
}


def setThermostatMode(String value) {
	sendCommands([
		zwave.thermostatModeV2.thermostatModeSet(mode: modeMap[value]).format(),
		zwave.thermostatModeV2.thermostatModeGet().format()
	])
}

def off() {
    logger("Executing 'off'", "debug")
    sendCommands([
        zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format(), 
        zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def on() {
    logger("Executing 'on'", "debug")
    sendCommands([
        zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format(), 
        zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def heat() {
    logger("Executing 'heat'", "debug")

    sendCommands([
        zwave.thermostatModeV3.thermostatModeSet(mode: 1).format(),
        zwave.thermostatModeV3.thermostatModeGet().format(),
        sendEvent(name: "thermostatMode", value: "heat"),
        sendEvent(name: "thermostatOperatingState", value: "heating")
    ])
}


def emergencyHeat() {
	logger( "emergencyHeat not implemented", "debug")
	// TODO: handle 'emergencyHeat' command
}

def cool() {
    logger( "cool not implemented", "debug")
	// TODO: handle 'cool' command
}

def auto() {
    logger( "auto not implemented", "debug")
	// TODO: handle 'auto' command
}

def setThermostatMode() {
    logger( "setThermostatMode not implemented", "debug")
	// TODO: handle 'setThermostatMode' command
}

def poll() {
    logger( "Running poll", "debug")
    /*
     * Ensure that we get switch status and meter readings regularly
     * The Meter report seems to stop coming even though we set the reporting interval
     */
    sendCommands([
        // switch state tels us if the relay is on or off
        zwave.switchBinaryV1.switchBinaryGet().format(),
        // The device doesnt actually support thermostatOperatingState
        //zwave.thermostatOperatingStateV2.thermostatOperatingStateGet().format(),
        // Request a meter report
        zwave.meterV2.meterGet(scale: 0).format(),      // get kWh
        zwave.meterV2.meterGet(scale: 2).format(),      // get Watts
	])
    
    /*
     * Temperature reports apparently stop coming a couple of hours after power on,
     * despite setting the interval parameter in "settings" 
     * so we make our own query here.
     */
    
    deviceTiles.each{ tile ->
        def child = findChildDeviceByID(tile.id)
        if (child) { 
          logger("refreshing ${child}")
          ChildRefresh( tile.id ) 
        }
        
    }
    logger( "Current settings: ${settings}", "debug")
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    return switchEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
    return switchEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	return switchEvents(cmd)
}

def switchEvents(physicalgraph.zwave.Command cmd) {
    def evts = []
	def value = (cmd.value ? "on" : "off")
    logger("${device.displayName} state is ${value}", "trace")

	evts << createEvent(name: "switch", value: value, descriptionText: "$device.displayName: state is $value")
    
    // Cannot read operating state with this device so we'll fake it and send heating/idle events based on the relay position
    def operatingState = (cmd.value ? "heating" : "idle")
    logger("${device.displayName} operatingState is ${operatingState}", "trace")
    evts << createEvent(name: "thermostatOperatingState", value: operatingState, descriptionText: "$device.displayName: operating state is $operatingState")
    
    // Set led value to on if "on", and blinking if "off"
    def ledState = (cmd.value ? 1 : 3)
    writeLEDParam(ledState)

    return evts
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
    logger("SensorMultilevelReport", "trace")
    def evt = null
    // Internal Temperature
    if (cmd.sensorType == 1) {
        def map = [:]
        map.name = "temperature"
        def cmdScale = cmd.scale == 1 ? "F" : "C"
        map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
        map.unit = getTemperatureScale()
        evt = createEvent(map)
    }

    return evt
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    logger( "${cmd}", "trace")
    def result = null
    def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())
    logger( "Encapsulated CMD (${cmd.sourceEndPoint}): ${encapsulatedCommand}", "trace")

    //logger( "ep${TEMPERATURE_1_ENDPOINT} ${ENDPOINT_TEMPERATURE_ID(1)} - ${findChildByEndpoint(${TEMPERATURE_1_ENDPOINT})}", "debug")
    //logger( "ep${TEMPERATURE_2_ENDPOINT} ${ENDPOINT_TEMPERATURE_ID(2)} - ${findChildByEndpoint(${TEMPERATURE_2_ENDPOINT})}", "debug")
    //logger( "ep${FLOOD_ENDPOINT} ${ENDPOINT_FLOOD_ID()} - ${findChildByEndpoint(${FLOOD_ENDPOINT})}", "debug")
    
    // should check here if the endpoint's child device exists, and ignore if it doesn't
    
    result = findChildByEndpoint(cmd.sourceEndPoint)?.zwaveEvent(encapsulatedCommand)
    
    if (result == null) { // No child to handle the endpoint, is it internal?
        if (cmd.sourceEndPoint == THERMOSTAT_ENDPOINT) {
            result = zwaveEvent(encapsulatedCommand)
        } else if (cmd.sourceEndPoint == ROOT_ENDPOINT) {
            result = zwaveEvent(encapsulatedCommand)
        } 
    }
    /*
    if (cmd.sourceEndPoint == TEMPERATURE_1_ENDPOINT) {
        result = findChildDeviceByID(ENDPOINT_TEMPERATURE_ID(1))?.zwaveEvent(encapsulatedCommand)
    } else if (cmd.sourceEndPoint == TEMPERATURE_2_ENDPOINT) {
        result = findChildDeviceByID(ENDPOINT_TEMPERATURE_ID(2))?.zwaveEvent(encapsulatedCommand)
    } else if (cmd.sourceEndPoint == FLOOD_ENDPOINT) {
        result = findChildDeviceByID(ENDPOINT_FLOOD_ID())?.zwaveEvent(encapsulatedCommand)
    } else if (cmd.sourceEndPoint == THERMOSTAT_ENDPOINT) {
        result = zwaveEvent(encapsulatedCommand)
    } else if (cmd.sourceEndPoint == ROOT_ENDPOINT) {
        result = zwaveEvent(encapsulatedCommand)
    } else {
        logger( "Unhandled Multi Channel Endpoint: ${cmd.sourceEndPoint}", "warn")
        logger( "Unhandled Encapsulated CMD: ${encapsulatedCommand}", "warn")
        result = zwaveEvent(encapsulatedCommand)
    }
    */
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
    logger( "$cmd", "trace")

    def evt = null
    def cmdScale = cmd.scale == 1 ? "F" : "C"
	def setpoint = convertTemperatureIfNeeded(cmd.scaledValue, cmdScale, cmd.precision)
	def unit = getTemperatureScale()
	switch (cmd.setpointType) {
		case 1:
			evt = createEvent(name: "heatingSetpoint", value: setpoint, unit: unit, displayed: false)
   			updateThermostatSetpoint("heatingSetpoint", setpoint)
			break;
		default:
			logger("unknown setpointType $cmd.setpointType", "warn")
			return 0
    }
    
 
	// So we can respond with same format
	state.size = cmd.size
	state.scale = cmd.scale
	state.precision = cmd.precision
    return evt
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
    logger( "$cmd", "trace")
	def map = [name: "thermostatMode", data:[supportedThermostatModes: state.supportedModes]]
	switch (cmd.mode) {
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUXILIARY_HEAT:
			map.value = "emergency heat"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
	}
	updateThermostatSetpoint(null, null)
    //  return event map
 	return createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
    logger( "$cmd", "trace")
	def supportedModes = []
	if(cmd.off) { supportedModes << "off" }
	if(cmd.heat) { supportedModes << "heat" }
	if(cmd.cool) { supportedModes << "cool" }
	if(cmd.auto) { supportedModes << "auto" }
	//if(cmd.auxiliaryemergencyHeat) { supportedModes << "emergency heat" }

	state.supportedModes = supportedModes
    //  return event map
	return createEvent(name: "supportedThermostatModes", value: supportedModes, displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
    logger( "NotificationReport CMD: ${cmd} (not implemented)", "warn")
    return 0
}


def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
    logger( "$cmd", "trace")
    def result = null
    
    if (cmd.meterType == 1) {
		if (cmd.scale == 0) {
			result = createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
            logger("Meter report energy ${cmd.scaledMeterValue} kWh", "debug")
		} else if (cmd.scale == 1) {
			result = createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
            logger("Meter report energy ${cmd.scaledMeterValue} kVAh", "debug")
		} else if (cmd.scale == 2) {
			result = createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
            logger("Meter report power ${cmd.scaledMeterValue} W", "debug")
		}
	}
    logger("MeterReport gets ${result}","debug")
    // return event map
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv3.ConfigurationReport cmd) {
    logger( "$cmd", "trace")
    logger( "Configuration: (${cmd.parameterNumber}) ${preferenceOptions[cmd.parameterNumber as int]?.name} = ${cmd.scaledConfigurationValue}" )
    //settings."param${cmd.parameterNumber}" = cmd.scaledConfigurationValue
    // return empty event map
    return [:]
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv3.ConfigurationBulkReport cmd) {
    logger( "$cmd", "trace")
    def cmds = []

    def parameterOffset = cmd.parameterOffset
    def numberOfParameters = cmd.numberOfParameters
    def stop = numberOfParameters+parameterOffset-1
    def start = parameterOffset
    
    logger("ConfigurationBulkReport: Scanning Device Parameters (#${start} to #${stop}).","info")
    (start..stop).each { i ->
            cmds << zwave.configurationV3.configurationGet(parameterNumber: i).format()
            cmds << "delay ${standardDelay}"
    }
    return [response(cmds)]
}


// defined by the capability so set it to the most likely value
def updateThermostatSetpoint(setpoint, value) {
	def scale = getTemperatureScale()
	def heatingSetpoint = (setpoint == "heatingSetpoint") ? value : getTempInLocalScale("heatingSetpoint")
	//def coolingSetpoint = (setpoint == "coolingSetpoint") ? value : getTempInLocalScale("coolingSetpoint")
	def mode = device.currentValue("thermostatMode")
	def thermostatSetpoint = heatingSetpoint    // corresponds to (mode == "heat" || mode == "emergency heat")
	/*
    if (mode == "cool") {
		thermostatSetpoint = coolingSetpoint
	} else 
    if (mode == "auto" || mode == "off") {
		// Set thermostatSetpoint to the setpoint closest to the current temperature
		def currentTemperature = getTempInLocalScale("temperature")
		if (currentTemperature > (heatingSetpoint + coolingSetpoint)/2) {
			thermostatSetpoint = coolingSetpoint
		}
	}
    */
	sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint, unit: getTemperatureScale())
}


private multiEncap(physicalgraph.zwave.Command cmd, endpoint) {
    zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpoint).encapsulate(cmd)
}

// Get stored temperature from currentState in current local scale
def getTempInLocalScale(state) {
	def temp = device.currentState(state)
	if (temp && temp.value && temp.unit) {
		return getTempInLocalScale(temp.value.toBigDecimal(), temp.unit)
	}
	return 0
}

// get/convert temperature to current local scale
def getTempInLocalScale(temp, scale) {
	if (temp && scale) {
		def scaledTemp = convertTemperatureIfNeeded(temp.toBigDecimal(), scale).toDouble()
		return (getTemperatureScale() == "F" ? scaledTemp.round(0).toInteger() : roundC(scaledTemp))
	}
	return 0
}

def getTempInDeviceScale(state) {
	def temp = device.currentState(state)
	if (temp && temp.value && temp.unit) {
		return getTempInDeviceScale(temp.value.toBigDecimal(), temp.unit)
	}
	return 0
}

def getTempInDeviceScale(temp, scale) {
	if (temp && scale) {
		def deviceScale = (state.scale == 1) ? "F" : "C"
		return (deviceScale == scale) ? temp :
				(deviceScale == "F" ? celsiusToFahrenheit(temp).toDouble().round(0).toInteger() : roundC(fahrenheitToCelsius(temp)))
	}
	return 0
}

def roundC (tempC) {
	return (Math.round(tempC.toDouble() * 2))/2
}

/**
 *  sendCommands(cmds, delay=200)
 *
 *  Sends a list of commands directly to the device using sendHubCommand.
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private sendCommands(cmds, delay=200) {
    sendHubCommand( cmds.collect{ (it instanceof physicalgraph.zwave.Command ) ? response(encapCommand(it)) : response(it) }, delay)
}

/**
 *  encapCommand(cmd)
 *
 *  Applies security or CRC16 encapsulation to a command as needed.
 *  Returns a physicalgraph.zwave.Command.
 **/
private encapCommand(physicalgraph.zwave.Command cmd) {
    if (state.useSecurity) {
        return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd)
    }
    else if (state.useCrc16) {
        return zwave.crc16EncapV1.crc16Encap().encapsulate(cmd)
    }
    else {
        return cmd
    }
}

/**
 *  logger()
 *
 *  Wrapper function for all logging. Simplified for this device handler.
 **/
private logger(msg, level = "debug") {

    switch(level) {
        case "error":
            if (state.loggingLevelIDE >= 1) log.error msg
            break

        case "warn":
            if (state.loggingLevelIDE >= 2) log.warn msg
            break

        case "info":
            if (state.loggingLevelIDE >= 3) log.info msg
            break

        case "debug":
            if (state.loggingLevelIDE >= 4) log.debug msg
            break

        case "trace":
            if (state.loggingLevelIDE >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}

/* 
 *  Device preferences (taken from the documentation)
 **/
private static getPreferenceOptions() {

    def options = [
    /* Don't show or allow user to change. We update LED in the DH to indicate relay position.
            1: [
                    required: false,
                    size: 1,
                    type: "enum",
                    defaultValue: 1,
                    defaultDescription: "LED turned on",
                    options: [
                            0: "0: LED turned off",
                            1: "1: LED turned on (Default)",
                            2: "2: LED flashing at 1 second intervals (½ Hz).",
                            3: "3: LED flashing at ½ second interval (1 Hz).",
                         
                    ],
                    name: "Configuration of the status LED",
                    description: "This parameter changes the operation of the status LED."
            ],
            */
            2: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 50,
                    defaultDescription: "50",
                    range: "0..100",
                    name: "Status LED brightness level",
                    description: "Specifies the brightness level of the LED when it is on"
            ],
            3: [
                    required: false,
                    size: 1,
                    type: "enum",
                    defaultValue: 1,
                    defaultDescription: "10K NTC",
                    options: [
                            0: "0: Input disabled",
                            1: "1: 10K NTC (TEWA PART NUMBER: TT02-10KC3-93D-3000R-TPH).",
                         
                    ],
                    name: "Thermistor type for input 1.",
                    description: "Configures the thermistor type connected to input 1."
            ],
            4: [
                    required: false,
                    size: 1,
                    type: "enum",
                    defaultValue: 1,
                    defaultDescription: "10K NTC",
                    options: [
                            0: "0: Input disabled",
                            1: "1: 10K NTC (TEWA PART NUMBER: TT02-10KC3-93D-3000R-TPH).",
                         
                    ],
                    name: "Thermistor type for input 2.",
                    description: "Configures the thermistor type connected to input 2."
            ],
            5: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 0,
                    defaultDescription: "0",
                    range: "-40..40",
                    name: "Temperature offset on input 1.",
                    description: "Configures a temperature offset that can be added to the measured temperature in order to get a more accurate measurement from the thermistor on input 1."
            ],
            6: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 20,
                    defaultDescription: "5 = 0.5°C",
                    range: "3..50",
                    name: "Temperature hysteresis. 0.3 - 5.0°C",
                    description: "Configures temperature hysteresis that are used around the temperature setpoints."
            ],
            7: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 0,
                    defaultDescription: "0",
                    range: "-40..40",
                    name: "Temperature offset on input 2.",
                    description: "Configures a temperature offset that can be added to the measured temperature in order to get a more accurate measurement from the thermistor on input 2."
            ],
            8: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 0,
                    defaultDescription: "20°C",
                    range: "-15..128",
                    name: "Temperature setpoint for input 1.",
                    description: "Configures a temperature setpoint for input 1."
            ],
            9: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 0,
                    defaultDescription: "20°C",
                    range: "-15..128",
                    name: "Temperature setpoint for input 2.",
                    description: "Configures a temperature setpoint for input 2."
            ],
            10: [
                    required: false,
                    size: 1,
                    type: "enum",
                    defaultValue: 1,
                    defaultDescription: "limit control is enabled",
                    options: [
                            0: "0: Input 2 thermostat limit control is disabled",
                            1: "1: Input 2 thermostat limit control is enabled. (Default).",
                         
                    ],
                    name: "Use input 2 as a thermostat limiter.",
                    description: "Configures that input 2 is uses as thermostat limiter for the relay control, that when the temperature on input 2 is above the setpoint for input 2, then the thermostat control for input 1 is disabled."
            ],
         
            11: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 129,
                    defaultDescription: "129 = 2 minutes",
                    range: "0..255",
                    name: "Time interval for temperature reports.",
                    description: "Configures the time interval between when temperature sensor reports are transmitted. 0 – 127 seconds, 1 – 128 minutes. Value – 127 = minutes. (Default is 129 = 2 minutes)."
            ],
            12: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 132,
                    defaultDescription: "132 = 5 minutes",
                    range: "0..255",
                    name: "Time interval between notification reports for flood input 3.",
                    description: "Configures the time interval between when notification reports for flood input 3. 0 – 127 seconds, 1 – 128 minutes. Value – 127 = minutes. (Default is 132 = 5 minutes)."
            ],
            13: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 142,
                    defaultDescription: "142 = 15 minutes",
                    range: "0..255",
                    name: "Time interval between meter reports.",
                    description: "Configures the time interval between when meter reports for reporting the energy (kWh) consumed by the load connected to the relay output. 0 – 127 seconds, 1 – 128 minutes. Value – 127 = minutes. (Default is 142 = 15 minutes)."
            ],
            14: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 6,
                    defaultDescription: "Default value is 6 (6 seconds) before a state is accepted as valid",
                    range: "0..60",
                    name: "Flood steady timer.",
                    description: "Configures the time that the flood input (input 3) must be steady before the state is accepted as a valid state."
            ],
            15: [
                    required: false,
                    size: 2,
                    type: "number",
                    defaultValue: 2048,
                    defaultDescription: "Default value is 2048",
                    range: "0..4095",
                    name: "Flood detection threshold.",
                    description: "Configures the threshold for input 3 that will cause a flood to be detected. Low value means a low detection threshold, high value will cause the input to be less sensitive"
            ],
            16: [
                    required: false,
                    size: 1,
                    type: "number",
                    defaultValue: 220,
                    defaultDescription: "220 Volt",
                    range: "0..250",
                    name: "Voltage.",
                    description: "Configures the value used for power calculation, as only the current for the load on the relay output is measured."
            ],
            19: [
                    required: false,
                    size: 2,
                    type: "number",
                    defaultValue: 0,
                    defaultDescription: "Default value is 0",
                    range: "0..6000",
                    name: "Size of load connected on the relay output.",
                    description: "Configures a constant value that will be used in power metering when this value is different from 0. This value specifies the actual load in Watt used for power metering."
            ],
            ]
            }
            
/*
 * Application options
 */
private static getAppConfigurationOptions() {

    def options = [

            "temp_1": [
                    required: false,
                    type: "bool",
                    title: "Floor sensor 1",
                    description: "Floor temperature sensor on input 1"
            ],
            "temp_2": [
                    required: false,
                    type: "bool",
                    title: "Floor sensor 2",
                    description: "Floor temperature sensor on input 2"
            ],
            "flood": [
                    required: false,
                    type: "bool",
                    title: "Flood sensor",
                    description: "Flood sensor on input 3"
            ],
            "loggingLevelIDE": [
                title: "IDE Live Logging Level",
                description :"Messages with this level and higher will be logged to the IDE.",
                type: "enum",
                options: [
                    3 : "Info",
                    4 : "Debug",
                    5 : "Trace"
                ],
                defaultValue: 3,
                required: false
            ]
          ]
}
