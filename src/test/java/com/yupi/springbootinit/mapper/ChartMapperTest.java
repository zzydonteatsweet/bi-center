package com.yupi.springbootinit.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ChartMapperTest {

    @Resource
    private ChartMapper chartMapper;

    @Test
    void queryChartData() {
        if(chartMapper == null) {
            System.out.println("Null");
        }else System.out.println("Not Null");
        String chartId = "2";
        String querySql = String.format("select * from chart_%s", chartId);
        List<Map<String,Object>> resultData = chartMapper.queryChartData(querySql);
        System.out.println(resultData);
    }

    @Test
    void createTable() {
        Map<String, Object> parameters = new HashMap<>();

        parameters.put("tableName", "test2");

        List<Map<String,String>> columns = new ArrayList<>();
        for(int i = 1 ; i < 4 ; i ++) {
            Map<String,String> mp = new HashMap<>();
            String name = String.format("name%s",String.valueOf(i));
            mp.put("name", name);
            String type = new String("text");
            mp.put("type", type);
            columns.add(mp);
        }
//                (Map.of("name","name1", "type","text"),
//                Map.of("name","name2", "type","text"),
//                Map.of("name", "name3", "type", "text")
//        );
        parameters.put("columns", columns);
        System.out.println(parameters);
        chartMapper.createTable(parameters);

    }

    @Test
    void insertToTableTest() {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("tableName","chart_6");
        List<String> columnName = Arrays.asList("1月","2月","3月","4月","5月");
        List<String> values = Arrays.asList("12", "13","14", "18", "12");
        parameters.put("columnName", columnName);
        parameters.put("values", values);

        chartMapper.insertToChart(parameters);
    }
}