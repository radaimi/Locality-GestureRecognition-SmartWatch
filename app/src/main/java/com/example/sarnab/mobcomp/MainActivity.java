package com.example.sarnab.mobcomp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends WearableActivity {

    private BluetoothAdapter mBTA;
    private String MY_DEVICE1_MAC = "B0:B4:48:CF:5F:83";
    private String MY_DEVICE2_MAC = "B0:B4:48:C3:1D:83";
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private Handler mHandler;
    final int REQUEST_ENABLE_BT = 7, MY_LOCATION = 4;
    Button scanBtn;
    ArrayList<String> deviceMacs;
    private ArrayList<BluetoothDevice> mydevices;
    public static String EXTRA_DEVICE1 = "bt_device1";
    public static String EXTRA_DEVICE2 = "bt_device2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceMacs = new ArrayList<String>();
        mydevices = new ArrayList<BluetoothDevice>();

        mHandler = new Handler();

        //Check if BLE is supported by device else exit
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this, R.string.no_ble, Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager BTM = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBTA = BTM.getAdapter();
        if(mBTA == null || !mBTA.isEnabled()){
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_ENABLE_BT);
        }

        scanBtn = (Button) findViewById(R.id.scanBtn);

        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_LOCATION);
        }

        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Scan();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Location access granted", Toast.LENGTH_LONG).show();
                    // permission was granted, yay!

                } else {
                    Toast.makeText(getApplicationContext(), "Exiting...", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode)
        {
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, R.string.no_bt, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private void Scan()
    {
        mLEScanner = mBTA.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        filters = new ArrayList<ScanFilter>();
        scanLeDevice(true);

    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);
            mLEScanner.startScan(filters, settings, mScanCallback);
        } else {
            mLEScanner.stopScan(mScanCallback);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice BtDev = result.getDevice();
            mydevices.add(BtDev);
            deviceMacs.add(BtDev.getAddress());
            if(deviceMacs.contains(MY_DEVICE1_MAC) && deviceMacs.contains(MY_DEVICE2_MAC))
            {
                connectToDevice();
            }
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    public void connectToDevice() {
        scanLeDevice(false);// will stop after device detection
        Intent newActivity = new Intent(MainActivity.this, Connection.class);
        newActivity.putExtra(EXTRA_DEVICE1, mydevices.get(deviceMacs.indexOf(MY_DEVICE1_MAC)));
        newActivity.putExtra(EXTRA_DEVICE2, mydevices.get(deviceMacs.indexOf(MY_DEVICE2_MAC)));
        startActivity(newActivity);
    }

}
