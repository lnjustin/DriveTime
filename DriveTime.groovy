/**
 * DriveTime - Powered by Google Traffic
 * Copyright 2024 lnjustin
 * 
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 * v0.1.0 - initial beta
 * v0.1.0 - revised to be stateless
 * v0.1.1 - PushableButton
 * v1.0.0 - Fix trafficDelayStr type
 * v1.1.0 - Add Go Command, Mode of Transportation
 */

 import java.text.SimpleDateFormat 

metadata
{
    definition(name: "DriveTime", namespace: "lnjustin", author: "Justin Leonard", importUrl: "")
    {
        capability "Actuator"
        capability "PushableButton"
        attribute "duration", "number" // second
        attribute "durationStr", "string" // hh:mm
        attribute "route", "string"
        attribute "trafficDelay", "number" // seconds
        attribute "trafficDelayStr", "string" // hh:mm
        attribute "distance", "number"
        attribute "lastUpdate", "string"
        attribute "lastUpdateStr", "string"

        command "go",[[name:"Origin*",type:"STRING", description:"Origin Address"],
			[name:"Destination*",type:"STRING", description:"Destination Address"]]

    }
}

preferences
{
    section
    {
       // href(name: "GoogleApiLink", title: "Get Google API Key", required: false, url: "https://developers.google.com/maps/documentation/directions/get-api-key", style: "external")
        input name: "api_key", type: "text", title: "Enter Google API key", required: true
        input name: "origin_address1", type: "text", title: "Origin Address 1", required: false
        input name: "destination_address1", type: "text", title: "Destination Address 1", required: false
        input name: "origin_address2", type: "text", title: "Origin Address 2", required: false
        input name: "destination_address2", type: "text", title: "Destination Address 2", required: false
        input name: "origin_address3", type: "text", title: "Origin Address 3", required: false
        input name: "destination_address3", type: "text", title: "Destination Address 3", required: false
        input name: "origin_address4", type: "text", title: "Origin Address 4", required: false
        input name: "destination_address4", type: "text", title: "Destination Address 4", required: false
        input name: "mode", type: "enum", title: "Mode of Transportation", options: ["driving", "walking", "bicycling", "transit"], default: "driving", required: false
        if (settings?.mode && settings?.mode == "transit") input name: "transitMode", type: "enum", title: "Transit Type", options: ["bus", "subway", "train", "tram", "rail"], default: "bus", required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging"
    }
} 

def push(buttonNumber) {
    if (buttonNumber <= 4) {
        def origin = settings["origin_address${buttonNumber}"]
        def destination = settings["destination_address${buttonNumber}"]
        go(origin, destination)
    }
}

def go(origin, destination) {
    def subUrl = "directions/json?origin=${origin}&destination=${destination}&key=${api_key}&alternatives=true&departure_time=now" + (mode != null ? "&mode=${mode}" : "&mode=driving") + (mode == "transit" ? "&transit_mode=${transitMode}" : "")  
    def response = httpGetExec(subUrl)
    if (response) {
        state.routes = [:]
        def routes = response.routes
        logDebug("Found routes: ${routes}")
        if (routes[0]){
            def route = routes[0]
            def summary = route.summary
            def duration = ""
            def trafficDelay = ""
            if (mode == "driving") {
                duration = route.legs[0].duration_in_traffic?.value
                trafficDelay = Math.max(0,(route.legs[0].duration_in_traffic?.value - route.legs[0].duration?.value))
            }
            else {
                duration = route.legs[0].duration?.value
                trafficDelay = 0
            }
            def distance = route.legs[0].distance.text

            sendEvent(name: "route", value: summary)
            sendEvent(name: "duration", value: duration)
            sendEvent(name: "durationStr", value: formatTime(duration))
            sendEvent(name: "trafficDelay", value: trafficDelay)
            sendEvent(name: "trafficDelayStr", value: formatTime(trafficDelay))
            sendEvent(name: "distance", value: distance)

            def timeUpdated = now()
            sendEvent(name: "lastUpdate", value: timeUpdated)
            sendEvent(name: "lastUpdateStr", value: epochToDt(timeUpdated))
        }
    }
    else log.warn "No response from Google Traffic API. Check connection."
}

def httpGetExec(subUrl)
{
    try
    {
        getString = "https://maps.googleapis.com/maps/api/" + subUrl
        logDebug("Drivetime command: ${getString}")
        httpGet(getString.replaceAll(' ', '%20'))
        { resp ->
            if (resp.data)
            {
                return resp.data
            }
            else {
                log.warn "No response from Google Traffic API. Check connection."
            }
        }
    }
    catch (Exception e)
    {
        log.warn "httpGetExec() failed: ${e.message}"
    }
}

def formatTime(duration) {
    def hours = (duration / 3600).intValue()
    def mins = ((duration % 3600) / 60).intValue()
    def hStr = ""
    def space = ""
    def mStr = ""
    if (hours > 0) {
        if (hours > 1) hStr = hours + " hrs"
        else hStr = hours + " hr"
    }
    if (mins > 0) {
        if (hStr != "") space = " "
        if (mins > 1) mStr = mins + " mins"
        else mStr = mins + " min"
    }
    if (hStr == "" && mStr == "") mStr = "0 mins"
    return hStr + space + mStr
}

def epochToDt(val) {
    def dt = new Date(val)
    def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
    return tf.format(dt)
}

def logDebug(msg) 
{
    if (logEnable)
    {
        log.debug(msg)
    }
}   
