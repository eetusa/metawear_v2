package com.example.testmetawearjava;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class DataSet {
    public boolean IsBeingSent;
    public boolean HasBeenSent;
    public boolean IsDataAnalysisBeingRequested;

    public List<Float[]> accelerationData;
    private JSONObject POSTdata;
    private int activityId;
    private String userId;
    private JSONObject dataAnalysis;


    public DataSet(List<Float[]> accelerationData, int activityId, String userId){
        this.accelerationData = accelerationData;
        this.activityId = activityId;
        this.userId = userId;
        POSTdata = new JSONObject();
        HasBeenSent = false;
        IsBeingSent = false;
        IsDataAnalysisBeingRequested = false;
        CreateJSONObject();
    }

    public JSONObject getPOSTData(){
        return POSTdata;
    }

    public void setDataAnalysis(JSONObject dataAnalysis){
        this.dataAnalysis = dataAnalysis;
        IsDataAnalysisBeingRequested = false;
    }

    public JSONObject getDataAnalysis(){
        return dataAnalysis;
    }

    public int getActivityId(){
        return activityId;
    }

    private void CreateJSONObject(){
        try {
            POSTdata.put("action","save_data");
            POSTdata.put("userId", userId);
            POSTdata.put("key",Integer.toString(activityId));

            JSONArray dataArray = new JSONArray();
            for (int i = 0; i < accelerationData.size(); i++){
                JSONArray dataPoint = new JSONArray();
                dataPoint.put(accelerationData.get(i)[0]);
                dataPoint.put(accelerationData.get(i)[1]);
                dataPoint.put(accelerationData.get(i)[2]);
                dataArray.put(dataPoint);
            }
            POSTdata.put("data", dataArray);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
