package com.yupi.springbootinit.utils;

import com.yupi.springbootinit.mapper.ChartMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Component
public class ChartUtils {
    @Resource
    ChartMapper chartMapper;

    public boolean saveData(String rawData, Long id) {
        String chartName = String.format("chart_%s", id.toString());

        String[] data = rawData.split("\n");
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("tableName", chartName);

        List<Map<String,String>> columns = new ArrayList<>();
        List<String> columnName = new ArrayList<>();
        String[] names = data[0].split(",");
        for (int i = 0 ; i < names.length ; i ++) {
            Map<String,String> mp = new HashMap<>();
            mp.put("name", names[i]);
            columnName.add(names[i]);
            mp.put("type", "text");
            columns.add(mp);
        }
        parameters.put("columns", columns);
        parameters.put("columnName", columnName);
        System.out.println("chartUtils\n" + parameters);
        try {
            chartMapper.createTable(parameters);
        }catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        for(int i = 1 ; i < data.length ; i ++) {
            String[] rawValues = data[i].split(",");
            List<String> values = new ArrayList<>(Arrays.asList(rawValues));
            parameters.put("values", values);
            try {
                chartMapper.insertToChart(parameters);
            }catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }


}
