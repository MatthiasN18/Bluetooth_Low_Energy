package com.example.bluetooth_low_energy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final UUID hr_service = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID hr_characteristic = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID hr_control_characteristic = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb");
    BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;
    BluetoothLeScanner btScanner;
    BluetoothGatt bluetoothGatt;
    int deviceID = 0;
    boolean scanning = false;
    ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int SCAN_PERIOD = 7000;
    Handler handler = new Handler();

    //Layout
    TextView peripheralTextView;
    Button connectButton;
    Button disconnectButton;
    Button scanButton;
    Button stopButton;
    EditText deviceIDEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        peripheralTextView = findViewById(R.id.PeripheralTextView);
        peripheralTextView.setMovementMethod(new ScrollingMovementMethod());
        deviceIDEditText = findViewById(R.id.deviceIDEditText);
        deviceIDEditText.setText("0");

        connectButton = findViewById(R.id.ConnectButton);
        connectButton.setOnClickListener(v -> connectToDevice());

        disconnectButton = findViewById(R.id.DisconnectButton);
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());

        scanButton = findViewById(R.id.ScanButton);
        scanButton.setOnClickListener(v -> scanForDevices());

        stopButton = findViewById(R.id.StopButton);
        stopButton.setOnClickListener(v -> stopScan());


        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        //ensure bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //ensure location services are enabled
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            super.onScanResult(callbackType, result);
            //scan and add devices to arraylist
            if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            peripheralTextView.append("ID: " + deviceID + " || DeviceName: " + result.getDevice().getName());
            devices.add(result.getDevice());
            deviceID++;

            //scroll to bottom of textview
            final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
            if (scrollAmount > 0) {
                peripheralTextView.scrollTo(0, scrollAmount);
            } else {
                peripheralTextView.scrollTo(0, 0);
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(android.bluetooth.BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case 0:
                    peripheralTextView.append("Disconnected");
                    connectButton.setVisibility(View.INVISIBLE);
                    disconnectButton.setVisibility(View.VISIBLE);
                    break;
                case 2:
                    peripheralTextView.append("Connected");
                    connectButton.setVisibility(View.VISIBLE);
                    disconnectButton.setVisibility(View.INVISIBLE);
                    //discover services
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    bluetoothGatt.discoverServices();
                    break;
                default:
                    peripheralTextView.append("Unknown State");
                    break;
            }
        }

        public void onServicesDiscovered(android.bluetooth.BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (bluetoothGatt.getServices() == null) return;

            for (BluetoothGattService service : bluetoothGatt.getServices()) {
                peripheralTextView.append("Service: " + service.getUuid().toString());
            }
            List<BluetoothGattCharacteristic> gattCharacteristics = bluetoothGatt.getService(hr_service).getCharacteristics();

            for (BluetoothGattCharacteristic characteristic : gattCharacteristics) {
                peripheralTextView.append("Characteristic: " + characteristic.getUuid().toString());
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);

            TextView hrValue = findViewById(R.id.hrValue);
            runOnUiThread(() -> hrValue.setText(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) + ""));
        }
    };

    public void scanForDevices() {
        scanning = true;
        deviceID = 0;
        devices.clear();
        peripheralTextView.append("Scanning...");
        scanButton.setVisibility(View.INVISIBLE);
        stopButton.setVisibility(View.VISIBLE);

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(hr_service))
                .build();
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        List<ScanFilter> scanFilters = new LinkedList<>();
        scanFilters.add(scanFilter);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        btScanner.startScan(scanFilters, scanSettings, scanCallback);

        handler.postDelayed(() -> stopScan(), SCAN_PERIOD);
    }

    public void stopScan() {
        scanning = false;
        peripheralTextView.append("Scan stopped");
        scanButton.setVisibility(View.VISIBLE);
        stopButton.setVisibility(View.INVISIBLE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        btScanner.stopScan(scanCallback);
    }

    public void connectToDevice() {
        peripheralTextView.append("Connecting to device...");
        int selectedDevice = Integer.parseInt(deviceIDEditText.getText().toString());
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt = devices.get(selectedDevice).connectGatt(this, false, gattCallback);
    }

    public void disconnectFromDevice() {
        peripheralTextView.append("Disconnecting from device...");
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.disconnect();
    }
}