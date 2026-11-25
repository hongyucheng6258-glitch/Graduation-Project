package com.campus.platform.module.ai.gateway;

import com.campus.platform.common.mapper.MapperQueries;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.entity.PdfDocument;
import com.campus.platform.module.ai.entity.PromptTemplate;
import com.campus.platform.module.ai.mapper.AiCallLogMapper;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.mapper.PromptTemplateMapper;
import com.campus.platform.utils.RedisUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.BufferedSource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AiGatewayService 服务接口。
 */
public interface IAiGatewayService {

    String chat(Long userId, String scene, String question, Long sessionId, Map<String, String> params);

    String internalChat(Long userId, String scene, String question, Map<String, String> params);

    SseEmitter streamChat(Long userId, String scene, String question, Long sessionId, Map<String, String> params);

    boolean isAiConfigured();
}
