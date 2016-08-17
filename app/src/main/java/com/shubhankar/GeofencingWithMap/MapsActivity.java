package com.shubhankar.GeofencingWithMap;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.joda.time.DateTime;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback
        , GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener/*, ResultCallback<Status>*/ {

    private GoogleMap mMap;
    String TAG = "GeoFenceApp";

    protected GoogleApiClient mGoogleApiClient;
    protected ArrayList<Geofence> mGeofenceList;
    private PendingIntent mGeofencePendingIntent;
    LatLngBounds.Builder boundsBuilder;
    MarkerOptions fenceMarkerOptions;
    CircleOptions fenceCircleOptions;
    Marker newFenceMarker;
    Circle newFenceRange;
    HashSet fenceNames;
    List<GeoFence> storedFences;
    Set<String> fenceNameList = new HashSet<>();
    List<Marker> markersOnMap = new ArrayList<>();
    List<Circle> circlesOnMap = new ArrayList<>();

    Boolean clientConnected = false;
    SharedPreferences mSharedPref;
    Boolean firstTime = true;
    Location myLocation;
    Button clear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        clear = (Button) findViewById(R.id.clear_fences);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeAllFences();
            }
        });
        //build client
        buildGoogleApiClient();

        //get shared preferences
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<Geofence>();

        // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null.
        mGeofencePendingIntent = null;

        String fences = mSharedPref.getString(Constants.FENCE_KEY, null);
        if (fences != null) {
            Type listType = new TypeToken<ArrayList<GeoFence>>() {
            }.getType();
            storedFences = new Gson().fromJson(fences, listType);
        } else {
            storedFences = new ArrayList<>();
        }

        //format fence circle
        if (fenceCircleOptions == null) {
            fenceCircleOptions = new CircleOptions()
                    .radius(Constants.GEOFENCE_RADIUS_IN_METERS)
                    .visible(true)
                    .fillColor(ContextCompat.getColor(getApplicationContext(), R.color.fenceCircle))
                    .strokeColor(ContextCompat.getColor(getApplicationContext(), R.color.fenceCircleStroke))
                    .strokeWidth(1f);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
//        mMap.setPadding(50, 50, 5, 30);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location location) {
                    myLocation = location;
                    if (firstTime) {
//                        Log.d(TAG, "New Location:" + location);
                        if (storedFences == null || storedFences.isEmpty())
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15f));
                        firstTime = false;
                    }
                }
            });
            mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                @Override
                public boolean onMyLocationButtonClick() {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()), 15f));
                    return false;
                }
            });
        }

        //create bounds builder
        boundsBuilder = new LatLngBounds.Builder();

        //populate fences on the map
        if (storedFences != null && storedFences.size() > 0) {
            for (GeoFence item : storedFences) {
                Log.d(TAG, "Got stored fence :" + item.getName());
                if (item.getExpiresAt() != null && System.currentTimeMillis() > item.expiresAt) {
                    //if fence has expired, remove it from local storage
                    storedFences.remove(item);
                    mSharedPref.edit().putString(Constants.FENCE_KEY, new Gson().toJson(storedFences)).apply();
                } else {
                    fenceNameList.add(item.getName());
                }
            }
            setupMapMarkers(storedFences, true, true);
        } else {
            Log.d(TAG, "No stored fences found");
        }

        //setup functionality for adding a fence
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(final LatLng latLng) {
                final GeoFence fence = new GeoFence("", latLng.latitude, latLng.longitude);

                //initialize the temp marker
                if (fenceMarkerOptions == null) {
                    fenceMarkerOptions = new MarkerOptions().position(latLng);
                } else {
                    fenceMarkerOptions.position(latLng);
                }

                //initialize its range
                if (fenceCircleOptions != null) {
                    fenceCircleOptions.center(latLng);
                    fenceCircleOptions.radius(Constants.GEOFENCE_RADIUS_IN_METERS);

                }

                //remove all other fence markers
                if (newFenceMarker != null) {
                    newFenceMarker.remove();
                }

                //remove circle indicating range
                if (newFenceRange != null) {
                    newFenceRange.remove();
                }

                // drop a marker here
                newFenceMarker = mMap.addMarker(fenceMarkerOptions);
                newFenceRange = mMap.addCircle(fenceCircleOptions);

                //animate UI to adjust to new marker
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f), new GoogleMap.CancelableCallback() {

                    //call back for when animation of  a new marker finishes in order to show the dialog
                    @Override
                    public void onFinish() {

                        //show a popup to enter the name of this geofence
                        LayoutInflater li = LayoutInflater.from(MapsActivity.this);
                        View custom = li.inflate(R.layout.fence_dialog, null);
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MapsActivity.this);
                        alertDialogBuilder.setView(custom);
                        alertDialogBuilder.setCancelable(false);
                        final EditText placeName = (EditText) custom.findViewById(R.id.fence_name);
                        TextView cancel = (TextView) custom.findViewById(R.id.cancel_fence_name);
                        TextView confirm = (TextView) custom.findViewById(R.id.confirm_fence_name);
                        final SeekBar fenceRadius = (SeekBar) custom.findViewById(R.id.fence_radius);
                        final TextView displayRadius = (TextView) custom.findViewById(R.id.fence_radius_value);
                        final float[] values = {150, 250, 500, 750, 1000};
                        final AlertDialog alertDialog = alertDialogBuilder.create();

                        fenceRadius.setProgress(0);
                        fenceRadius.setMax(values.length - 1);
                        fenceRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                Log.d("Seekbar value", "" + progress);
                                displayRadius.setText(Math.round(values[progress]) + " mtrs");
                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }
                        });

                        //set actions on the dialog
                        cancel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (newFenceMarker != null)
                                    newFenceMarker.remove();
                                if (newFenceRange != null)
                                    newFenceRange.remove();
                                alertDialog.cancel();
                            }
                        });

                        confirm.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String fenceName = placeName.getText().toString();
                                if (!TextUtils.isEmpty(fenceName)) {
                                    if (!fenceNameList.contains(fenceName)) {
                                        fence.setName(fenceName);
                                        fence.setRadius(values[fenceRadius.getProgress()]);
                                        addMarker(fence, true);
                                        if (newFenceMarker != null)
                                            newFenceMarker.remove();

                                        if (newFenceRange != null)
                                            newFenceRange.remove();
                                        setupGeoFence(fence);
                                        alertDialog.cancel();
                                    } else {
                                        Toast.makeText(getApplicationContext(), "A fence with this name already exists", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(getApplicationContext(), "Please select a valid name", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        alertDialog.show();
                    }

                    @Override
                    public void onCancel() {

                    }
                });
            }
        });
    }

    //method to set up mapMarkers
    public void setupMapMarkers(List<GeoFence> fences, boolean addSelf, boolean showCircle) {
        boundsBuilder = new LatLngBounds.Builder();
        if (addSelf && myLocation != null) {
            boundsBuilder.include(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        }
        for (GeoFence geofence : fences) {
            boundsBuilder.include(geofence.getLatLng());
            markersOnMap.add(mMap.addMarker(new MarkerOptions().position(geofence.getLatLng()).title(geofence.getName())));
            if (showCircle && geofence.getRadius() != null && geofence.getRadius() > 0) {
                fenceCircleOptions.radius(geofence.getRadius());
                fenceCircleOptions.center(geofence.getLatLng());
                circlesOnMap.add(mMap.addCircle(fenceCircleOptions));
            }
        }
        mMap.stopAnimation();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150));
    }

    //add marker to location
    public void addMarker(GeoFence fence, boolean showCircle) {
        if (boundsBuilder != null) {
            boundsBuilder.include(fence.getLatLng());
            markersOnMap.add(mMap.addMarker(new MarkerOptions().position(fence.getLatLng()).title(fence.getName())));
            if (showCircle && fence.getRadius() != null && fence.getRadius() > 0) {
                fenceCircleOptions.center(fence.getLatLng());
                fenceCircleOptions.radius(fence.getRadius());
                circlesOnMap.add(mMap.addCircle(fenceCircleOptions));
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150));
        }
    }

    //method to remove all fences
    private void removeAllFences() {
        if (storedFences == null || storedFences.isEmpty()) {
            Toast.makeText(getApplicationContext(), "No fences to remove!", Toast.LENGTH_LONG).show();
        } else {
            if (!mGoogleApiClient.isConnected()) {
                Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
                return;
            }
            unregisterFences(new ArrayList<>(fenceNameList));
            if (!markersOnMap.isEmpty()) {
                for (Marker marker : markersOnMap) {
                    marker.remove();
                }
                markersOnMap.clear();
            }

            if (!circlesOnMap.isEmpty()) {
                for (Circle circle : circlesOnMap) {
                    circle.remove();
                }
                circlesOnMap.clear();
            }
        }
    }

    public void unregisterFences(final List<String> fencesToRemove) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Remove geofences.
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    fencesToRemove
            ).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    if (status.isSuccess()) {
                        mSharedPref.edit().remove(Constants.FENCE_KEY).apply();
                        Toast.makeText(getApplicationContext(), "GeoFences Removed", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMessage = GeofenceErrorMessages.getErrorString(getApplicationContext(),
                                status.getStatusCode());
                        Log.e(TAG, errorMessage);
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (SecurityException securityException) {
            Log.d(TAG, "FINE_LOCATION_PERMISSION NEEDED!");
        }
    }

    //add a fence
    public void setupGeoFence(GeoFence fence) {
        mGeofenceList = new ArrayList<>();
        mGeofenceList.add(new Geofence.Builder()
                .setRequestId(fence.getName())
                .setCircularRegion(
                        fence.getLat(),
                        fence.getLon(),
                        Constants.GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(Constants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT)
                .build());
        addGeoFence(fence);
    }

    //add this fence to api client
    public void addGeoFence(final GeoFence fence) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    if (status.isSuccess()) {
                        Toast.makeText(getApplicationContext(), "Geofence added at " + fence.getName(), Toast.LENGTH_SHORT).show();
                        fence.setExpiresAt(new DateTime().plus(Constants.GEOFENCE_EXPIRATION_IN_MILLISECONDS).getMillis());
                        saveNewFence(fence);
                    } else {
                        String errorMessage = GeofenceErrorMessages.getErrorString(getApplicationContext(),
                                status.getStatusCode());
                        Log.e(TAG, errorMessage);
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (SecurityException securityException) {
            Log.d(TAG, "FINE_LOCATION_PERMISSION NEEDED!");
        }
    }

    //save fence to shared preference
    public void saveNewFence(GeoFence fence) {
        if (!fenceNameList.contains(fence.getName())) {
            fenceNameList.add(fence.getName());
            storedFences.add(fence);
            mSharedPref.edit().putString(Constants.FENCE_KEY, new Gson().toJson(storedFences)).apply();
        }
    }

    //building the googleapiclient
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");
        clientConnected = true;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
        clientConnected = false;
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "Connection suspended");
        clientConnected = false;
    }

    //get a pending intent if already created or a new one if null
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        return PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //makes a geofence request to add all the geofences mentioned in the geofencelist
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();

        // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
        // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
        // is already inside that geofence.
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);

        // Add the geofences to be monitored by geofencing service.
        builder.addGeofences(mGeofenceList);

        Log.d(TAG, "Adding fences " + Arrays.toString(mGeofenceList.toArray()));

        // Return a GeofencingRequest.
        return builder.build();
    }

    //method to add member to geo fence list
    public void setupGeoFences(LatLng position, String name) {
        //comment out this line to add bulk locations, this will make sure only one is added at a time
        mGeofenceList = new ArrayList<>();

        mGeofenceList.add(new Geofence.Builder()
                // Set the request ID of the geofence. (name of geofence)
                .setRequestId(name)
                // Set the circular region of this geofence.
                .setCircularRegion(
                        position.latitude,
                        position.longitude,
                        Constants.GEOFENCE_RADIUS_IN_METERS
                )
                // Set the expiration duration of the geofence.
                .setExpirationDuration(Constants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                // Set the transition types of interest. Alerts are only generated for these transition.
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT)
//                .setLoiteringDelay(60 * 1000) //set this if if type dwell is used
                // Create the geofence.
                .build());

        //display a permanent marker for fence
        mMap.addMarker(new MarkerOptions().position(position).title(name));

        //call addGeoFences finally to add them to locationservices
        addGeofences(name, position);
    }

    //method to add geofences
    public void addGeofences(final String newFenceName, final LatLng newFenceLatLng) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    // The GeofenceRequest object.
                    getGeofencingRequest(),
                    // A pending intent that that is reused when calling removeGeofences(). This
                    // pending intent is used to generate an intent when a matched geofence
                    // transition is observed.
                    getGeofencePendingIntent()
            ).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    if (status.isSuccess()) {
                        Toast.makeText(getApplicationContext(), "GeoFences Added", Toast.LENGTH_SHORT).show();
                        //now add the new geofence to shared pref
                        addFenceToPref(newFenceLatLng, newFenceName);
                    } else {
                        String errorMessage = GeofenceErrorMessages.getErrorString(getApplicationContext(),
                                status.getStatusCode());
                        Log.e(TAG, errorMessage);
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            }); // Result processed in onResult().
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.d(TAG, "FINE_LOCATION_PERMISSION NEEDED!");
        }
    }

    //method to remove geofences
    public void removeGeofences(final List<String> fencesToRemove) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Remove geofences.
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    //we will remove geofences by their requestid's (names)
                    fencesToRemove
            ).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    if (status.isSuccess()) {
                        Toast.makeText(getApplicationContext(), "GeoFences Removed", Toast.LENGTH_SHORT).show();
                        //now remove them all from preference
                        for (String fence : fencesToRemove) {
                            deleteFenceFromPref(fence);
                        }
                    } else {
                        String errorMessage = GeofenceErrorMessages.getErrorString(getApplicationContext(),
                                status.getStatusCode());
                        Log.e(TAG, errorMessage);
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            }); // Result processed in onResult().
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.d(TAG, "FINE_LOCATION_PERMISSION NEEDED!");
        }
    }

    //this is the result of adding geofences
//    public void onResult(Status status) {
//        if (status.isSuccess()) {
//            Toast.makeText(this, "GeoFences Added", Toast.LENGTH_SHORT).show();
//        } else {
//            String errorMessage = GeofenceErrorMessages.getErrorString(this,
//                    status.getStatusCode());
//            Log.e(TAG, errorMessage);
//            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
//        }
//    }

    public LatLng getFenceLatLng(String fenceName) {
        double lat = (double) mSharedPref.getFloat(fenceName + "_lat", 0);
        double lon = (double) mSharedPref.getFloat(fenceName + "_long", 0);
        return new LatLng(lat, lon);
    }

    public void deleteFenceFromPref(String name) {
        if (fenceNames.isEmpty()) {
            //no fences stored to delete
            Log.d(TAG, "No fences to delete.");
            return;
        }
        if (fenceNames.contains(name)) {
            fenceNames.remove(name);
            mSharedPref.edit()
                    .putStringSet("fences", fenceNames)
                    .remove(name + "_lat")
                    .remove(name + "_long")
                    .apply();
//            Toast.makeText(getApplicationContext(), "Deleted " + name, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Deleted " + name + " from pref");
        } else {
            Toast.makeText(getApplicationContext(), "Fence does not exist", Toast.LENGTH_SHORT).show();
        }
    }

    public void addFenceToPref(LatLng latLng, String name) {
        //we will overwrite all fences to store
        if (fenceNames != null) {
            if (!fenceNames.contains(name)) {
                Log.d(TAG, "Saving " + name + " to pref");
                fenceNames.add(name);
                mSharedPref.edit().putStringSet("fences", fenceNames).apply();
            }
            mSharedPref.edit()
                    .putFloat(name + "_lat", (float) latLng.latitude)
                    .putFloat(name + "_long", (float) latLng.longitude)
                    .apply();
            Toast.makeText(getApplicationContext(), "Saved fence at " + name, Toast.LENGTH_SHORT).show();
        }
    }

}

