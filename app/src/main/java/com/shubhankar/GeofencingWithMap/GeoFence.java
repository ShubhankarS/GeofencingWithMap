package com.shubhankar.GeofencingWithMap;

import com.google.android.gms.maps.model.LatLng;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Created by shubhankar on 17/8/16.
 */
public class GeoFence {
    @Expose
    @SerializedName("name")
    String name;

    @Expose
    @SerializedName("lat")
    double lat;

    @Expose
    @SerializedName("lon")
    double lon;

    @Expose
    @SerializedName("expiresAt")
    Long expiresAt;

    public GeoFence(String name, double lat, double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public LatLng getLatLng() {
        return new LatLng(this.lat, this.lon);
    }


    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
