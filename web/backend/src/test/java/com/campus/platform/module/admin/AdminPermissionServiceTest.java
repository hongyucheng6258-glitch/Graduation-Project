package com.campus.platform.module.admin;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.utils.RedisUtils;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 复查修复回归：按「撤销时间 vs 令牌签发时间」失效旧令牌。
 * 完整回归：改角色→revokeToken 写撤销时间→旧令牌(iat 更早)拒绝→
 * 重登新令牌(iat 更晚)成功→旧令牌仍拒绝（不清 key）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("R2 AdminPermissionService 撤销时间 vs 签发时间")
class AdminPermissionServiceTest {

    @Mock
    private AdminMapper adminMapper;
    @Mock
    private RedisUtils redisUtils;

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
    @DisplayName("requireActive(uid, iat) 撤销时间比较")
    class RevocationComparisons {

        @Test
        @DisplayName("撤销时间不存在 → 通过")
        void noRevoke_shouldPass() {
            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "audit", 0));
            when(redisUtils.get("auth:revoked-at:admin:5")).thenReturn(null);

            assertThat(adminPermissionService.requireActive(5L, 1_000L).getRole()).isEqualTo("audit");
        }

        @Test
        @DisplayName("撤销时间早于令牌签发时间（重登新令牌）→ 通过")
        void newerToken_shouldPass() {
            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "audit", 0));
            when(redisUtils.get("auth:revoked-at:admin:5")).thenReturn("10000");

            assertThat(adminPermissionService.requireActive(5L, 20000L).getRole()).isEqualTo("audit");
        }

        @Test
        @DisplayName("撤销时间晚于令牌签发时间（改角色前的旧令牌）→ 401")
        void olderToken_shouldReject() {
            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "audit", 0));
            when(redisUtils.get("auth:revoked-at:admin:5")).thenReturn("10000");

            assertThatThrownBy(() -> adminPermissionService.requireActive(5L, 1000L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
        }

        @Test
        @DisplayName("管理员不存在 → 401；被禁用 → 401")
        void inactiveAdmin_shouldReject() {
            when(adminMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> adminPermissionService.requireActive(999L, 1L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());

            when(adminMapper.selectById(5L)).thenReturn(admin(5L, "audit", 1));
            assertThatThrownBy(() -> adminPermissionService.requireActive(5L, 1L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
        }
    }

    @Nested
    @DisplayName("完整回归：改角色 → 旧令牌拒绝 → 重登 → 新令牌成功 → 旧令牌仍拒绝")
    class FullReloginRegression {

        @Test
        @DisplayName("revokeToken 记录当前时间；不清除该 key 即可同时拒绝旧、放行新")
        void revokeThenReloginFlow() {
            // 模拟：管理员 7 改角色，系统记录撤销时间
            long revokeTime = System.currentTimeMillis();
            adminPermissionService.revokeToken(7L);
            // revokeToken 写入的是「当前时间」而非固定 "1"
            verify(redisUtils).set(org.mockito.ArgumentMatchers.eq("auth:revoked-at:admin:7"), anyString());

            // 旧令牌 iat 在撤销之前 → 拒绝
            when(adminMapper.selectById(7L)).thenReturn(admin(7L, "audit", 0));
            when(redisUtils.get("auth:revoked-at:admin:7")).thenReturn(String.valueOf(revokeTime));
            assertThatThrownBy(() -> adminPermissionService.requireActive(7L, revokeTime - 60_000L))
                    .isInstanceOf(BizException.class);

            // 重登后新令牌 iat 在撤销之后 → 成功（不清除 revoked-at key）
            assertThat(adminPermissionService.requireActive(7L, revokeTime + 60_000L).getRole()).isEqualTo("audit");

            // 旧令牌（iat 不变）在重登后仍被拒绝
            assertThatThrownBy(() -> adminPermissionService.requireActive(7L, revokeTime - 60_000L))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("restoreToken 删除撤销时间，旧记录不再拦截")
        void restore_shouldDeleteKey() {
            adminPermissionService.restoreToken(7L);
            verify(redisUtils).delete("auth:revoked-at:admin:7");
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
            when(redisUtils.get(anyString())).thenReturn(null);

            assertThat(adminPermissionService.requireSuper().getRole()).isEqualTo("super");
        }

        @Test
        @DisplayName("普通管理员（audit）调用 super 接口 → 403")
        void auditAdmin_shouldThrow403() {
            UserContext.set(6L, "admin");
            when(adminMapper.selectById(6L)).thenReturn(admin(6L, "audit", 0));
            when(redisUtils.get(anyString())).thenReturn(null);

            assertThatThrownBy(() -> adminPermissionService.requireSuper())
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        }
    }
}
