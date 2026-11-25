package com.campus.platform.module.admin;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.admin.service.AdminPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 管理员有效性校验：管理员被删除或禁用后，拦截器必须立即拒绝其会话。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminPermissionService 管理员有效性校验")
class AdminPermissionServiceTest {

    @Mock
    private AdminMapper adminMapper;

    @InjectMocks
    private AdminPermissionService adminPermissionService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Admin admin(long id, String role, Integer status) {
        Admin a = new Admin();
        a.setId(id);
        a.setUsername("u" + id);
        a.setRole(role);
        a.setStatus(status);
        return a;
    }

    @Nested
    @DisplayName("requireActive 管理员存在且可用")
    class RequireActive {

        @Test
        @DisplayName("正常管理员应通过")
        void activeAdmin_shouldPass() {
            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "audit", 0));

            assertThat(adminPermissionService.requireActive(5L).getRole()).isEqualTo("audit");
        }

        @Test
        @DisplayName("管理员不存在应 401")
        void missingAdmin_shouldReject() {
            when(adminMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminPermissionService.requireActive(999L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
        }

        @Test
        @DisplayName("管理员被禁用应 401")
        void disabledAdmin_shouldReject() {
            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "audit", 1));

            assertThatThrownBy(() -> adminPermissionService.requireActive(5L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
        }
    }

    @Nested
    @DisplayName("requireSuper 超级管理员限制")
    class RequireSuper {

        @Test
        @DisplayName("super 角色通过")
        void superAdmin_shouldPass() {
            UserContext.set(5L, "admin");
            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "super", 0));

            assertThat(adminPermissionService.requireSuper().getRole()).isEqualTo("super");
        }

        @Test
        @DisplayName("普通管理员（audit）调用 super 接口 → 403")
        void auditAdmin_shouldThrow403() {
            UserContext.set(6L, "admin");
            when(adminMapper.selectById(6L)).thenReturn(admin(6L, "audit", 0));

            assertThatThrownBy(() -> adminPermissionService.requireSuper())
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        }
    }
}
