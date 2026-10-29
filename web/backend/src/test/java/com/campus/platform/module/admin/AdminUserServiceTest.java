package com.campus.platform.module.admin;

import com.campus.platform.module.admin.service.AdminUserService;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.admin.mapper.AdminMapper;

import com.campus.platform.module.auth.service.AuthService;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.RedisUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P1-1 回归：D1 用户禁用/解封与鉴权黑名单联动。
 *
 * <p>关键约束：本类写入的 Redis key 必须与 {@code JwtInterceptor} 查询的 key 逐字符一致，
 * 否则黑名单会「静默失效」——没有异常、没有编译错误，只是封禁不生效。
 * 因此这里用 ArgumentCaptor 锁定 key 的字面量格式。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P1-1 AdminUserService 禁用/解封与黑名单联动")
class AdminUserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AdminMapper adminMapper;
    @Mock
    private AuthService authService;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private AdminPermissionService adminPermissionService;

    @InjectMocks
    private AdminUserService adminUserService;

    private User existingUser(long id) {
        User u = new User();
        u.setId(id);
        u.setStudentNo("2021001");
        u.setStatus(Constants.USER_STATUS_NORMAL);
        return u;
    }

    @Nested
    @DisplayName("禁用用户")
    class Ban {

        @Test
        @DisplayName("禁用应落库 status=1 并写入黑名单")
        void ban_shouldPersistStatusAndWriteBlacklist() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(saved.capture());
            assertThat(saved.getValue().getStatus())
                    .as("禁用语义由 Constants.USER_STATUS_BANNED 定义，须与 AuthService.login 的判断一致")
                    .isEqualTo(Constants.USER_STATUS_BANNED);

            verify(redisUtils).set("auth:blacklist:" + Constants.ROLE_STUDENT + ":42", "1");
            verify(redisUtils, never()).delete(anyString());
        }

        @Test
        @DisplayName("黑名单 key 格式必须与 JwtInterceptor 查询格式逐字符对齐")
        void blacklistKey_mustMatchInterceptorLookupFormat() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(redisUtils).set(key.capture(), any());

            // JwtInterceptor 拼接方式：("auth:blacklist:" + role + ":" + uid)，role 取自 JWT
            String interceptorLookupKey = "auth:blacklist:" + Constants.ROLE_STUDENT + ":" + 42L;
            assertThat(key.getValue())
                    .as("写入 key 与拦截器查询 key 一旦不一致，封禁将静默失效（无异常、无日志）")
                    .isEqualTo(interceptorLookupKey);
        }
    }

    @Nested
    @DisplayName("解封用户")
    class Unban {

        @Test
        @DisplayName("解封应落库 status=0 并移除黑名单")
        void unban_shouldPersistStatusAndRemoveBlacklist() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(Constants.USER_STATUS_NORMAL);

            verify(redisUtils).delete("auth:blacklist:" + Constants.ROLE_STUDENT + ":42");
            verify(redisUtils, never()).set(anyString(), any());
        }

        @Test
        @DisplayName("重复解封应幂等，不应报错")
        void unban_shouldBeIdempotent() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);
            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);

            verify(redisUtils, times(2)).delete("auth:blacklist:student:42");
        }
    }

    @Nested
    @DisplayName("异常与边界")
    class EdgeCases {

        @Test
        @DisplayName("用户不存在应抛 404，且不得写任何黑名单")
        void userNotFound_shouldThrow404AndNotTouchRedis() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.updateStatus(999L, Constants.USER_STATUS_BANNED))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());

            verify(userMapper, never()).updateById(any(User.class));
            verify(redisUtils, never()).set(anyString(), any());
            verify(redisUtils, never()).delete(anyString());
        }

        @Test
        @DisplayName("status 传 null 应走解封分支，不得 NPE")
        void nullStatus_shouldNotThrowNpe() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, null);

            // 源码用 (status != null && status == 1) 判断，null 落到 else 分支
            verify(redisUtils).delete("auth:blacklist:student:42");
        }

        @Test
        @DisplayName("禁用→解封→再禁用，黑名单应随状态正确翻转")
        void banUnbanBan_shouldFlipBlacklistConsistently() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);
            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);
            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);

            verify(redisUtils, times(2)).set("auth:blacklist:student:42", "1");
            verify(redisUtils, times(1)).delete("auth:blacklist:student:42");
        }
    }

    @Nested
    @DisplayName("子管理员删除/降权后令牌撤销（P1）")
    class AdminTokenRevocation {

        private com.campus.platform.module.admin.entity.Admin existingAdmin(long id, String role) {
            com.campus.platform.module.admin.entity.Admin a = new com.campus.platform.module.admin.entity.Admin();
            a.setId(id);
            a.setUsername("admin" + id);
            a.setRole(role);
            a.setStatus(0);
            return a;
        }

        @Test
        @DisplayName("删除管理员后必须撤销其存量令牌")
        void deleteAdmin_shouldRevokeToken() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "audit"));
            when(adminMapper.selectCount(any())).thenReturn(5L);

            adminUserService.deleteAdmin(7L, 1L);

            verify(adminMapper).deleteById(7L);
            verify(adminPermissionService).revokeToken(7L);
        }

        @Test
        @DisplayName("修改角色（如 super 降为 audit）后必须撤销其存量令牌")
        void updateAdmin_roleChanged_shouldRevokeToken() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "super"));

            com.campus.platform.module.admin.dto.AdminSaveDTO dto = new com.campus.platform.module.admin.dto.AdminSaveDTO();
            dto.setUsername("admin7");
            dto.setPassword("newpass");
            dto.setRole("audit");
            adminUserService.updateAdmin(7L, dto);

            verify(adminPermissionService).revokeToken(7L);
        }

        @Test
        @DisplayName("角色未变化时不触发令牌撤销")
        void updateAdmin_roleUnchanged_shouldNotRevoke() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "audit"));

            com.campus.platform.module.admin.dto.AdminSaveDTO dto = new com.campus.platform.module.admin.dto.AdminSaveDTO();
            dto.setUsername("admin7");
            dto.setPassword("newpass");
            dto.setRole("audit");
            adminUserService.updateAdmin(7L, dto);

            verify(adminPermissionService, never()).revokeToken(anyLong());
        }

        @Test
        @DisplayName("不能删除最后一个超级管理员，且不得撤销令牌")
        void deleteLastSuper_shouldBeRejected() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "super"));
            when(adminMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminUserService.deleteAdmin(7L, 1L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());

            verify(adminMapper, never()).deleteById(anyLong());
            verify(adminPermissionService, never()).revokeToken(anyLong());
        }
    }
}
