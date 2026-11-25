package com.campus.platform.module.ai.service;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.dto.AssistComposeDTO;
import com.campus.platform.module.ai.dto.AssistPolishDTO;
import com.campus.platform.module.ai.gateway.IAiGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AiAssistService 服务接口。
 */
public interface IAiAssistService {

    Map<String, Object> compose(Long userId, AssistComposeDTO dto);

    String polish(Long userId, AssistPolishDTO dto);
}
