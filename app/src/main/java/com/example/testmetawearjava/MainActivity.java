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
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends Activity implements ServiceConnection {
    private BtleService.LocalBinder serviceBinder;

    // !!! IMPORTANT !!!
    // the mac address of the board is hardcoded atm. For testing purposes on your own board,
    // you can get the address of your own board using bluetooth scanners
    private final String MW_MAC_ADDRESS= "C3:70:17:BB:47:48";

    private int connectionAttempts = 0;

    private List<Float[]> accelerationData = new ArrayList<>();
    private Map<Integer, JSONObject> savedActivities = new HashMap<>();
    private DataContainer dataContainer = new DataContainer();


    private String userId = "TestiKayttaja";

    private TextView datadisplay;
    private TextView statusTextView;

    private TextView activityValue;
    private TextView countValue;
    private TextView successValue;
    private TextView timeValue;
    private TextView caloricValue;

    private Button connectButton;
    private Button startDataStreamButton;

    private boolean bindSuccesful = false;
    private boolean streamingData = false;
    private boolean connectingCurrently = false;

    private MetaWearBoard board;
    SensorFusionBosch sensorFusion;
    Accelerometer  accelerometer;

    Runnable updater;
    boolean runUpdater = true;
    DecimalFormat df = new DecimalFormat("#.####");
    Handler handler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        activityValue = findViewById(R.id.ActivityValue);
        countValue = findViewById(R.id.CountValue);
        successValue = findViewById(R.id.SuccessValue);
        timeValue = findViewById(R.id.TimeValue);
        caloricValue = findViewById(R.id.CaloriesValue);

        statusTextView = findViewById(R.id.connectionstatus);
        connectButton = findViewById(R.id.connect_button);
        changeButtonByState(1, connectButton);
        startDataStreamButton = findViewById(R.id.start_datastream_button);
        datadisplay = findViewById(R.id.datadisplay);
        datadisplay.setSingleLine(false);

        df.setRoundingMode(RoundingMode.CEILING);
        changeButtonByState(0, startDataStreamButton);
        Log.i("lol","lol");
       // OnActivityEnd();

        addListeners();
        handler.post(runnableCode);
    }


    // Define the code block to be executed
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {

            if (dataContainer.hasData()){


                for (DataSet dataSet : dataContainer.DataSets){
                    if (dataSet.HasBeenSent && dataSet.getDataAnalysis() == null && !dataSet.IsDataAnalysisBeingRequested){
                       RequestAnalysisByDataSet(dataSet);
                      //  RequestDataOfDataSet(dataSet);
                    }
                    if (!dataSet.IsBeingSent){
                        AttemptToPOSTData(dataSet);
                    }
                }
            }

            // upd every 3 seconds
            handler.postDelayed(runnableCode, 3000);
        }
    };

   /* void AttemptToSendData(int index){

        List<Float[]> data = dataContainer.getData(index);
        dataContainer.markSendingData(index, true);
        RequestQueue queue = Volley.newRequestQueue(this);
        String base_url ="http://koikka.work:5000/workFIT/"; //post endpoint?

        // test only!!
        Random rnd = new Random();
        int activityId = 10000 + rnd.nextInt(90000);
        Log.i("Activity cycle","Attempting sending activity data of size: " + data.size() + " with activity id: " +activityId);
        // test only!!

        String data_str = stringBuilderifyData(data);

        StringBuilder end_url = new StringBuilder();
        end_url.append(base_url);
        end_url.append("data?action=save_data&userId=" + userId + "&key=" + activityId + "&data=");
        end_url.append(data_str);

        JSONObject postData = new JSONObject();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, end_url.toString(), postData, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                dataContainer.removeData(index);
                printData(response);
                savedActivities.put(activityId, null);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                dataContainer.markSendingData(index, false);

            }
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(jsonObjectRequest);
    }

    */

    void AttemptToPOSTData(DataSet dataSet){

        dataSet.IsBeingSent = true;
        RequestQueue queue = Volley.newRequestQueue(this);
        String base_url ="https://koikka.work/workFIT/api.php"; //post endpoint


        JSONObject postData = dataSet.getPOSTData();

        Log.i("Activity cycle","Attempting sending activity data of size: " + dataSet.accelerationData.size() + " with activity id: " + dataSet.getActivityId());
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, base_url, postData,
            response -> {
                Log.i("Activity cycle", String.valueOf(response));
                try {
                    if(response.get("status").toString().equals("true") && response.get("msg").toString().equals("Data added")){
                        //dataContainer.removeData(dataSet);
                        dataSet.HasBeenSent = true;
                        Log.i("Activity cycle", "Data received by backend");
                    } else {
                        dataSet.IsBeingSent = false;
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }, error -> {
                error.printStackTrace();
                dataSet.IsBeingSent = false;
            });

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(jsonObjectRequest);
    }

    void RequestDataOfDataSet(DataSet dataSet){
        dataSet.IsDataAnalysisBeingRequested = true;
        Log.i("Activity cycle","Requesting activity by id " + dataSet.getActivityId());
        RequestQueue queue = Volley.newRequestQueue(this);
        //String url = "https://koikka.work/workFIT/api.php?action=get_data&userId=" + userId + "&key=" +dataSet.getActivityId();
        String url = "https://koikka.work/workFIT/api.php?action=get_data&userId=" + userId + "&key=" +"koira";
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    Log.i("Activity cycle","Received response");
                    Log.i("Activity cycle", String.valueOf(response));
                    Log.i("Activity cycle", dataSet.getPOSTData().toString());

                   // dataSet.setDataAnalysis(response);

                }, error -> {
            if (error.getMessage() != null) Log.i("Response is: ", error.getMessage());
            else {
                Log.e("ResponseError", "no response");
            }
            dataSet.IsDataAnalysisBeingRequested = false;
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(jsonObjectRequest);
    }

    void RequestAnalysisByDataSet(DataSet dataSet){
        dataSet.IsDataAnalysisBeingRequested = true;
        Log.i("Activity cycle","Requesting activity by id " + dataSet.getActivityId());
        RequestQueue queue = Volley.newRequestQueue(this);
       String url = "https://koikka.work/workFIT/api.php?action=get_status&userId=" + userId + "&key=" +dataSet.getActivityId();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    Log.i("Activity cycle","Received response");
                    Log.i("Activity cycle", String.valueOf(response));

                    dataSet.setDataAnalysis(response);
                    try {
                        SetDataOnUi(dataSet.getDataAnalysis());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                }, error -> {
            if (error.getMessage() != null) Log.i("Response is: ", error.getMessage());
            else {
                Log.e("ResponseError", "no response");
            }
            dataSet.IsDataAnalysisBeingRequested = false;
        });

        queue.add(jsonObjectRequest);
    }

    void RequestDataByActivityId(int activityId) {
        // Instantiate the RequestQueue.
        Log.i("Activity cycle","Requesting activity by id " + activityId);
        RequestQueue queue = Volley.newRequestQueue(this);

        String url = "https://koikka.work/workFIT/api.php?action=get_status&userId=" + userId + "&key=" +activityId;
/*
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.i("Activity cycle","Received response");
                        Log.i("Activity cycle",response);
                        try {
                            JSONObject reader = new JSONObject(response);
                            JSONObject data = reader.getJSONObject("data");
                            savedActivities.put(activityId, data);
                            SetDataOnUi(data);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error.getMessage() != null) Log.i("Response is: ", error.getMessage());
                else {
                    Log.e("ResponseError", "no response");
                }
            }
        });

 */

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    Log.i("Activity cycle","Received response");
                    Log.i("Activity cycle", String.valueOf(response));
                    try {
                        SetDataOnUi(response);
                        savedActivities.put(activityId, response);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }, error -> {
                    if (error.getMessage() != null) Log.i("Response is: ", error.getMessage());
                    else {
                        Log.e("ResponseError", "no response");
                    }
        });

        queue.add(jsonObjectRequest);

    }



    // add button listeners
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
    }

    // handles UI inputs for connecting and disconnecting to board
    // and changes the UI to represent connection states
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
                connectionAttempts = 0;
                changeButtonByState(2,connectButton, "Disconnect");
            } else {
                shutDown();
            }
        }
    }

    // handles UI inputs for starting and stopping the data stream
    // and changes UI to represent data stream states
    void startDataStreamButtonListener(){
        if (board == null) return;
        if (board.isConnected()){
            if (!streamingData){
                startLinearMotionStream();
            } else {

                stopSensorFusionStream();
                OnActivityEnd();
                changeButtonByState(1,startDataStreamButton,"Start data stream");

                if (board.isConnected()) setStatusText("Connected to "+ board.getModel().toString());
                else
                    setStatusText("Disconnected");
            }
        }
    }

    void OnActivityEnd(){

        //TestCall();
        //GenerateRandomAccelerationData();
        Log.i("Activity cycle","Ending activity");
        //SendActivityData();
        AddDataToSendQueue();

    }

    private void AddDataToSendQueue(){
      //  dataContainer.addData(accelerationData);
        Random rnd = new Random();
        int activityId = 10000 + rnd.nextInt(90000);
        dataContainer.addDataSet(new DataSet(accelerationData, activityId, userId));

        accelerationData = new ArrayList<>();
    }

    private void GenerateRandomAccelerationData(){
        Random rnd = new Random();
        for (int i = 0; i < 10000; i++){
            float x = rnd.nextFloat();
            float y = rnd.nextFloat();
            float z = rnd.nextFloat();
            accelerationData.add(new Float[]{x,y,z});
        }

        Log.i("Generated values","first value: " + accelerationData.get(0)[0]+ " "+ accelerationData.get(0)[1]+ " "+ accelerationData.get(0)[2]+ " last value: "+ accelerationData.get(accelerationData.size()-1)[0]+ " "+ accelerationData.get(accelerationData.size()-1)[1]+ " "+ accelerationData.get(accelerationData.size()-1)[2]);
    }


    private void SendActivityData(){
        RequestQueue queue = Volley.newRequestQueue(this);
        String base_url ="http://koikka.work:5000/workFIT/"; //post endpoint?


        // test only!!
        Random rnd = new Random();
        int activityId = 10000 + rnd.nextInt(90000);
        Log.i("Activity cycle","Sending activity data of size: " + accelerationData.size() + " with activity id: " +activityId);

        String actId = Integer.toString(activityId);
        String data = "";
        String data_str = stringBuilderifyData();
       // data = stringifyData();

        //Log.i("hevonen",data_str);
        // test only!!
        StringBuilder end_url = new StringBuilder();
        end_url.append(base_url);
        end_url.append("data?action=save_data&userId=" + userId + "&key=" + actId + "&data=");
        end_url.append(data_str);
       // String use_url = base_url + "data?action=save_data&userId=" + userId + "&key=" + actId + "&data=" + data;
       // Log.i("Hevonen", use_url);

        JSONObject postData = new JSONObject();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, end_url.toString(), postData, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
               // Log.i("Hevonen","response length " + response.length());
               // System.out.println(response);
                printData(response);
                savedActivities.put(activityId, null);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(jsonObjectRequest);

/*
        JSONObject postData = new JSONObject();


        try{
            postData.put("activity_id", activityId);
            JSONArray dataArray = new JSONArray();

            for (int i = 0; i < accelerationData.size(); i++){
                JSONArray dataPoint = new JSONArray();
                dataPoint.put(accelerationData.get(i)[0]);
                dataPoint.put(accelerationData.get(i)[1]);
                dataPoint.put(accelerationData.get(i)[2]);
                dataArray.put(dataPoint);
            }
            postData.put("data", dataArray);
            Log.i("data JSON", String.valueOf(postData));
        } catch (Exception e){

        }

 */

    }

    private void printData(JSONObject obj){
        try {
            JSONArray lol = obj.getJSONArray("data");

                Log.i("testi",""+lol);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
    private String stringBuilderifyData(List<Float[]> data){
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < data.size(); i++){
            result.append("[");
            result.append(data.get(i)[0]);
            result.append(";");
            result.append(data.get(i)[1]);
            result.append(";");
            result.append(data.get(i)[2]);
            result.append("],");
        }

        //result = result.substring(0, result.length()-1);
        return result.substring(0, result.length()-1);

    }

    private String stringBuilderifyData(){
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < accelerationData.size(); i++){
            result.append("[");
            result.append(accelerationData.get(i)[0]);
            result.append(";");
            result.append(accelerationData.get(i)[1]);
            result.append(";");
            result.append(accelerationData.get(i)[2]);
            result.append("],");
        }

        //result = result.substring(0, result.length()-1);
        return result.substring(0, result.length()-1);

    }

    private String stringifyData(){
        String result = "";
        for (int i = 0; i < accelerationData.size(); i++){
            result += "[";
            result += accelerationData.get(i)[0];
            result += ";";
            result += accelerationData.get(i)[1];
            result += ";";
            result += accelerationData.get(i)[2];
            result += "],";
        }
        result = result.substring(0, result.length()-1);
        return result;
    }

    void TestCall(){
        // Instantiate the RequestQueue.
        Log.i("Activity ended","here");
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://koikka.work:5000/workFIT/get_status?userId=q";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.i("Response", response);
                        try {
                            JSONObject reader = new JSONObject(response);
                            JSONObject data = reader.getJSONObject("data");
                            SetDataOnUi(data);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error.getMessage() != null) Log.i("Response is: ", error.getMessage());
                else{
                    Log.e("ResponseError", "no response");
                }
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }


    void SetDataOnUi(JSONObject data) throws JSONException {
        String activityId = data.getString("activity_id");
        String activityTimeSpent = data.getString("time_spent_splitting_wood");
        String activityName = data.getString("activity");
        String activityRepeatCount = data.getString("hit_times");
        String activityDate = data.getString("time");
        String activitySuccessCount = data.getString("wood_split");
        String activityCaloricCount = data.getString("kcal");

        activityValue.setText(activityName);
        countValue.setText(activityRepeatCount);
        successValue.setText(activitySuccessCount);
        timeValue.setText(activityTimeSpent);
        caloricValue.setText(activityCaloricCount);

    }

    // retrieveBoard()
    //      attempts to find the board using bluetooth service and binds it
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

    // connectDevice()
    //      attempts connection to device
    //      adds listeners to board
    public void connectDevice(){
        setStatusText("Attempting connection");
        connectingCurrently = true;

        // tries to connect to the device until success or 10 times
        board.connectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                if (task.isFaulted()) {
                    Log.i("MainActivity", "BlueConnect failed to connect");
                    connectionAttempts++;
                    setStatusText("Connection failed. Retrying.. "+connectionAttempts);

                    if (connectionAttempts < 10){
                        connectDevice();
                    } else {
                        setStatusText("Connection failed.");
                        changeButtonByState(1, connectButton, "Connect");
                    }
                } else {

                    // if connection successful assign some variables used in logics,
                    // change the ui, blink the board led, print out some info
                    connectingCurrently = false;
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

    // startLinearMotionStream()
    //      Starts a linear acceleration data producer using sensorFusion on board
    //      in NDOF-mode ( https://mbientlab.com/androiddocs/latest/sensor_fusion.html )
    //      which should give xyz data in absolute orientaion (doesn't work tough).
    //      Also has commented out euler data producer and quaternion producers.
    //      Changes the UI to represent that data stream is going on.
    //      Note: should again separate UI and logic
    void startLinearMotionStream(){
        Log.i("Activity cycle","Starting activity record");
      //  accelerationData = new ArrayList<>();
        runUpdater = true;
        updateTime();
        streamingData = true;
        changeButtonByState(2,startDataStreamButton,"Stop data stream");
        setStatusText("Stream linear motion data");
        if (sensorFusion == null) sensorFusion = board.getModule(SensorFusionBosch.class);

        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_16G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_2000DPS)
                .commit();

        AsyncDataProducer producer = sensorFusion.linearAcceleration();
        AsyncDataProducer eulerproducer = sensorFusion.eulerAngles();

        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Acceleration acc = (Acceleration) data.value(data.types()[0]);

                        /*
                        xData.add(acc.x());
                        yData.add(acc.y());
                        zData.add(acc.z());
                         */

                        accelerationData.add(new Float[]{acc.x(), acc.y(), acc.z()});

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

        /*
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
         */
    }


    // stops sensorFusion data stream
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
        runUpdater = false;
        streamingData = false;
        connectingCurrently = false;
    }

    // kills all data streams and updates the UI and logics
    // note: ui and logic should be separated to different functions
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
        runUpdater = false;
        streamingData = false;
        connectingCurrently = false;
        connectionAttempts = 0;
    }

    // set status text
    void setStatusText(String str){
        if (statusTextView == null) return;
        statusTextView.setText("Status: "+str);
    }

    // Changes button style and text in UI
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

    // Changes button style in UI
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

    // updates ui for data gotten from data stream every 300ms
    void updateTime() {
        datadisplay = findViewById(R.id.datadisplay);
        final Handler timerHandler = new Handler();

        updater = () -> {

            int index = accelerationData.size()-1;

            if (index > 0){
                Float[] data = accelerationData.get(index);
                datadisplay.setText("x: " + df.format(data[0]) + " \n");
                datadisplay.append("y: " + df.format(data[1]) + " \n");
                datadisplay.append("z: " + df.format(data[2]) );
            }

            if (runUpdater){
                timerHandler.postDelayed(updater,300);
            }
        };
        timerHandler.post(updater);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (board != null){
            shutDown();
        }
        getApplicationContext().unbindService(this);

    }
    @Override
    public void onServiceDisconnected(ComponentName componentName) { }
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        retrieveBoard();
        bindSuccesful = true;
        setStatusText("Disconnected");
    }




}