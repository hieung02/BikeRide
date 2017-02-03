package com.example.hunterz.bikeactivity3;

import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.motorola.mod.ModDevice;
import com.motorola.mod.ModManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MapsActivity extends FragmentActivity implements LocationListener, OnMapReadyCallback {
    String TAG = "BIKE_APP";
    private GoogleMap mMap;
    private LocationManager locationManager;
    private static final long MIN_TIME = 400;
    private static final float MIN_DISTANCE = 100;
    private TextView speedTV;
    private Button searchB;
    private SupportMapFragment mapFragment;
    private ImageView bikeV;
    private TextView start_messageTV;
    private static final int RAW_PERMISSION_REQUEST_CODE = 100;
    private Personality personality;
    private RawPersonality rpersonality;
    private SensorManager sm;
    protected Sensor accel;
    protected Sensor als;
    protected SensorEventListener alsSel;
    protected SensorEventListener accelSel;
    protected MapsActivity thiz;
    private int currentState = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DISTANCE, this);

        speedTV = (TextView) findViewById(R.id.speed);
        searchB = (Button) findViewById(R.id.search);
        bikeV = (ImageView) findViewById(R.id.bikeView);
        start_messageTV = (TextView) findViewById(R.id.start_message);

        setVisibility(false);

        Log.d(TAG,"onCreate");
        thiz = this;
    }



    protected void setVisibility(boolean visible) {
        if (visible) {
            speedTV.setVisibility(View.VISIBLE);
            searchB.setVisibility(View.VISIBLE);
            mapFragment.getView().setVisibility(View.VISIBLE);
            bikeV.setVisibility(View.INVISIBLE);
            start_messageTV.setVisibility(View.INVISIBLE);
        } else {
            speedTV.setVisibility(View.INVISIBLE);
            searchB.setVisibility(View.INVISIBLE);
            mapFragment.getView().setVisibility(View.INVISIBLE);
            bikeV.setVisibility(View.VISIBLE);
            start_messageTV.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true); //checks for current location
    }

    public void onLocationChanged(Location location){
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng,18);
        mMap.animateCamera(cameraUpdate);
        locationManager.removeUpdates(this);

        Log.d(TAG,"Location works");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }


    /** Handler for events from mod device */
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if(personality != null) {
                switch (msg.what) {
                    case Personality.MSG_MOD_DEVICE:
                        /** Mod attach/detach */
                        ModDevice device = personality.getModDevice();
                        onModDevice(device);
                        break;
                    case Personality.MSG_RAW_DATA:
                        /** Mod raw data */
                        byte[] buff = (byte[]) msg.obj;
                        int length = msg.arg1;
                        onRawData(buff, length);
                        break;
                    case Personality.MSG_RAW_IO_READY:
                        /** Mod RAW I/O ready to use */
                        onRawInterfaceReady();
                        break;
                    case Personality.MSG_RAW_IO_EXCEPTION:
                        /** Mod RAW I/O exception */
                        onIOException();
                        break;
                    case Personality.MSG_RAW_REQUEST_PERMISSION:
                        /** Request grant RAW_PROTOCOL permission */
                        onRequestRawPermission();
                    default:
                        Log.i(Constants.TAG, "MainActivity - Un-handle events: " + msg.what);
                        break;
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        releasePersonality();
    }


    @Override
    public void onPause() {
        super.onPause();
        thiz = this;
        sm.unregisterListener(alsSel);
        sm.unregisterListener(accelSel);
    }

    @Override
    public void onResume() {
        super.onResume();

        SensorEventListener alsSel = new SensorEventListener() {
            @Override //Code will need more development (but, later...)
            //Turn signals - left, right
            public void onSensorChanged(SensorEvent event) {
                float intensity = event.values[0];

                if(intensity < 10) {
                    rpersonality.executeRaw(new byte[]{3});
                }

//                Log.d(TAG,Float.toString(intensity));
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        SensorEventListener accelSel = new SensorEventListener() {
            @Override //Code will need more development (but, later...)
            public void onSensorChanged(SensorEvent event) {
                int localState = -1;
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float threshold = 1.50f;
//                float hlst = -0.5f;

                Log.d(TAG,"accelSel");

                if(z < -7.00f){//Phone faces down -- callForHelp function is invoked.
                    callForHelp();
                    localState = 3;
                    Log.d(TAG,"FaceDown");

                }else if(x > threshold){
                    localState = 1;
                    rpersonality.executeRaw(new byte[]{1});
                    Log.d(TAG,"Right");
                }else if (x < (-1*threshold)){
                    localState = 2;
                    rpersonality.executeRaw(new byte[]{2});
                    Log.d(TAG,"Left");
                }else{
                    localState = 0;
                    rpersonality.executeRaw(new byte[]{0});
                    Log.d(TAG,"Nothing");
                }

                if(localState != currentState) {
                    if (localState == 3) {
                        callForHelp();
                        Log.d(TAG, "FaceDown");
                    } else if (localState == 1) {
                        rpersonality.executeRaw(new byte[]{1});
                        Log.d(TAG, "Right");
                    } else if (localState == 2) {
                        rpersonality.executeRaw(new byte[]{2});
                        Log.d(TAG, "Left");
                    } else {
                        rpersonality.executeRaw(new byte[]{0});
                        Log.d(TAG, "Nothing");
                    }
                    currentState = localState;
                }

            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        als = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        boolean b = sm.registerListener(alsSel, als, 1000000);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        boolean c = sm.registerListener(accelSel, accel, 1000000);

        /** Initial MDK Personality interface */
        initPersonality();
    }

    /** Initial MDK Personality interface */
    private void initPersonality() {
        if (null == personality) {
            personality = new RawPersonality(this, Constants.VID_MDK, Constants.PID_TEMPERATURE);
            rpersonality = (RawPersonality)personality;
            personality.registerListener(handler);
            rpersonality.executeRaw(new byte[]{1});
        }
    }

    /** Clean up MDK Personality interface */
    private void releasePersonality() {
        /** Clean up MDK Personality interface */
        if (null != personality) {
            personality.getRaw().executeRaw(Constants.RAW_CMD_STOP);
            personality.onDestroy();
            personality = null;
        }
    }

    /** Mod device attach/detach */
    public void onModDevice(ModDevice device) {
        if (null != device) {
            if ((device.getVendorId() == Constants.VID_MDK
                    && device.getProductId() == Constants.PID_TEMPERATURE)
                    || device.getVendorId() == Constants.VID_DEVELOPER) {
                    setVisibility(true);
                    Log.d(TAG,"setVisibility(true)... Attach?");
            } else {
                setVisibility(true);
                Log.d(TAG,"setVisibility(false)... Attach?");
            }
        } else {
            setVisibility(false);
            Log.d(TAG,"setVisibility(false)... device==null");
        }
    }

    /** Check current mod whether in developer mode */
    private boolean isMDKMod(ModDevice device) {
        if (device == null) {
            /** Mod is not available */
            return false;
        } else if (device.getVendorId() == Constants.VID_DEVELOPER
                && device.getProductId() == Constants.PID_DEVELOPER) {
            // MDK in developer mode
            return true;
        } else {
            // Check MDK
            return device.getVendorId() == Constants.VID_MDK;
        }
    }

    /** Got data from mod device RAW I/O */
    public void onRawData(byte[] buffer, int length) {
        /** Parse raw data to header and payload */
        int cmd = buffer[Constants.CMD_OFFSET] & ~Constants.TEMP_RAW_COMMAND_RESP_MASK & 0xFF;
        int payloadLength = buffer[Constants.SIZE_OFFSET];

        /** Checking the size of buffer we got to ensure sufficient bytes */
        if (payloadLength + Constants.CMD_LENGTH + Constants.SIZE_LENGTH != length) {
            return;
        }

        /** Parser payload data */
        byte[] payload = new byte[payloadLength];
        System.arraycopy(buffer, Constants.PAYLOAD_OFFSET, payload, 0, payloadLength);
        parseResponse(cmd, payloadLength, payload);
    }

    /** RAW I/O of attached mod device is ready to use */
    public void onRawInterfaceReady() {
        /**
         *  Personality has the RAW interface, query the information data via RAW command, the data
         *  will send back from MDK with flag TEMP_RAW_COMMAND_INFO and TEMP_RAW_COMMAND_CHALLENGE.
         */
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                personality.getRaw().executeRaw(Constants.RAW_CMD_INFO);
            }
        }, 500);
    }

    /** Handle the IO issue when write / read */
    public void onIOException() {
    }

    /*
     * Beginning in Android 6.0 (API level 23), users grant permissions to apps while
     * the app is running, not when they install the app. App need check on and request
     * permission every time perform an operation.
    */
    public void onRequestRawPermission() {
        requestPermissions(new String[]{ModManager.PERMISSION_USE_RAW_PROTOCOL},
                RAW_PERMISSION_REQUEST_CODE);
    }

    /** Handle permission request result */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == RAW_PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (null != personality) {
                    /** Permission grant, try to check RAW I/O of mod device */
                    personality.getRaw().checkRawInterface();
                }
            } else {
                // TODO: user declined for RAW accessing permission.
                // You may need pop up a description dialog or other prompts to explain
                // the app cannot work without the permission granted.
            }
        }
    }

    boolean dialog_active = false;
    public void callForHelp(){
            if(!dialog_active) {
                dialog_active = true;
                AlertDialog.Builder helpAlert = new AlertDialog.Builder(this);
                helpAlert.setMessage("Help is on its way.")
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                dialog_active = false;
                            }
                        })
                        .create();
                helpAlert.show();
            }
    }

    /** Parse the data from mod device */
    private void parseResponse(int cmd, int size, byte[] payload) {
        if (cmd == Constants.TEMP_RAW_COMMAND_INFO) {
            /** Got information data from personality board */

            /**
             * Checking the size of payload before parse it to ensure sufficient bytes.
             * Payload array shall at least include the command head data, and exactly
             * same as expected size.
             */
            if (payload == null
                    || payload.length != size
                    || payload.length < Constants.CMD_INFO_HEAD_SIZE) {
                return;
            }

            int version = payload[Constants.CMD_INFO_VERSION_OFFSET];
            int reserved = payload[Constants.CMD_INFO_RESERVED_OFFSET];
            int latencyLow = payload[Constants.CMD_INFO_LATENCYLOW_OFFSET] & 0xFF;
            int latencyHigh = payload[Constants.CMD_INFO_LATENCYHIGH_OFFSET] & 0xFF;
            int max_latency = latencyHigh << 8 | latencyLow;

            StringBuilder name = new StringBuilder();
            for (int i = Constants.CMD_INFO_NAME_OFFSET; i < size - Constants.CMD_INFO_HEAD_SIZE; i++) {
                if (payload[i] != 0) {
                    name.append((char) payload[i]);
                } else {
                    break;
                }
            }
            Log.i(Constants.TAG, "command: " + cmd
                    + " size: " + size
                    + " version: " + version
                    + " reserved: " + reserved
                    + " name: " + name.toString()
                    + " latency: " + max_latency);
        } else if (cmd == Constants.TEMP_RAW_COMMAND_DATA) {
            /** Got sensor data from personality board */

            /** Checking the size of payload before parse it to ensure sufficient bytes. */
            if (payload == null
                    || payload.length != size
                    || payload.length != Constants.CMD_DATA_SIZE) {
                return;
            }

            int dataLow = payload[Constants.CMD_DATA_LOWDATA_OFFSET] & 0xFF;
            int dataHigh = payload[Constants.CMD_DATA_HIGHDATA_OFFSET] & 0xFF;

            /** The raw temperature sensor data */
            int data = dataHigh << 8 | dataLow;

            /** The temperature */
            double temp = ((0 - 0.03) * data) + 128;

            /** Draw temperature value to line chart */
        } else if (cmd == Constants.TEMP_RAW_COMMAND_CHALLENGE) {
            /** Got CHALLENGE command from personality board */

            /** Checking the size of payload before parse it to ensure sufficient bytes. */
            if (payload == null
                    || payload.length != size
                    || payload.length != Constants.CMD_CHALLENGE_SIZE) {
                return;
            }

            byte[] resp = Constants.getAESECBDecryptor(Constants.AES_ECB_KEY, payload);
            if (resp != null) {
                /** Got decoded CHALLENGE payload */
                ByteBuffer buffer = ByteBuffer.wrap(resp);
                buffer.order(ByteOrder.LITTLE_ENDIAN); // lsb -> msb
                long littleLong = buffer.getLong();
                littleLong += Constants.CHALLENGE_ADDATION;

                ByteBuffer buf = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).order(ByteOrder.LITTLE_ENDIAN);
                buf.putLong(littleLong);
                byte[] respData = buf.array();

                /** Send challenge response back to mod device */
                byte[] aes = Constants.getAESECBEncryptor(Constants.AES_ECB_KEY, respData);
                if (aes != null) {
                    byte[] challenge = new byte[aes.length + 2];
                    challenge[0] = Constants.TEMP_RAW_COMMAND_CHLGE_RESP;
                    challenge[1] = (byte) aes.length;
                    System.arraycopy(aes, 0, challenge, 2, aes.length);
                    personality.getRaw().executeRaw(challenge);
                } else {
                    Log.e(Constants.TAG, "AES encrypt failed.");
                }
            } else {
                Log.e(Constants.TAG, "AES decrypt failed.");
            }
        } else if (cmd == Constants.TEMP_RAW_COMMAND_CHLGE_RESP) {
            /** Get challenge command response */

            /** Checking the size of payload before parse it to ensure sufficient bytes. */
            if (payload == null
                    || payload.length != size
                    || payload.length != Constants.CMD_CHLGE_RESP_SIZE) {
                return;
            }

            /**
             * Check first byte, response from MDK Sensor Card shall be 0
             * if challenge passed
             */
            boolean challengePassed = payload[Constants.CMD_CHLGE_RESP_OFFSET] == 0;
        }
    }
}
