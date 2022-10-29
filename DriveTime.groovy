/**
 * DriveTime - Powered by Google Traffic
 * Copyright 2022 Justin Leonard
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
 */

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
        attribute "trafficDelayStr", "number" // hh:mm
        attribute "distance", "number"
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
        input name: "logEnable", type: "bool", title: "Enable debug logging"
    }
} 

def push(buttonNumber) {
    if (buttonNumber <= 4) {
        def origin = settings["origin_address${buttonNumber}"]
        def destination = settings["destination_address${buttonNumber}"]
         def subUrl = "directions/json?origin=${origin}&destination=${destination}&key=${api_key}&alternatives=true&mode=driving&departure_time=now"   
         def response = httpGetExec(subUrl)
         if (response) {
             state.routes = [:]
             def routes = response.routes
             logDebug("Found routes: ${routes}")
             if (routes[0]){
                 def route = routes[0]
                 def summary = route.summary
                 def duration = route.legs[0].duration_in_traffic?.value
                 def trafficDelay = Math.max(0,(route.legs[0].duration_in_traffic?.value - route.legs[0].duration?.value))
                 def distance = route.legs[0].distance.text
                 state.routes = [origin: origin, destination: destination, route: summary, duration: duration, trafficDelay: trafficDelay, distance: distance, timeUpdated: now()]
                sendEvent(name: "route", value: summary)
                sendEvent(name: "duration", value: duration)
                sendEvent(name: "durationStr", value: formatTime(duration))
                sendEvent(name: "trafficDelay", value: trafficDelay)
                sendEvent(name: "trafficDelayStr", value: formatTime(trafficDelay))
                sendEvent(name: "distance", value: distance)
             }
        }
         else {
             log.warn "No response from Google Traffic API. Check connection."
         }
    }
}

def httpGetExec(subUrl)
{
    try
    {
        getString = "https://maps.googleapis.com/maps/api/" + subUrl
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
    if (hours > 0) {
        if (hours > 1) hStr = hours + " hrs"
        else hStr = hours + "hr"
    }
    mStr = ""
    if (mins > 0) {
        if (mins > 1) mStr = mins + " mins"
        else mStr = mins + " min"
    }
    if (hStr == "" && mStr == "") mStr = "0 mins"
    return hStr + mStr
}

def logDebug(msg) 
{
    if (logEnable)
    {
        log.debug(msg)
    }
}   
