package com.campus.platform.module.admin;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.audit.AdminOperationAspect;
import com.campus.platform.module.admin.audit.AdminPermission;
import com.campus.platform.module.admin.controller.AdminAiController;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.admin.entity.AdminOperationLog;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.admin.service.AdminOperationLogService;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.admin.service.AiAdminService;
import com.campus.platform.module.ai.dto.AiConfigUpdateDTO;
import com.campus.platform.utils.RedisUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminOperationAspectTest {
    private final AdminMapper admins = mock(AdminMapper.class);
    private final AdminPermissionService permissions = new AdminPermissionService(admins, mock(RedisUtils.class));
    private final AdminOperationLogService logs = mock(AdminOperationLogService.class);
    private final AiAdminService configs = mock(AiAdminService.class);
    private final Admin admin = new Admin();
    private AdminAiController controller;

    @BeforeEach
    void setUp() {
        admin.setId(42L);
        admin.setUsername("qa-admin");
        admin.setStatus(0);
        UserContext.set(admin.getId(), "admin");
        when(admins.selectById(admin.getId())).thenReturn(admin);

        var factory = new AspectJProxyFactory(new AdminAiController(configs));
        factory.addAspect(new AdminOperationAspect(permissions, logs));
        controller = factory.getProxy();
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void auditRoleCannotChangeAiSettingsAndDeniedActionIsRecorded() {
        admin.setRole("audit");
        assertThat(permissions.permissionsFor(admin))
                .contains(AdminPermission.CONTENT_REVIEW.getCode(), AdminPermission.NOTICE_WRITE.getCode())
                .doesNotContain(AdminPermission.AI_CONFIG_EDIT.getCode(), AdminPermission.ACTIVITY_EXPORT.getCode());

        assertThatThrownBy(() -> controller.updateConfigs(configRequest()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        verifyNoInteractions(configs);

        var entry = capturedEntry();
        assertThat(entry.isSucceeded()).isFalse();
        assertThat(entry.getResultCode()).isEqualTo(403);
        assertThat(entry.getAdminId()).isEqualTo(admin.getId());
        assertThat(entry.getAction()).isEqualTo("update-config");
    }

    @Test
    void superRoleCanChangeAiSettingsAndLogOnlyContainsMetadata() {
        admin.setRole("super");

        assertThat(controller.updateConfigs(configRequest()).getCode()).isEqualTo(200);
        verify(configs).updateConfigs(Map.of("api_key", "synthetic-test-key"));

        var entry = capturedEntry();
        assertThat(entry.isSucceeded()).isTrue();
        assertThat(entry.getModule()).isEqualTo("ai");
        assertThat(entry.getSource()).isEqualTo("manual");
        assertThat(entry.toString()).doesNotContain("synthetic-test-key", "api_key");
    }

    private AdminOperationLog capturedEntry() {
        var entry = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logs).record(entry.capture());
        return entry.getValue();
    }

    private AiConfigUpdateDTO configRequest() {
        var request = new AiConfigUpdateDTO();
        request.setConfigs(Map.of("api_key", "synthetic-test-key"));
        return request;
    }
}
