package com.example.sarnab.mobcomp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.UUID;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

public class Connection extends WearableActivity {

    public static final String TAG = Connection.class.getSimpleName();
    BluetoothDevice BTDev1,BTDev2;
    BluetoothGattCharacteristic LedChar1, LedChar2;
    public static String EXTRA_DEVICE1 = "bt_device1";
    public static String EXTRA_DEVICE2 = "bt_device2";
    BluetoothGatt gatt1, gatt2;
    UUID MY_SERVICE_UUID, MY_LED_CHAR_UUID, CLIENT_DESC_CONFIG_UUID;
    boolean connected1, connected2, display_rssi;
    int [] rssi1 = new int[5];
    int [] rssi2 = new int[5];

    TextView xTV,yTV,zTV;

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;
    SensorEventListener Acc,Gyr;

    float[] grav = new float[3];
    float[] gyro = new float[3];
    float[] GyroFifo = new float[21];
    int gest = 0;
    int gestCounter = 0;
    int alpha = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        BTDev1 = getIntent().getExtras().getParcelable(EXTRA_DEVICE1);
        BTDev2 = getIntent().getExtras().getParcelable(EXTRA_DEVICE2);
        MY_SERVICE_UUID = convertFromInteger(0xFFF0);
        MY_LED_CHAR_UUID = convertFromInteger(0xFFF1);
        CLIENT_DESC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        xTV = (TextView)findViewById(R.id.xTV);
        yTV = (TextView)findViewById(R.id.yTV);
        zTV = (TextView)findViewById(R.id.zTV);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if ((sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) && (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)) {
            // success! we have an accelerometer and magnetometer
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        } else {
            // fai! we dont have an accelerometer and/or gyroscope!
            Log.d("Sensor", "sensors unavailable!!");
            finish();
        }

        Acc = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                System.arraycopy(event.values, 0, grav, 0, 3);
                double acc_vect = Math.sqrt(grav[0]*grav[0] + grav[1]*grav[1] + grav[2]*grav[2]);
                if(acc_vect>60.0)
                {
                    sensorManager.unregisterListener(Acc);
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            //Do something after 100ms
                            sensorManager.registerListener(Gyr, gyroscope, 10000);
                            yTV.setText(R.string.gest);
                            display_rssi=true;
                        }
                    }, 100);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        Gyr = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int peak=0;
                System.arraycopy(event.values, 0, gyro, 0, 3);
                System.arraycopy(GyroFifo, 1, GyroFifo, 0, 20);
                GyroFifo[20] = gyro[2];
                if(GyroFifo[10]<-2.5)
                {
                    if( (GyroFifo[10]<GyroFifo[0]) && (GyroFifo[10]<GyroFifo[20]) )
                        peak = 1;
                }

                if(peak==0 && gest==1) {
                    gestCounter++;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            xTV.setText(String.valueOf(gestCounter));
                        }
                    });
                    sensorManager.unregisterListener(Gyr);
                    int rssiSum1 = rssi1[0]+rssi1[1]+rssi1[2]+rssi1[3]+rssi1[4];
                    int rssiSum2 = rssi2[0]+rssi2[1]+rssi2[2]+rssi2[3]+rssi2[4];
                    if (rssiSum1 > rssiSum2)
                    {
                        gatt1.readCharacteristic(LedChar1);
                    }
                    else
                    {
                        gatt2.readCharacteristic(LedChar2);
                    }
                    sensorManager.registerListener(Acc, accelerometer, SensorManager.SENSOR_DELAY_UI);
                    display_rssi = false;
                    Handler hnd = new Handler();
                    hnd.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            yTV.setText(R.string.jerk);
                            zTV.setText("off");
                        }
                    }, 500);

                }
                gest = peak;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        for(int i=0;i<5;i++)
        {
            rssi1[i] = -200;
            rssi2[i] = -200;
        }
        ConnectGatt conn = new ConnectGatt();
        conn.start();
        sensorManager.registerListener(Acc, accelerometer, SensorManager.SENSOR_DELAY_UI);

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        Log.d("Destroy","getting destroyed");
        if(connected1)
            gatt1.disconnect();
        if(connected2)
            gatt2.disconnect();
        super.onDestroy();
    }

    public UUID convertFromInteger(int i) {
        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805F9B34FBL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);
    }

    private class ConnectGatt extends Thread {
        private ConnectGatt() {
        }
        public void run() {
            BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == STATE_CONNECTED) {
                        if(gatt==gatt1)
                            connected1 = true;
                        else if(gatt==gatt2)
                            connected2 = true;
                        gatt.discoverServices();

                        if(connected1 ^ connected2) {
                            Log.d("RSSI","polling start");
                            RSSIpoll poll = new RSSIpoll();
                            poll.start();
                        }
                    }
                    if (newState == STATE_DISCONNECTED) {
                        if(gatt==gatt1) {
                            connected1 = false;
                            rssi1[0] = -200;
                            rssi1[1] = -200;
                            rssi1[2] = -200;
                            rssi1[3] = -200;
                            rssi1[4] = -200;
                        }
                        else if(gatt==gatt2) {
                            connected2 = false;
                            rssi2[0] = -200;
                            rssi2[1] = -200;
                            rssi2[2] = -200;
                            rssi2[3] = -200;
                            rssi2[4] = -200;
                        }
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    Log.d("Service", MY_SERVICE_UUID.toString());
                    Log.d("LED Characteristic", MY_LED_CHAR_UUID.toString());
                    BluetoothGattService S = gatt.getService(MY_SERVICE_UUID);
                    if(gatt==gatt1)
                        LedChar1 = S.getCharacteristic(MY_LED_CHAR_UUID);
                    else if(gatt==gatt2)
                        LedChar2 = S.getCharacteristic(MY_LED_CHAR_UUID);
                    //gatt.readCharacteristic(LedChar);
                }

                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
                {
                    if(gatt==gatt1) {
                        System.arraycopy(rssi1, 1, rssi1, 0, 4);
                        rssi1[4] = (rssi*alpha + rssi1[3]*(100-alpha))/100;
                    }
                    else if(gatt==gatt2) {
                        System.arraycopy(rssi2, 1, rssi2, 0, 4);
                        rssi2[4] = (rssi*alpha + rssi2[3]*(100-alpha))/100;
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
                    if(status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d("Read", "Success!!");
                        if(characteristic.getUuid().equals(MY_LED_CHAR_UUID)){
                            byte[] v = characteristic.getValue();
                            v[0] = (byte)(1 - v[0]);
                            characteristic.setValue(v);
                            gatt.writeCharacteristic(characteristic);
                        }
                    }
                    else
                        Log.d("Read", "Failure!!");
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
                {
//                    if(characteristic.getUuid().equals(MY_LED_CHAR_UUID)) {
//                    }
                }
            };
            gatt1 = BTDev1.connectGatt(Connection.this, true, gattCallback);
            gatt2 = BTDev2.connectGatt(Connection.this, true, gattCallback);
            Log.d("Thread","gatt connect started");
        }
    }

    private class RSSIpoll extends Thread {
        private RSSIpoll() {
        }
        public void run() {
            while(connected1 || connected2) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.d("Thread", "interrupted");
                }
                if(connected1)
                    gatt1.readRemoteRssi();
                if(connected2)
                    gatt2.readRemoteRssi();
                if(display_rssi) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int rssiSum1 = rssi1[0] + rssi1[1] + rssi1[2] + rssi1[3] + rssi1[4];
                            int rssiSum2 = rssi2[0] + rssi2[1] + rssi2[2] + rssi2[3] + rssi2[4];
                            if (rssiSum1 > rssiSum2)
                                zTV.setText("1");
                            else
                                zTV.setText("2");
                        }
                    });
                }
            }
        }
    }
}
