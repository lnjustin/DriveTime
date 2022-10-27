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

 */

metadata
{
    definition(name: "DriveTime", namespace: "lnjustin", author: "Justin Leonard", importUrl: "")
    {
        capability "Actuator"
        capability "Momentary"
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
        input name: "origin_address", type: "text", title: "Origin Address", required: true
        input name: "destination_address", type: "text", title: "Destination Address", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging"
    }
}

def logDebug(msg) 
{
    if (logEnable)
    {
        log.debug(msg)
    }
}    

def push() {
    logDebug("DriveTime Pushed")
    checkDrive()
}

def checkDrive() {
     def subUrl = "directions/json?origin=${origin_address}&destination=${destination_address}&key=${api_key}&alternatives=true&mode=driving&departure_time=now"   
     def response = httpGetExec(subUrl)
     if (response) {
         state.routes = [:]
         def routes = response.routes
         for (Integer i=0; i<routes.size(); i++) {
             def route = routes[i]
             def summary = route.summary
             def duration = route.legs[0].duration_in_traffic?.value
             def trafficDelay = (route.legs[0].duration_in_traffic?.value - route.legs[0].duration?.value)
             def distance = route.legs[0].distance.text
             state.routes[i.toString()] = [summary: summary, duration: duration, trafficDelay: trafficDelay, distance: distance]
         }
         state.routesAsOf = new Date().getTime()
         sendDriveEvents()
    }
     else {
         log.warn "No response from Google Traffic API. Check connection."
     }
}

def sendDriveEvents() {    
    if (state.routes['0'] != null) {
        logDebug("Updating DriveTime")
        sendEvent(name: "route", value: state.routes['0'].summary)
        sendEvent(name: "duration", value: state.routes['0'].duration)
        sendEvent(name: "durationStr", value: formatTime(state.routes['0'].duration))
        sendEvent(name: "trafficDelay", value: state.routes['0'].trafficDelay)
        sendEvent(name: "trafficDelayStr", value: formatTime(state.routes['0'].trafficDelay))
        sendEvent(name: "distance", value: state.routes['0'].distance)
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
    return hStr + mStr
}
