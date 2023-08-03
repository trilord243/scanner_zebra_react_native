package com.scanner_test;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Promise;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.Scanner.DataListener;
import com.symbol.emdk.barcode.Scanner.StatusListener;
import com.symbol.emdk.barcode.BarcodeManager.ScannerConnectionListener;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;


public class ZebraScannerModule extends ReactContextBaseJavaModule implements EMDKListener, DataListener, StatusListener, ScannerConnectionListener {

    // Debugging
    private static final boolean D = true;
    private boolean isReading = false;


    // React native...
    private ReactApplicationContext mReactContext;

    // Zebra EMDK library
    private EMDKManager emdkManager = null;
    private BarcodeManager barcodeManager = null;
    private Scanner scanner = null;

    private ScannerInfo selectedScanner = null;
    private boolean bExtScannerDisconnected = false;
    private final Object lock = new Object();

    private static final String BARCODE_READ_SUCCESS = "barcodeReadSuccess";
    private static final String BARCODE_READ_FAIL = "barcodeReadFail";



    public ZebraScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mReactContext = reactContext;

        EMDKResults results = EMDKManager.getEMDKManager(reactContext, this);
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            updateStatus("EMDKManager object request failed!");
        }
    }

    @Override
    public String getName() {
        return "ZebraScanner";
    }

    private void initScanner() {
        if (scanner == null) {
            if (selectedScanner != null) {
                if (barcodeManager != null) {
                    scanner = barcodeManager.getDevice(selectedScanner);
                }
            } else {
                updateStatus("Failed to get the specified scanner device! Please close and restart the application.");
                return;
            }
            if (scanner != null) {
                scanner.addDataListener(this);
                scanner.addStatusListener(this);
                try {
                    scanner.enable();
                } catch (ScannerException e) {
                    updateStatus(e.getMessage());
                    deInitScanner();
                }
            } else {
                updateStatus("Failed to initialize the scanner device.");
            }
        }
    }

    private void deInitScanner() {
        if (scanner != null) {
            try {
                scanner.disable();
            } catch (Exception e) {
                updateStatus(e.getMessage());
            }

            try {
                scanner.removeDataListener(this);
                scanner.removeStatusListener(this);
            } catch (Exception e) {
                updateStatus(e.getMessage());
            }

            try {
                scanner.release();
            } catch (Exception e) {
                updateStatus(e.getMessage());
            }
            scanner = null;
        }
    }

    private void initBarcodeManager() {
        barcodeManager = (BarcodeManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);
        // Add connection listener
        if (barcodeManager != null) {
            barcodeManager.addConnectionListener(this);
        }
    }

    private void deInitBarcodeManager() {
        if (emdkManager != null) {
            emdkManager.release(EMDKManager.FEATURE_TYPE.BARCODE);
        }
    }

    private void enumerateScannerDevices() {
        if (barcodeManager != null) {
            List<ScannerInfo> deviceList = barcodeManager.getSupportedDevicesInfo();
            if ((deviceList != null) && (deviceList.size() != 0)) {
                for (ScannerInfo scnInfo : deviceList) {
                    if (scnInfo.isDefaultScanner()) {
                        selectedScanner = scnInfo;
                        updateStatus("Setting " + scnInfo.getFriendlyName() + " as default scanner");
                        break;
                    }
                }
            } else {
                updateStatus("Failed to get the list of supported scanner devices! Please close and restart the application.");
            }

            // Init the scanner.
            initScanner();
        }
    }

    /**
     * Send event to javascript
     * @param eventName Name of the event
     * @param params Additional params
     */
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        mReactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);

    }

    public void onBarcodeEvent(String barcode) {
        updateStatus("Barcode scan read: " + barcode);
        WritableMap params = Arguments.createMap();
        params.putString("data", barcode);
        sendEvent(BARCODE_READ_SUCCESS, params);
    }

    /*******************************/
    /** Methods Available from JS **/
    /*******************************/
    @ReactMethod
    public void startReader(final Promise promise) {
        Log.d("debbuf","Start");
        updateStatus("EMDK open success!");
        this.emdkManager = emdkManager;
        // Acquire the barcode manager resources
        initBarcodeManager();
        // Enumerate scanner devices
        enumerateScannerDevices();
        this.isReading = true;

        promise.resolve(true);
    }

    @ReactMethod
    public void stopReader(Promise promise) {
        Log.d("debbuf","Stop");
        // The application is in background
        // Release the barcode manager resources
        deInitScanner();
        deInitBarcodeManager();
        this.isReading = false;

        promise.resolve(null);
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("BARCODE_READ_SUCCESS", BARCODE_READ_SUCCESS);
        constants.put("BARCODE_READ_FAIL", BARCODE_READ_FAIL);
        return constants;
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {
        this.emdkManager = emdkManager;
        // Acquire the barcode manager resources
        initBarcodeManager();
        // Enumerate scanner devices
        enumerateScannerDevices();
    }

    @Override
    public void onClosed() {
        // Release all the resources
        if (emdkManager != null) {
            emdkManager.release();
            emdkManager = null;
        }
        updateStatus("EMDK closed unexpectedly! Please close and restart the application.");
    }

    @Override
    public void onConnectionChange(ScannerInfo scannerInfo, BarcodeManager.ConnectionState connectionState) {
        String status;
        String scannerName = "";
        String statusExtScanner = connectionState.toString();
        String scannerNameExtScanner = scannerInfo.getFriendlyName();
        if (selectedScanner != null) {
            scannerName = selectedScanner.getFriendlyName();
            updateStatus("Applying " + scannerName);
        }
        if (scannerName.equalsIgnoreCase(scannerNameExtScanner)) {
            switch (connectionState) {
                case CONNECTED:
                    synchronized (lock) {
                        initScanner();
                        bExtScannerDisconnected = false;
                    }
                    break;
                case DISCONNECTED:
                    bExtScannerDisconnected = true;
                    synchronized (lock) {
                        deInitScanner();
                    }
                    break;
            }
            status = scannerNameExtScanner + ":" + statusExtScanner;
            updateStatus(status);
        } else {
            bExtScannerDisconnected = false;
            status = scannerNameExtScanner + ":" + statusExtScanner;
            updateStatus(status);
        }
    }

    @Override
    public void onData(ScanDataCollection scanDataCollection) {
        if ((scanDataCollection != null) && (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
            ArrayList<ScanDataCollection.ScanData> scanData = scanDataCollection.getScanData();
            for (ScanDataCollection.ScanData data : scanData) {
                onBarcodeEvent(data.getData());
            }
        }
    }

    @Override
    public void onStatus(StatusData statusData) {
        StatusData.ScannerStates state = statusData.getState();
        String statusString = "";
        switch (state) {
            case IDLE:
                statusString = statusData.getFriendlyName() + " is enabled and idle...";
                updateStatus(statusString);
                // set trigger type
                scanner.triggerType = Scanner.TriggerType.HARD;
                // submit read
                if (isReading && !scanner.isReadPending() && !bExtScannerDisconnected) {
                    try {
                        scanner.read();
                    } catch (ScannerException e) {
                        updateStatus(e.getMessage());
                    }
                }
                break;
            case WAITING:
                statusString = statusData.getFriendlyName() + "Scanner is waiting for trigger press...";
                updateStatus(statusString);
                break;
            case SCANNING:
                statusString = "Scanning...";
                updateStatus(statusString);
                break;
            case DISABLED:
                statusString = statusData.getFriendlyName() + " is disabled.";
                updateStatus(statusString);
                break;
            case ERROR:
                statusString = "An error has occurred.";
                updateStatus(statusString);
                break;
            default:
                break;
        }
    }


    private void updateStatus(final String status) {
        if (D) Log.d("TAG", "ZEBRASCANNER - Status: " + status);
    }
}

