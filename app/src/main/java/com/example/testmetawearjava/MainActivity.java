package com.example.testmetawearjava;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.mbientlab.metawear.AsyncDataProducer;
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.DataProducer;
import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.ForcedDataProducer;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.builder.function.Function2;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.CartesianAxis;
import com.mbientlab.metawear.data.EulerAngles;
import com.mbientlab.metawear.data.Quaternion;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Gyro;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    private List<Float> xData = new ArrayList<>();
    private List<Float> yData = new ArrayList<>();
    private List<Float> zData = new ArrayList<>();


    private List<List<Float>> accelometer_offSetDataSet = new ArrayList<>();
    private float accelometer_offsetZ = 0f;
    private float accelometer_offsetY = 0f;
    private float accelometer_offsetX = 0f;

    private TextView statusTextView;
    private Button connectButton;
    private Button startDataStreamButton;
    private Button calibrationButton;

    private boolean bindSuccesful = false;
    private boolean streamingData = false;
    private boolean connectingCurrently = false;
    private boolean calibratingCurrently = false;

    private Color buttonDefaultColor;

    private TextView datadisplay;
    private GraphView graph1;
    private GraphView graph2;
    private GraphView graph3;

    LineGraphSeries<DataPoint> series1;
    int seriesSize1 = 0;
    int maxX = 1000;

    LineGraphSeries<DataPoint> series2;
    int seriesSize2 = 0;

    LineGraphSeries<DataPoint> series3;
    int seriesSize3 = 0;

    SensorFusionBosch sensorFusion;
    Accelerometer  accelerometer;

    Runnable updater;
    boolean runUpdater = true;
    DecimalFormat df = new DecimalFormat("#.####");


   // private SensorFusionBosch sensorFusion;







    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
        statusTextView = findViewById(R.id.connectionstatus);
        connectButton = findViewById(R.id.connect_button);
        startDataStreamButton = findViewById(R.id.start_datastream_button);
        calibrationButton = findViewById(R.id.calibrateButton);
        datadisplay = findViewById(R.id.datadisplay);
        datadisplay.setSingleLine(false);


        df.setRoundingMode(RoundingMode.CEILING);


        changeButtonByState(0,calibrationButton);
        changeButtonByState(0, startDataStreamButton);

        addListeners();

        initiateGraphs();


        /*
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
        staticLabelsFormatter.setHorizontalLabels(new String[] {"old", "middle", "new"});
        staticLabelsFormatter.setVerticalLabels(new String[] {"low", "middle", "high"});
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);


         */

    }

    void initiateGraphs(){
        graph1 = findViewById(R.id.graphview);
        series1 = new LineGraphSeries<>();
        graph1.addSeries(series1);
        graph1.getViewport().setXAxisBoundsManual(true);
        graph1.getViewport().setYAxisBoundsManual(true);
        graph1.getViewport().setMinX(0);
        graph1.getViewport().setMaxX(maxX);
        graph1.getViewport().setMinY(-7);
        graph1.getViewport().setMaxY(7);
        graph1.setTitle("X-data");


        graph2 = findViewById(R.id.graphview2);
        series2 = new LineGraphSeries<>();
        graph2.addSeries(series2);
        graph2.getViewport().setXAxisBoundsManual(true);
        graph2.getViewport().setYAxisBoundsManual(true);
        graph2.getViewport().setMinX(0);
        graph2.getViewport().setMaxX(maxX);
        graph2.getViewport().setMinY(-7);
        graph2.getViewport().setMaxY(7);
        graph2.setTitle("Y-data");

        graph3 = findViewById(R.id.graphview3);
        series3 = new LineGraphSeries<>();
        graph3.addSeries(series3);
        graph3.getViewport().setXAxisBoundsManual(true);
        graph3.getViewport().setYAxisBoundsManual(true);
        graph3.getViewport().setMinX(0);
        graph3.getViewport().setMaxX(maxX);
        graph3.getViewport().setMinY(-7);
        graph3.getViewport().setMaxY(7);
        graph3.setTitle("Z-data");
    }

    void addListeners(){

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectButtonListener();
            }
        });

        startDataStreamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDataStreamButtonListener();
            }
        });

        calibrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calibrationButtonListener();
            }
        });


    }

    void calculateAccelerometerOffsets(){

        float runningX = 0f;
        float runningY = 0f;
        float runningZ = 0f;

        for (List<Float> item : accelometer_offSetDataSet){
            runningX += item.get(0);
            runningY += item.get(1);
            runningZ += item.get(2);
        }

        int sampleSize = accelometer_offSetDataSet.size();

        accelometer_offsetX = (runningX / sampleSize);
        accelometer_offsetY = (runningY / sampleSize);
        accelometer_offsetZ = (runningZ / sampleSize);

    }



    void changeButtonByState(int state, Button button, String text){
        if (state == 2){
            Drawable buttonDrawable = button.getBackground();
            buttonDrawable = DrawableCompat.wrap(buttonDrawable);
            DrawableCompat.setTint(buttonDrawable, Color.CYAN);

            button.setBackground(buttonDrawable);
            button.setText(text);
            button.setTextColor(Color.DKGRAY);

        } else if (state == 1) {
            Drawable buttonDrawable = button.getBackground();
            buttonDrawable = DrawableCompat.wrap(buttonDrawable);
            DrawableCompat.setTint(buttonDrawable, Color.LTGRAY);

            button.setTextColor(Color.DKGRAY);
            button.setBackground(buttonDrawable);
            button.setText(text);


        } else {
            Drawable buttonDrawable = button.getBackground();
            buttonDrawable = DrawableCompat.wrap(buttonDrawable);
            DrawableCompat.setTint(buttonDrawable, Color.parseColor("#ebebeb"));

            button.setBackground(buttonDrawable);
            button.setTextColor(Color.LTGRAY);
            button.setText(text);

        }
    }

    void changeButtonByState(int state, Button button){
        if (state == 2){
            Drawable buttonDrawable = button.getBackground();
            buttonDrawable = DrawableCompat.wrap(buttonDrawable);
            DrawableCompat.setTint(buttonDrawable, Color.CYAN);

            button.setBackground(buttonDrawable);
            button.setTextColor(Color.DKGRAY);

        } else if (state == 1) {
            Drawable buttonDrawable = button.getBackground();
            buttonDrawable = DrawableCompat.wrap(buttonDrawable);
            DrawableCompat.setTint(buttonDrawable, Color.LTGRAY);

            button.setTextColor(Color.DKGRAY);
            button.setBackground(buttonDrawable);


        } else {
            Drawable buttonDrawable = button.getBackground();
            buttonDrawable = DrawableCompat.wrap(buttonDrawable);
            DrawableCompat.setTint(buttonDrawable, Color.parseColor("#ebebeb"));

            button.setBackground(buttonDrawable);
            button.setTextColor(Color.LTGRAY);

        }
    }

    void calibrationButtonListener(){
        if (calibratingCurrently){

        } else {
            stopAccelometerStream();
            stopSensorFusionStream();
            calibratingCurrently = true;
            streamingData = true;
            startColletingCalibrationDataLinearAcceleration();

        }
    }

    void connectButtonListener(){
        if (bindSuccesful){
            if (board == null) return;

            Log.i("status","connectingCurrently: "+connectingCurrently + " board.connected: "+board.isConnected());

            if (connectingCurrently){
                shutDown();
                return;
            }

            if (!board.isConnected()){
                connectingCurrently = true;
                connectDevice();
                connectionAttemps = 0;
                changeButtonByState(2,connectButton, "Disconnect");
            } else {
                shutDown();
            }
        }
    }

    void stopSensorFusionStream(){
        if (sensorFusion != null){
            if (sensorFusion.linearAcceleration() != null){
                sensorFusion.linearAcceleration().stop();
            }
            sensorFusion.stop();
        }
        if (board != null){
            board.tearDown();
        }
      //  changeButtonByState(1,startDataStreamButton,"Start data stream");
        runUpdater = false;
        streamingData = false;
        connectingCurrently = false;
        calibratingCurrently = false;
    }

    void stopAccelometerStream(){
        if (accelerometer != null){
            if (accelerometer.acceleration() != null){
                accelerometer.acceleration().stop();
            }
            accelerometer.stop();
        }
        if (board != null){
            board.tearDown();
        }
        changeButtonByState(1,startDataStreamButton,"Start data stream");
        runUpdater = false;
        streamingData = false;
        connectingCurrently = false;
    }

    void startDataStreamButtonListener(){
        if (board == null) return;
        if (board.isConnected()){
            if (!streamingData){
                /*
                    if (accelometer_offsetZ != 0 || accelometer_offsetY != 0 || accelometer_offsetX != 0){
                        startAccelerationDataStreamWithOffsetNotSplit();
                    } else {
                        startAccelerationDataStreamWithoutOffset();
                    }

                 */
                startLinearMotionStream();
            } else {
              //  stopAccelometerStream();
                stopSensorFusionStream();
                changeButtonByState(1,startDataStreamButton,"Start data stream");
                if (board.isConnected()) setStatusText("Connected to "+ board.getModel().toString());
                else
                    setStatusText("Disconnected");
               // plotData();

            }
        }
    }

    int plotData(LineGraphSeries<DataPoint> series, int seriesSize, List<Float> dataArray){

        int size = dataArray.size();
        float y;

        for (int i = seriesSize; i < size; i++){
            y = dataArray.get(i);
            series.appendData(new DataPoint(i, y), true, size);
        }
        return size;

      //  graph.getViewport().setMaxX(maxX);

    }



    public void shutDown(){
        if (accelerometer != null){
            if (accelerometer.acceleration() != null){
                accelerometer.acceleration().stop();
            }
            accelerometer.stop();
        }
        if (board != null){
            board.tearDown();
            if (connectingCurrently || board.isConnected()){
                board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
                    @Override
                    public Void then(Task<Void> task) throws Exception {
                        Log.i("MainActivity", "Disconnected");
                        return null;
                    }
                });
            }
        }
        setStatusText("Disconnected");
        changeButtonByState(1,connectButton,"Connect");
        changeButtonByState(0,startDataStreamButton,"Start data stream");
        changeButtonByState(0,calibrationButton);
        runUpdater = false;
        streamingData = false;
        connectingCurrently = false;
        connectionAttemps = 0;
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
            shutDown();
        }
        //timerHandler.removeCallbacks(updater);

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);

    }

    void setStatusText(String str){
        if (statusTextView == null) return;
        statusTextView.setText("Status: "+str);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        retrieveBoard();
        bindSuccesful = true;
        setStatusText("Disconnected");
       // connectDevice();
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

    void startLinearMotionStream(){
        runUpdater = true;
        updateTime();
        streamingData = true;
        changeButtonByState(2,startDataStreamButton,"Stop data stream");
        setStatusText("Stream linear motion data");
        if (sensorFusion == null)  sensorFusion = board.getModule(SensorFusionBosch.class);

        /*
     //   Log.i("juttu",sensorFusion.linearAcceleration().toString());
        if ((accelerometer = board.getModule(Accelerometer.class)) != null) {
            Log.i("BlueActivity", "Accelerometer found");
            Log.i("BlueActivity", "Accelerometer range " + accelerometer.getRange());
            accelerometer.start();
        }
        Gyro gyro;
        if((gyro = board.getModule(Gyro.class)) != null){
            gyro.start();
        }

         */


        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_16G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_2000DPS)
                .commit();



       // AsyncDataProducer producer = sensorFusion.correctedAcceleration();
        AsyncDataProducer producer = sensorFusion.linearAcceleration();
        AsyncDataProducer eulerproducer = sensorFusion.eulerAngles();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Acceleration acc = (Acceleration) data.value(data.types()[0]);

                       // Log.i("Linear motion", "x: " + acc.x() + " y: " + acc.y() + " z: " + acc.z());
                        xData.add(acc.x() - accelometer_offsetX);
                        yData.add(acc.y() - accelometer_offsetY);
                        zData.add(acc.z() - accelometer_offsetZ);
                      //  int xAmount = xData.size();
                      //  series.appendData(new DataPoint(xAmount, xData.get(xAmount-1)), true, xAmount);
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                producer.start();
                sensorFusion.start();
                return null;
            }
        });

        eulerproducer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        EulerAngles angles = (EulerAngles) data.value(data.types()[0]);
                        Log.i("Heading",angles.heading()+"");
                        Log.i("pitch",angles.pitch()+"");
                        Log.i("roll",angles.roll()+"");
                        Log.i("yaw",angles.yaw()+"");

                        //  int xAmount = xData.size();
                        //  series.appendData(new DataPoint(xAmount, xData.get(xAmount-1)), true, xAmount);
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
            //    eulerproducer.start();
             //   sensorFusion.start();
                return null;
            }
        });

        sensorFusion.quaternion().addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("MainActivity", "Quaternion = " + data.value(Quaternion.class));
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                sensorFusion.quaternion().start();
                sensorFusion.start();
                return null;
            }
        });


/*

        AsyncDataProducer producer = sensorFusion.quaternion();
        Log.i("juttu", producer.toString());
        Log.i("juttu", SensorFusionBosch.AccRange.AR_2G.toString());

        sensorFusion.start();
        producer.start();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("MainActivity", "Quaternion = " + data.value(Quaternion.class));
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                sensorFusion.quaternion().start();
                sensorFusion.start();
                return null;
            }
        });
         */

    }

    void startColletingCalibrationDataLinearAcceleration() {
        runUpdater = false;
        streamingData = true;
        changeButtonByState(2,calibrationButton);
        setStatusText("Calibrating SensorFusion. Don't move the sensor.");
        accelometer_offSetDataSet.clear();
        xData.clear();
        yData.clear();
        zData.clear();
        calibratingCurrently = true;
        final int[] waitingRunner = {0};

        if (sensorFusion == null)  sensorFusion = board.getModule(SensorFusionBosch.class);

        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_16G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_2000DPS)
                .commit();

        AsyncDataProducer producer = sensorFusion.linearAcceleration();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {

                    @Override
                    public void apply(Data data, Object... env) {
                        if (waitingRunner[0] > 1000) {


                            Acceleration acc = (Acceleration) data.value(data.types()[0]);
                            Float x = acc.x();
                            Float y = acc.y();
                            Float z = acc.z();
                            List<Float> values = new ArrayList<>();

                            values.add(x);
                            values.add(y);
                            values.add(z);

                            accelometer_offSetDataSet.add(values);
                            if (accelometer_offSetDataSet.size() > 999) {
                                stopCollectingCalibrationDataLinearAcceleration();
                            }
                        }
                        waitingRunner[0]++;

                        //

                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                producer.start();
                sensorFusion.start();
                return null;
            }
        });
    }

    void stopCollectingCalibrationDataLinearAcceleration(){
        stopSensorFusionStream();

        calculateAccelerometerOffsets();
        changeButtonByState(1, calibrationButton);
        setStatusText("Calibrated SensorFusion data. Connected to " +board.getModel().toString());
        datadisplay.setText("x offset: "+accelometer_offsetX);
        datadisplay.append("\ny offset: " + accelometer_offsetY);
        datadisplay.append("\nz offset: " + accelometer_offsetZ);
    }

    void startAccelerationDataStreamWithOffset(){
        runUpdater = true;
        updateTime();
        changeButtonByState(2,startDataStreamButton,"Stop data stream");
        setStatusText("Streaming acceleration data with offset");
        streamingData = true;
        Log.i("offsets","x offset: " + accelometer_offsetX + " y offset: " + accelometer_offsetY + " z offset " + accelometer_offsetZ);
       // changeButtonByState(2,calibrationButton);
       // setStatusText("Calibrating accelerometer. Don't move the sensor.");
       // accelometer_offSetDataSet.clear();


        if ((accelerometer = board.getModule(Accelerometer.class)) != null) {
            Log.i("BlueActivity", "Accelerometer found");
            Log.i("BlueActivity", "Accelerometer range " + accelerometer.getRange());
            accelerometer.start();
        }

        Accelerometer.AccelerationDataProducer producer = accelerometer.acceleration();
        producer.start();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {

                    source.multicast().to().split().index(0).map(Function2.SUBTRACT, accelometer_offsetX).stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                            //Log.i("Data 0", "X-axis: " + data.value(Float.class) );
                            xData.add( data.value(Float.class) );
                        }
                    }).to().split().index(1).map(Function2.SUBTRACT, accelometer_offsetY).stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                           // Log.i("Data 1", "Y-axis: " + data.value(Float.class) );
                            yData.add( data.value(Float.class) );
                        }
                    }).to().split().index(2).map(Function2.SUBTRACT, accelometer_offsetZ).stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                           // Log.i("Data 2", "Z-axis: " + data.value(Float.class) );
                            zData.add( data.value(Float.class) );
                        }
                    }).to().account().stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                         //   Log.i("Data 3", "time?: " + data.formattedTimestamp());
                        }
                    });
/*


 */
            }
        });
    }

    void startAccelerationDataStreamWithOffsetNotSplit(){
        runUpdater = true;
        updateTime();
        streamingData = true;
        changeButtonByState(2,startDataStreamButton,"Stop data stream");
        setStatusText("Streaming acceleration data with offset");
        streamingData = true;
        Log.i("offsets","x offset: " + accelometer_offsetX + " y offset: " + accelometer_offsetY + " z offset " + accelometer_offsetZ);
        // changeButtonByState(2,calibrationButton);
        // setStatusText("Calibrating accelerometer. Don't move the sensor.");
        // accelometer_offSetDataSet.clear();


        if ((accelerometer = board.getModule(Accelerometer.class)) != null) {
            Log.i("BlueActivity", "Accelerometer found");
            Log.i("BlueActivity", "Accelerometer range " + accelerometer.getRange());
            accelerometer.start();
        }

        Accelerometer.AccelerationDataProducer producer = accelerometer.acceleration();
        producer.start();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Acceleration acc = (Acceleration) data.value(data.types()[0]);
                        /*
                        float x = acc.x();
                        float y = acc.y();
                        float z = acc.z();

                         */
                        xData.add(acc.x()-accelometer_offsetX);
                        yData.add(acc.y()-accelometer_offsetY);
                        zData.add(acc.z()-accelometer_offsetZ);

                        // String lol = data.formattedTimestamp();
                        //    String dataStr = Float.toString(x)+","+Float.toString(y)+","+Float.toString(z);
                        //  dataAsStrings.add(dataStr);
                    }
                });
            }
        });
    }

    public void startAccelerationDataStreamWithoutOffset() {
        runUpdater = true;
        updateTime();
        streamingData = true;
        changeButtonByState(2,startDataStreamButton,"Stop data stream");
        setStatusText("Streaming acceleration data without offset");
        if ((accelerometer = board.getModule(Accelerometer.class)) != null) {
            Log.i("BlueActivity", "Accelerometer found");
            Log.i("BlueActivity", "Accelerometer range " + accelerometer.getRange());
            accelerometer.start();
        }
        Accelerometer.AccelerationDataProducer producer = accelerometer.acceleration();
        producer.start();
        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Acceleration acc = (Acceleration) data.value(data.types()[0]);
                        /*
                        float x = acc.x();
                        float y = acc.y();
                        float z = acc.z();

                         */
                        xData.add(acc.x());
                        yData.add(acc.y());
                        zData.add(acc.z());

                       // String lol = data.formattedTimestamp();
                    //    String dataStr = Float.toString(x)+","+Float.toString(y)+","+Float.toString(z);
                      //  dataAsStrings.add(dataStr);
                    }
                });
            }
        });
    }

    void stopCollectingCalibrationData(){
        if (accelerometer != null){
            if (accelerometer.acceleration() != null){
                accelerometer.acceleration().stop();
            }
            accelerometer.stop();
        }
        if (board != null){
            board.tearDown();
        }
        streamingData = false;

        calculateAccelerometerOffsets();
        calibratingCurrently = false;
        changeButtonByState(1, calibrationButton);
        setStatusText("Calibrated accelerometer. Connected to " +board.getModel().toString());
        datadisplay.setText("x offset: "+accelometer_offsetX);
        datadisplay.append("\ny offset: " + accelometer_offsetY);
        datadisplay.append("\nz offset: " + accelometer_offsetZ);
    }
    void startColletingCalibrationData() {
        streamingData = true;
        changeButtonByState(2,calibrationButton);
        setStatusText("Calibrating accelerometer. Don't move the sensor.");
        accelometer_offSetDataSet.clear();
        xData.clear();
        yData.clear();
        zData.clear();


        if ((accelerometer = board.getModule(Accelerometer.class)) != null) {
            Log.i("BlueActivity", "Accelerometer found");
            Log.i("BlueActivity", "Accelerometer range " + accelerometer.getRange());
            accelerometer.start();
        }

        Accelerometer.AccelerationDataProducer producer = accelerometer.acceleration();
        producer.start();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {

                    @Override
                    public void apply(Data data, Object... env) {

                        if (!calibratingCurrently){
                            calibratingCurrently = true;
                            setStatusText("Getting calibration data");
                        }
                        Acceleration acc = (Acceleration) data.value(data.types()[0]);
                        Float x = acc.x();
                        Float y = acc.y();
                        Float z = acc.z();
                        List<Float> values = new ArrayList<>();

                        values.add(x);
                        values.add(y);
                        values.add(z);

                        accelometer_offSetDataSet.add(values);
                        if (accelometer_offSetDataSet.size() > 999){
                            stopCollectingCalibrationData();
                        }


                        //

                    }
                });
            }
        });
    }



    public void connectDevice(){
        setStatusText("Attempting connection");
        connectingCurrently = true;
        board.connectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                if (task.isFaulted()) {
                    Log.i("MainActivity", "BlueConnect failed to connect");
                    connectionAttemps++;
                    setStatusText("Connection failed. Retrying.. "+connectionAttemps);
                    //setStatusText("Connection failed");
                 //   statusTextView.setText("Connection failed");
                    if (connectionAttemps < 10){
                        connectDevice();
                    } else {
                        setStatusText("Connection failed.");
                        changeButtonByState(1, connectButton, "Connect");
                    }

                } else {
                    connectingCurrently = false;
                    changeButtonByState(1, calibrationButton);
                    changeButtonByState(1, startDataStreamButton);

                    String deviceName = board.getModel().toString();
                    setStatusText("Connected to " + deviceName);

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

                    //startAccelerationDataStreamWithOffset();

                    Log.i("metaboot mode", String.valueOf(board.inMetaBootMode()));


                    Led led;
                    if ((led= board.getModule(Led.class)) != null) {
                        led.editPattern(Led.Color.BLUE, Led.PatternPreset.BLINK)
                                .repeatCount((byte) 3)
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

    void updateTime() {
        datadisplay = findViewById(R.id.datadisplay);
        final Handler timerHandler = new Handler();

        updater = () -> {

            int index = xData.size()-1;
            // index = Math.min(index, yData.size()-1);
            // index = Math.min(index, zData.size()-1);
            if (index > 0){
                datadisplay.setText("x: " + df.format(xData.get(index)) + " \n");
                datadisplay.append("y: " + df.format(yData.get(index)) + " \n");
                datadisplay.append("z: " + df.format(zData.get(index)));
                seriesSize1 = plotData(series1, seriesSize1, xData);
                seriesSize2 = plotData(series2, seriesSize2, yData);
                seriesSize3 = plotData(series3, seriesSize3, zData);
            }
            /*
            String varib = dataAsStrings.get(dataAsStrings.size()-1);
            String[] parts = varib.split(",");

             */
            // Log.i("help", "x size: " + String.valueOf(xData.size()) + " y size " + String.valueOf(yData.size()) + " z size " + String.valueOf(zData.size()));


            if (runUpdater){
                timerHandler.postDelayed(updater,300);
            }
        };
        timerHandler.post(updater);
    }



    @Override
    public void onServiceDisconnected(ComponentName componentName) { }
}