package com.yupi.springbootinit;

import javax.annotation.Resource;

import com.yupi.springbootinit.manager.AiManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 主类测试
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@SpringBootTest
class MainApplicationTests {

    @Resource
    private AiManager aiManager;

    @Test
    void contextLoads() {

    }

    @Test
    void testAiInterface() {
        long modelId = 1659171950288818178L;
        String data = new String("分析需求:\n" +
                "用户增长情况\n"+
                "原始数据:\n"+
                "日期,用户数\n"+
                "1号,10\n"+
                "2号,20"+
                "3号,30"
                );
        System.out.println(aiManager.doChat(modelId, data));
    }


}
