package com.example.testmetawearjava;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.DataProducer;
import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.ForcedDataProducer;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.CartesianAxis;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Led;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends Activity implements ServiceConnection {
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "C3:70:17:BB:47:48";
    private MetaWearBoard board;
    private final static int REQUEST_ENABLE_BT = 1;
    private int connectionAttemps = 0;

    private List<String> dataAsStrings = new ArrayList<String>();
    private int dataCount = 0;
    private int dataCountTreshold = 1;

    private TextView datadisplay;

    Runnable updater;
    void updateTime() {
        datadisplay=(TextView) findViewById(R.id.datadisplay);
        final Handler timerHandler = new Handler();

        updater = new Runnable() {
            @Override
            public void run() {
              //  Log.i("Timer","Here, size: "+dataAsStrings.size());
                if (dataAsStrings.size() > 0){

                    datadisplay.setText(dataAsStrings.get(dataAsStrings.size()-1));
                   // datadisplay.invalidate();
                   // datadisplay.requestLayout();
                }
                timerHandler.postDelayed(updater,300);
            }
        };
        timerHandler.post(updater);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

       datadisplay = findViewById(R.id.datadisplay);
       datadisplay.setText("moro");
    updateTime();

    }

    public void retrieveBoard() {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

            Log.i("bluedevice", remoteDevice.toString());
            if (serviceBinder == null) Log.i("blueservice","is null");

        // Create a MetaWear board object for the Bluetooth Device
        if (remoteDevice != null){
            board= serviceBinder.getMetaWearBoard(remoteDevice);
        }

    }



    public void scanForBoard(String UUID){
        final BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = null;
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (board != null){
            board.tearDown();
        }
        //timerHandler.removeCallbacks(updater);

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        retrieveBoard();
        connectDevice();
    }

    public void readForcedProducer(ForcedDataProducer producer) {
        // instructs producer to collect one data sample
        producer.read();
    }
    public void writeToDataDisplay(){
            final String data = dataAsStrings.get(dataCount - 1);
            datadisplay.setText(data);

    }
    public static void logDataTypes(Data data) {
        Log.i("MainActivity", "Class types; " + Arrays.toString(data.types()));
    }

    public void streamData(DataProducer producer) {
        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {

                        dataAsStrings.add(data.toString());
                        dataCount++;

                        if (dataCount == dataCountTreshold){

                          Acceleration acc = (Acceleration) data.value(data.types()[0]);
                            float x = acc.x();
                            float y = acc.y();
                            float z = acc.z();

                            Log.i("data","x: "+x+ "g y: "+y+ "g z: "+z +"g" + " tostring: " + acc.toString());

                            dataCountTreshold += 50;
                        }


                       //

                    }
                });
            }
        });
    }

    public void connectDevice(){
        board.connectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                if (task.isFaulted()) {
                    Log.i("MainActivity", "BlueConnect failed to connect");
                    connectionAttemps++;
                    if (connectionAttemps < 10){
                        connectDevice();
                    }

                } else {
                    Log.i("MainActivity", "BlueConnect connected");
                    Log.i("MainActivity", "board model = " + board.getModel());
                    board.readDeviceInformationAsync()
                            .continueWith(new Continuation<DeviceInformation, Void>() {
                                @Override
                                public Void then(Task<DeviceInformation> task) throws Exception {
                                    Log.i("MainActivity", "Device Information: " + task.getResult());
                                    return null;
                                }
                            });
                    Accelerometer  accelerometer;

                    if ((accelerometer = board.getModule(Accelerometer.class)) != null){

                        Log.i("BlueActivity","Accelerometer found");
                        Log.i("BlueActivity","Accelerometer range "+accelerometer.getRange());
                      //  accelerometer.start();
                        Accelerometer.AccelerationDataProducer accproducer = accelerometer.acceleration();

                        accproducer.start();
                        streamData(accproducer);

                    }
                    Led led;
                    if ((led= board.getModule(Led.class)) != null) {
                        led.editPattern(Led.Color.BLUE, Led.PatternPreset.BLINK)
                                .repeatCount((byte) 10)
                                .commit();
                        led.play();
                    }

                    

                }

                return null;
            }
        });

        board.onUnexpectedDisconnect(new MetaWearBoard.UnexpectedDisconnectHandler() {
            @Override
            public void disconnected(int status) {
                Log.i("MainActivity", "Unexpectedly lost connection: " + status);
            }
        });



    }





    @Override
    public void onServiceDisconnected(ComponentName componentName) { }
}