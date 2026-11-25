package com.campus.platform.module.idle.service;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.IAiGatewayService;
import com.campus.platform.module.idle.vo.IdleEstimateVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IdleEstimateService 服务接口。
 */
public interface IIdleEstimateService {

    IdleEstimateVO estimate(Long userId, String title, String description, String category, String expectItem);
}
