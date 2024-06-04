package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.springbootinit.model.entity.Chart;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

/**
* @author T041018
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2024-05-19 15:33:24
* @Entity generator.domain.Chart
*/
public interface ChartMapper extends BaseMapper<Chart> {

    List<Map<String,Object>> queryDataByRow(Map<String,Object> parameters);

    void createTable(Map<String, Object> parameters);

    void insertToChart(Map<String,Object> parameters);

    List<Map<String,Object>> queryAllData(String tableName);
}




