package com.example.testmetawearjava;

import java.util.ArrayList;
import java.util.List;

public class DataContainer {

    public List<DataSet> DataSets;

    public DataContainer(){
        DataSets = new ArrayList<>();
    }

    public void addDataSet(DataSet dataSet){
        DataSets.add(dataSet);
    }

    public void removeData(DataSet dataSet){
        DataSets.remove(dataSet);
    }

    public boolean hasData(){
        return (DataSets.size() > 0);
    }

    public int dataCount(){
        return DataSets.size();
    }


}
