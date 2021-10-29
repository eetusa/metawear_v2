package com.example.testmetawearjava;

import java.util.ArrayList;
import java.util.List;

public class DataContainer {
    private List<List<Float[]>> alldata;
    private List<Boolean> isDataSending;
    public int count;

    public DataContainer(){
        this.alldata = new ArrayList<>();
        this.count = 0;
        this.isDataSending = new ArrayList<>();
    }

    public void addData(List<Float[]> data){
        alldata.add(data);
        count = alldata.size();
        isDataSending.add(false);
    }

    public boolean isDataSending(int index){
        return isDataSending.get(index);
    }

    public void markSendingData(int index, boolean value){
        isDataSending.set(index, value);
    }

    public List<Float[]> getData(int index){
        return alldata.get(index);
    }

    public boolean removeData(int index){
        if (count > index){
            alldata.remove(index);
            isDataSending.remove(index);
            count = alldata.size();
            return true;
        }
        return false;
    }

    public boolean hasData(){
        return alldata.size()>0;
    }
}
