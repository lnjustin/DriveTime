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
 * V1.2.0 - Add distanceStr, units preference, setMode command, map
 * V1.3.0 - Add start navigation link
 * V1.4.0 - Add stop navigation link
 */

 import java.text.SimpleDateFormat 
 import java.net.URLEncoder

metadata
{
    definition(name: "DriveTime", namespace: "lnjustin", author: "Justin Leonard", importUrl: "")
    {
        capability "Actuator"
        capability "PushableButton"
        attribute "duration", "number" // seconds
        attribute "durationStr", "string" // hh:mm
        attribute "route", "string"
        attribute "trafficDelay", "number" // seconds
        attribute "trafficDelayStr", "string" // hh:mm
        attribute "distance", "number"
        attribute "distanceStr", "number"
        attribute "map", "string"
        attribute "navigateURL", "string"
        attribute "startNavigationLink", "string"
        attribute "stopNavigateURL", "string"
        attribute "stopNavigationLink", "string"

        attribute "lastUpdate", "string"
        attribute "lastUpdateStr", "string"

        command "go",[[name:"Origin*",type:"STRING", description:"Origin Address"],
			[name:"Destination*",type:"STRING", description:"Destination Address"]]
        
        command "configure"
        command "setMode",[[name:"Mode*",type:"ENUM", description:"Mode of Transportation", constraints: ["driving", "walking", "bicycling", "transit"]]]
        command "setTransitMode",[[name:"TransitMode*",type:"ENUM", description:"Mode of Transit", constraints: ["bus", "subway", "train", "tram", "rail"]]]
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
        input name: "units", type: "enum", title: "Units", options: ["metric", "imperial"], required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging"
    }
} 

void configure() {
    installed()
}

void installed() {
    def navigateURLValue = "https://www.google.com/maps/dir/?api=1&dir_action=navigate"
    navigateURLValue += "&origin=" + URLEncoder.encode("0.0,0.0","UTF-8")
    navigateURLValue += "&destination=" + URLEncoder.encode("0.0,0.0","UTF-8")
    navigateURLValue += "&travelmode=" + (mode != null ? mode : "driving")
    sendEvent(name: "stopNavigateURL", value: navigateURLValue)
    
    def navigateStop = "<a href='" + navigateURLValue + "'>Stop Navigation</a>"
    sendEvent(name: "stopNavigationLink", value: navigateStop)
}

def push(buttonNumber) {
    if (buttonNumber <= 4) {
        def origin = settings["origin_address${buttonNumber}"]
        def destination = settings["destination_address${buttonNumber}"]
        go(origin, destination)
    }
}

def go(origin, destination) {
    def subUrl = "directions/json?origin=${origin}&destination=${destination}&key=${api_key}&alternatives=true&departure_time=now"
    subUrl += mode != null ? "&mode=${mode}" : "&mode=driving"
    subUrl += (mode == "transit" && transitMode != null) ? "&transit_mode=${transitMode}" : ""
    subUrl += units != null ? "&units=${units}" : "&units=metric" // default to metric

    def response = httpGetExec(subUrl)
    if (response) {
        state.routes = [:]
        def routes = response.routes
        logDebug("Found routes: ${routes}")
        if (routes[0]){
            def route = routes[0]
            def summary = route.summary
            def duration = 0
            def durationStr = ""
            def trafficDelay = ""
            if (mode == "driving") {
                duration = route.legs[0].duration_in_traffic?.value
                durationStr = route.legs[0].duration_in_traffic?.text
                trafficDelay = Math.max(0,(route.legs[0].duration_in_traffic?.value - route.legs[0].duration?.value))
            }
            else {
                duration = route.legs[0].duration?.value
                durationStr = route.legs[0].duration?.text
                trafficDelay = 0
            }
            def distance = route.legs[0].distance.value // always returned from Google in meters, irrespective of units parameter passed to Google
            def distanceStr = route.legs[0].distance.text // returned from Google according to units parameter
            if (units == "imperial") distance = ((double) (distance * 0.00062137)).round(2) // convert meters to miles

            sendEvent(name: "route", value: summary)
            sendEvent(name: "duration", value: duration)
            sendEvent(name: "durationStr", value: durationStr)
            sendEvent(name: "trafficDelay", value: trafficDelay)
            sendEvent(name: "trafficDelayStr", value: formatTime(trafficDelay))
            sendEvent(name: "distance", value: distance)
            sendEvent(name: "distanceStr", value: distanceStr)

            def mapSource = "https://www.google.com/maps/embed/v1/directions?key=${api_key}"
            mapSource += "&origin=" + URLEncoder.encode(origin,"UTF-8")
            mapSource += "&destination=" + URLEncoder.encode(destination,"UTF-8")
            mapSource += "&mode=" + (mode != null ? mode : "driving")
            if (units != null) mapSource += "&units=" + units
            def mapContent = "<div id='${timeUpdated}' style='height: 100%; width: 100%'><iframe src='${mapSource}' allowfullscreen style='height: 100%; width:100%; border: none;' referrerpolicy='no-referrer-when-downgrade'></iframe><div>"
            // timeUpdated as id guards against caching preventing update
            sendEvent(name: "map", value: mapContent)
            
            def navigateURLValue = "https://www.google.com/maps/dir/?api=1&dir_action=navigate"
            navigateURLValue += "&origin=" + URLEncoder.encode(origin,"UTF-8")
            navigateURLValue += "&destination=" + URLEncoder.encode(destination,"UTF-8")
            navigateURLValue += "&travelmode=" + (mode != null ? mode : "driving")
            sendEvent(name: "navigateURL", value: navigateURLValue)
            
            def navigateStart = "<a href='" + navigateURLValue + "'>Start Navigation</a>"
            sendEvent(name: "startNavigationLink", value: navigateStart)

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

def setMode(modeOfTransportation) {
    device.updateSetting("mode",[value:modeOfTransportation, type:"enum"])
}

def setTransitMode(modeOfTransit) {
    device.updateSetting("transitMode",[value:modeOfTransit, type:"enum"])
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
