/**
 *  Haiku Wall Control Occupancy Sensor Device Driver
 *  
 *  Author: Kevin V.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Change Log:
 *  2020-03-06: Initial Release
 *
 *  Notes: This has been developed to receive two-way communications to detect room occupancy (motion sensor) status from the Haiku fans.
 *  This driver uses periodic socket polling to obtain the Occupancy Sensor information from the wall controllers.
 *  This code is modelled after Adam Kempenich's MagicHome WiFi driver discussed at https://community.hubitat.com/t/release-beta-0-7-magic-home-wifi-devices-initial-public-release/5197.
 */

import hubitat.helper.HexUtils
import hubitat.device.HubAction
import hubitat.helper.InterfaceUtils
import hubitat.device.Protocol

metadata {
    definition(name: "Haiku Wall Control Occupancy Sensor", namespace: "kevinv-hubitat", author: "Kevin Vest",
        importUrl: "TBD") {
    capability "Initialize"
    capability "MotionSensor"
    capability "Polling"
    capability "Refresh"

    command "getOccupancy"

    attribute "occupied", "string"
	}
    
    preferences {  
        input "deviceIP", "text", title: "Wall Control", description: "Device IP (e.g. 192.168.0.X)", required: true, defaultValue: "192.168.0.X"
        input "devicePort", "number", title: "Port", description: "Device Port (Default: 31415)", required: true, defaultValue: 31415
		
        input(name:"logDebug", type:"bool", title: "Log debug information?",
              description: "Logs raw data for debugging. (Default: Off)", defaultValue: false,
              required: true, displayDuringSetup: true)
        input(name:"logDescriptionText", type:"bool", title: "Log descriptionText?",
              description: "Logs when things happen. (Default: On)", defaultValue: true,
              required: true, displayDuringSetup: true)		
         
        
		input(name:"turnOffWhenDisconnected", type:"bool", title: "Turn off when disconnected?",
              description: "When a device is unreachable, turn its state off. in Hubitat", defaultValue: true,
              required: true, displayDuringSetup: true)
		
		input(name:"reconnectPings", type:"number", title: "Reconnect after ...",
            description: "Number of failed pings before reconnecting device.", defaultValue: 3,
            required: true, displayDuringSetup: true)
        
		input(name:"refreshTime", type:"number", title: "Time to refresh (seconds)",
            description: "Interval between refreshing a device for its current value. Default: 10. Use number between 0-60", defaultValue: 10,
            required: true, displayDuringSetup: true)
    }
}
def getOccupancy() {
    // Request Occupancy Status
    logDescriptionText "Requesting Occupancy from ${device.name}"
    
    cmdString = '<'+ device.name + ';SNSROCC;STATUS;GET>'
    
    byte[] data = cmdString.getBytes()
    
    sendCommand(data)
}

// ------------------- Helper Functions ------------------------- //

def limit( value, lowerBound = 0, upperBound = 100 ){
    // Takes a value and ensures it's between two defined thresholds

    value == null ? value = upperBound : null

    if(lowerBound < upperBound){
        if(value < lowerBound ){ value = lowerBound}
        if(value > upperBound){ value = upperBound}
    }
    else if(upperBound < lowerBound){
        if(value < upperBound){ value = upperBound}
        if(value > lowerBound ){ value = lowerBound}
    }

    return value
}

def parse( response ) {
    // Parse data received back from this device
    
    state.noResponse = 0    
    
    def responseArray = HexUtils.hexStringToIntArray(response)  
    switch(responseArray.length) {
        case null:
            logDebug "Null response received from device"
            break;
        default:
            String cmdString = new String(response.decodeHex())
            logDebug( "Received cmdString of ${cmdString}" )
            String regex = /^.*UNOCCUPIED.$/
            boolean match = cmdString.matches(regex)
            if (match) {
                sendEvent(name: "motion", value: "inactive") 
                logDebug( "Sent UNOCCUPIED status" )
            }
            else {
                sendEvent(name: "motion", value: "active")
                logDebug( "Sent OCCUPIED status" )
            }
            break;
    }
}

private logDebug( debugText ){
    // If debugging is enabled in settings, pass text to the logs
    
    if( settings.logDebug ) { 
        log.debug "Haiku Wall Controller (${settings.deviceIP}): ${debugText}"
    }
}

private logDescriptionText( descriptionText ){
    if( settings.logDescriptionText ) { 
        log.info "Haiku Wall Controller (${settings.deviceIP}): ${descriptionText}"
    }
}

def sendCommand( data ) {
    // Sends commands to the device
    
    String stringBytes = HexUtils.byteArrayToHexString(data)
    logDebug "${data} was converted. Transmitting: ${stringBytes}"
    InterfaceUtils.sendSocketMessage(device, stringBytes)
}


def refresh( ) {
	
	logDebug "Number of failed responses: ${state.noResponse}"
	state.noResponse++
    state.noResponse >= limit(settings.reconnectPings, 0, 10) ? ( initialize() ) : null // if a device hasn't responded after N attempts, reconnect
    getOccupancy()
}

def socketStatus( status ) { 
    logDescriptionText "A connection issue occurred."
    logDebug "socketStatus: ${status}"
    logDebug "Attempting to reconnect after ${limit(settings.reconnectPings, 0, 10)-state.noResponse} more failed attempt(s)."
}

def poll() {
    refresh()
}

def updated(){
    initialize()
}

def connectDevice( data ){

    if(data.firstRun){
        logDebug "Stopping refresh loop. Starting connectDevice loop"
        unschedule() // remove the refresh loop
        schedule("0/${limit(settings.refreshTime, 1, 60)} * * * * ? *", connectDevice, [data: [firstRun: false]])
    }
    
    InterfaceUtils.socketClose(device)
    telnetClose()
    
    pauseExecution(1000)
    
    if( data.firstRun || ( now() - state.lastConnectionAttempt) > limit(settings.refreshTime, 1, 60) * 500 /* Breaks infinite loops */ ) {
        def tryWasGood = false
        try {
            logDebug "Opening Socket Connection."
            InterfaceUtils.socketConnect(device, settings.deviceIP, settings.devicePort.toInteger(), byteInterface: true)
            pauseExecution(1000)
            logDescriptionText "Connection successfully established"
            tryWasGood = true
    
        } catch(e) {
            logDebug("Error attempting to establish socket connection to device.")
            logDebug("Next initialization attempt in ${settings.refreshTime} seconds.")
            settings.turnOffWhenDisconnected ? sendEvent(name: "switch", value: "off")  : null
            tryWasGood = false
        }
	    
	    if(tryWasGood){
	    	unschedule()
	    	logDebug "Stopping connectDevice loop. Starting refresh loop"
	    	schedule("0/${limit(settings.refreshTime, 1, 60)} * * * * ? *", refresh)
	    	state.noResponse = 0
	    }
        logDebug "Proper time has passed, or it is the device's first run."
        logDebug "${(now() - state.lastConnectionAttempt)} >= ${limit(settings.refreshTime, 1, 60) * 500}. First run: ${data.firstRun}"
        state.lastConnectionAttempt = now()
    }
    else{
        logDebug "Tried to connect too soon. Skipping this round."
        logDebug "X ${(now() - state.lastConnectionAttempt)} >= ${limit(settings.refreshTime, 1, 60) * 500}"
        state.lastConnectionAttempt = now()
    }
}

def initialize() {
    // Establish a connection to the device
    state.remove("initializeLoopRunning")
    state.remove("refreshRunning")
    state.remove("initializeLoop")
    state.remove("oldvariablename")
    
    logDebug "Initializing device."
    state.lastConnectionAttempt = now()
    connectDevice([firstRun: true])
}


def installed(){
	sendEvent(name: "Wall Control Installed", value: 1)
	state.noResponse = 0
}