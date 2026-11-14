package com.campus.platform.module.admin.audit;

import com.campus.platform.common.BizException;
import com.campus.platform.common.R;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.entity.AdminOperationLog;
import com.campus.platform.module.admin.service.AdminOperationLogService;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.ai.entity.PromptTemplate;
import com.campus.platform.module.notice.entity.Notice;
import com.campus.platform.module.site.entity.SystemConfig;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class AdminOperationAspect {
    private static final Set<String> REVIEW_TYPES = Set.of("idle", "activity", "lostfound", "post", "partner");

    private final AdminPermissionService permissions;
    private final AdminOperationLogService logs;

    @Around("@annotation(operation)")
    public Object authorizeAndRecord(ProceedingJoinPoint call, AdminOperation operation) throws Throwable {
        var admin = permissions.currentAdmin();
        var entry = new AdminOperationLog();
        entry.setAdminId(admin.getId());
        entry.setAdminUsername(admin.getUsername());
        entry.setModule(operation.module());
        entry.setAction(operation.action());
        entry.setSource(operation.assistant() ? "assistant" : "manual");
        var args = call.getArgs();
        if (operation.idArgument() >= 0 && args[operation.idArgument()] instanceof Long id) {
            entry.setTargetId(id);
        }
        if ("audit".equals(operation.module()) && !operation.assistant()
                && args[0] instanceof String type && REVIEW_TYPES.contains(type)) {
            entry.setTargetType(type);
        }

        long start = System.nanoTime();
        try {
            permissions.check(admin, operation.permission());
            Object response = call.proceed();
            int code = response instanceof R<?> result ? result.getCode() : ResultCode.SUCCESS.getCode();
            entry.setResultCode(code);
            entry.setSucceeded(code == ResultCode.SUCCESS.getCode().intValue());
            if (entry.isSucceeded() && response instanceof R<?> result) {
                readResultTarget(entry, result.getData());
            }
            return response;
        } catch (Throwable failure) {
            entry.setResultCode(failure instanceof BizException biz ? biz.getCode() : ResultCode.SYSTEM_ERROR.getCode());
            throw failure;
        } finally {
            entry.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            entry.setCreateTime(LocalDateTime.now());
            logs.record(entry);
        }
    }

    private void readResultTarget(AdminOperationLog entry, Object data) {
        if (data instanceof AdminOperationTarget target) {
            entry.setTargetId(numericId(target.operationTargetId()));
            String type = target.operationTargetType();
            if (type != null && REVIEW_TYPES.contains(type)) entry.setTargetType(type);
            if ("PASS".equals(target.operationDecision())) entry.setAction("pass");
            if ("REJECT".equals(target.operationDecision())) entry.setAction("reject");
        } else if (data instanceof Notice notice) {
            entry.setTargetId(notice.getId());
        } else if (data instanceof PromptTemplate prompt) {
            entry.setTargetId(prompt.getId());
        } else if (data instanceof SystemConfig config) {
            entry.setTargetId(config.getId());
        }
    }

    private Long numericId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) return null;
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
