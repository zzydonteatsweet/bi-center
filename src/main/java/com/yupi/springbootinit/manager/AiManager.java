package com.yupi.springbootinit.manager;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class AiManager {
    @Resource
    private YuCongMingClient client;

    public String doChat(long modelId, String data) {
//        System.out.println(data);
        DevChatRequest devChatRequest = new DevChatRequest();
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(data);

        BaseResponse<DevChatResponse> response = client.doChat(devChatRequest);
        if(response == null) {
            throw  new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 响应错误");
        }

        ThrowUtils.throwIf(ObjectUtils.isNull(response.getData().getContent()), ErrorCode.SYSTEM_ERROR,
                "返回结果为空");
        if(response.getData().getContent() == null) {
            return response.getMessage();
        }else
            return response.getData().getContent();
    }

}
