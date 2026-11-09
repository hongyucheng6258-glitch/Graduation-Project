package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AdminAssistantAccess {
    private final AdminPermissionService permissions;
    private final RedisUtils redis;
    private final AiConfigHolder config;

    @Value("${CAMPUS_ADMIN_ASSISTANT_ENABLED:false}")
    private boolean enabled;

    public boolean enabled() { return enabled; }

    public Admin requireAccess() {
        if (!enabled) throw new BizException(ResultCode.AI_NOT_CONFIGURED, "管理智能助手尚未启用");
        var admin = permissions.requireActive(UserContext.getUid());
        if (!"audit".equals(admin.getRole()) && !"super".equals(admin.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return admin;
    }

    public Admin requireSuper() {
        var admin = requireAccess();
        if (!"super".equals(admin.getRole())) throw new BizException(ResultCode.FORBIDDEN, "仅超级管理员可维护内部资料");
        return admin;
    }

    public void charge(Long adminId) {
        Long used = redis.incr("admin:assistant:quota:" + adminId + ":" + LocalDate.now(), 1, TimeUnit.DAYS);
        if (used == null || used > config.getRateLimitPerDay()) {
            throw new BizException(ResultCode.RATE_LIMITED, "今日管理助手调用次数已达上限");
        }
    }
}
