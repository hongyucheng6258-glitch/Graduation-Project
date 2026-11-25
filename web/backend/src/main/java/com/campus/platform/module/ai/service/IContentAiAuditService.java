package com.campus.platform.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.platform.module.ai.entity.AiAuditResult;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.gateway.IAiGatewayService;
import com.campus.platform.common.Constants;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ContentAiAuditService 服务接口。
 */
public interface IContentAiAuditService {

    void audit(String type, Object content, Long userId, String title, String body);

    void audit(String type, Object content, Long userId, String title, String body, String idleRevision);

    void applyDecision(String type, Object content, AiAuditResult result);
}
