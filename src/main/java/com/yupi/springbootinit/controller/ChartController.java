package com.yupi.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.FileConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.CosManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.mapper.ChartMapper;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.dto.file.UploadFileRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.FileUploadBizEnum;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ChartUtils;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    AiManager aiManager;

    @Resource
    private CosManager cosManager;

    @Resource
    private ChartMapper chartMapper;

    @Resource
    private ChartUtils chartUtils;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    private final static Gson GSON = new Gson();

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }


    /**
     * 上传文件
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<BiResponse> uploadFile(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest uploadFileRequest,
                                               HttpServletRequest request) {
        String name = uploadFileRequest.getName();
        String goal = uploadFileRequest.getGoal();
        String chartType = uploadFileRequest.getChartType();

        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length()>100
                ,ErrorCode.PARAMS_ERROR, "名字为空");

        long fileSize = multipartFile.getSize();
        String fileName = multipartFile.getOriginalFilename();

        final Long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.PARAMS_ERROR);

        String sufix = FileUtil.getSuffix(fileName);
        final List<String> validSufixList = Arrays.asList( "xlsx", "csv");

        ThrowUtils.throwIf(!validSufixList.contains(sufix), ErrorCode.PARAMS_ERROR);

        StringBuilder userInput = new StringBuilder();
        userInput.append("你是一个数据分析师，接下来我会给你分析目标和原始数据，请告诉我分析结论").append("\n");
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用"+chartType+"进行分析";
        }

        userInput.append("分析目标: ").append(userGoal).append("\n");
        String data = ExcelUtils.excel2Csv(multipartFile);
        userInput.append("原始数据: ").append(data).append("\n");

        //  人数每一行都有\n隔开
//        System.out.println(data);
        User loginUser = userService.getLoginUser(request);

        redisLimiterManager.doRateLimit("genChartByAi" + loginUser.getId().toString());

        long biModeId = 1659171950288818179L;

        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
        chart.setUserId(loginUser.getId());
        chart.setCreateTime(new Date());
        chart.setUpdateTime(new Date());

        boolean chartSaveResult = chartService.save(chart);
        ThrowUtils.throwIf(!chartSaveResult, ErrorCode.SYSTEM_ERROR, "图标保存失败");

        String res = aiManager.doChat(biModeId, userInput.toString());

        String splits[] = StringUtils.split(res, "【【【【【");

        for(String t : splits) {
            System.out.println(t);
        }
        if(splits.length < 2) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }

        String genChart = splits[0].trim();
        String genResult = splits[1].trim();

        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        boolean saveResult = chartService.updateById(chart);


        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        boolean saveDataResult = chartUtils.saveData(data, chart.getId());
        ThrowUtils.throwIf(!saveDataResult, ErrorCode.SYSTEM_ERROR, "数据保存数据库失败");

        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());

        return ResultUtils.success(biResponse);
    }

    @PostMapping("/upload_async")
    public BaseResponse<Long> uploadFileAsync(@RequestPart("file") MultipartFile multipartFile,
                                                     GenChartByAiRequest genChartByAiRequest,
                                                     HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();


        //  检验目标和姓名是否为空
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");

        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length()>100
                ,ErrorCode.PARAMS_ERROR, "名字为空");

        //  检验文件大小
        long fileSize = multipartFile.getSize();
        String fileName = multipartFile.getOriginalFilename();
        final Long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.PARAMS_ERROR);

        //   检验文件后缀
        String sufix = FileUtil.getSuffix(fileName);
        final List<String> validSufixList = Arrays.asList( "xlsx", "csv");
        ThrowUtils.throwIf(!validSufixList.contains(sufix), ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);

        Long userId = loginUser.getId();

        redisLimiterManager.doRateLimit("任务 " + userId);

        long biModeId = 1659171950288818178L;

        //  对表进行存储
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
        chart.setUserId(loginUser.getId());
        chart.setCreateTime(new Date());
        chart.setUpdateTime(new Date());

        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "存储失败");

        //  设定用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("你是一个数据分析师，接下来我会给你分析目标和原始数据，请告诉我分析结论").append("\n");
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用"+chartType+"进行分析";
        }

        userInput.append("分析目标: ").append(userGoal).append("\n");
        String data = ExcelUtils.excel2Csv(multipartFile);
        userInput.append("原始数据: ").append(data).append("\n");

        CompletableFuture.runAsync(() -> {
            //  改变状态
            Chart updatedChart = new Chart();
            updatedChart.setId(chart.getId());
            updatedChart.setStatus("running");
            updatedChart.setUpdateTime(new Date());
            boolean updateStatusResult = chartService.updateById(updatedChart);

            if(!updateStatusResult) {
                handleChartUpdatedFailed(updatedChart.getId(), "保存图表失败");
                return;
            }

            //  保存AI生成结果
            String res = aiManager.doChat(biModeId, userInput.toString());
            String splits[] = StringUtils.split(res, "【【【【【");
            System.out.println("the length of splits is " + splits.length);

            if(splits.length < 2) {
                handleChartUpdatedFailed(updatedChart.getId(), "AI生成错误");
                return ;
            }

            //  保存结果
            String genChart = splits[0].trim();
            String genResult = splits[1].trim();

            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            updateChartResult.setStatus("succeed");
            boolean updateChartInfo = chartService.updateById(updateChartResult);
            if(!updateChartInfo) {
                handleChartUpdatedFailed(chart.getId(), "结果保存失败");
                return ;
            }
        });

        return ResultUtils.success(chart.getId());
    }

    private void handleChartUpdatedFailed(long chartId, String exeMessage) {
        Chart updatedFailedChart = new Chart();
        updatedFailedChart.setId(chartId);
        updatedFailedChart.setStatus("Failed");
        updatedFailedChart.setExeMessage(exeMessage);
        boolean result = chartService.updateById(updatedFailedChart);
        if(!result) {
            log.error("更新图表失败"+chartId+","+exeMessage);
        }
    }
    /**
     * 验证文件
     * @param multipartFile
     * @param fileUploadBizEnum
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


}
