package com.yupi.springbootinit.controller;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@Slf4j
@RequestMapping("/queue")
@Profile({"dev","local"})
public class QueueController {

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @GetMapping("/add")
    public boolean add(String name) {

        CompletableFuture.runAsync(() -> {
            log.info("任务执行中"+ name + ", 执行人:" + Thread.currentThread().getName());
            try {
                Thread.sleep(10000);
            }catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, threadPoolExecutor);
        return true;
    }

    @GetMapping("/get")
    public String get() {
        Map<String,Object> res = new HashMap<>();
        int size = threadPoolExecutor.getQueue().size();
        res.put("队列长度",size);

        long taskCount = threadPoolExecutor.getTaskCount();
        res.put("任务总数",taskCount);

        long completedTaskCount = threadPoolExecutor.getCompletedTaskCount();
        res.put("已经完成的任务数",completedTaskCount);

        int activeCount = threadPoolExecutor.getActiveCount();
        res.put("正在工作的线程数",activeCount);

        return JSONUtil.toJsonStr(res);
    }
}
