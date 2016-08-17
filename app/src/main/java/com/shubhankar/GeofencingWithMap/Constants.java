/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shubhankar.GeofencingWithMap;

/**
 * Constants used in this sample.
 */
public class Constants {
    public static final long GEOFENCE_EXPIRATION_IN_HOURS = 96;

    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS =
            GEOFENCE_EXPIRATION_IN_HOURS * 60 * 60 * 1000;
    public static final float GEOFENCE_RADIUS_IN_METERS = 150;
    public static final String FENCE_KEY = "fences";

//    public static final HashMap<String, LatLng> MY_GEO_FENCES = new HashMap<String, LatLng>();
//
//    static {
//        //Work
//        MY_GEO_FENCES.put("WORK", new LatLng(13.022124, 77.64962));
//
//        // Home
//        MY_GEO_FENCES.put("HOME", new LatLng(12.9718774, 77.6856157));
//    }
}
