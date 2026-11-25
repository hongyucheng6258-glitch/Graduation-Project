package com.campus.platform.module.admin;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.admin.service.AdminUserService;
import com.campus.platform.module.auth.service.IAuthService;
import com.campus.platform.module.auth.service.TokenService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 管理端用户管理：禁用/解封与登录态联动。
 *
 * <p>禁用学生后必须清掉其全部登录态（Sa-Token kickout），否则存量 token 仍可调用接口；
 * 解封不重新签发，学生重新登录即可。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminUserService 禁用/解封与登录态联动")
class AdminUserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AdminMapper adminMapper;
    @Mock
    private IAuthService authService;
    @Mock
    private TokenService tokenService;

    @Mock private com.campus.platform.module.chat.websocket.ChatSessionRegistry chatSessions;
    @Mock private com.campus.platform.module.drawgame.websocket.DrawGuessSessionRegistry drawSessions;

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
        @DisplayName("禁用应落库 status=1 并清掉登录态")
        void ban_shouldPersistStatusAndKickout() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(saved.capture());
            assertThat(saved.getValue().getStatus())
                    .as("禁用语义由 Constants.USER_STATUS_BANNED 定义，须与 IAuthService.login 的判断一致")
                    .isEqualTo(Constants.USER_STATUS_BANNED);

            verify(tokenService).kickoutStudent(42L);
            verify(chatSessions).closeUserSessions(42L);
            verify(drawSessions).closeUserSessions(42L);
        }
    }

    @Nested
    @DisplayName("解封用户")
    class Unban {

        @Test
        @DisplayName("解封应落库 status=0，不触发登录态清理")
        void unban_shouldPersistStatusWithoutKickout() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(Constants.USER_STATUS_NORMAL);

            verify(tokenService, never()).kickoutStudent(anyLong());
            verify(chatSessions, never()).closeUserSessions(anyLong());
        }

        @Test
        @DisplayName("重复解封应幂等，不应报错")
        void unban_shouldBeIdempotent() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);
            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);

            verify(userMapper, times(2)).updateById(any(User.class));
            verify(tokenService, never()).kickoutStudent(anyLong());
        }
    }

    @Nested
    @DisplayName("异常与边界")
    class EdgeCases {

        @Test
        @DisplayName("用户不存在应抛 404，且不得清理任何登录态")
        void userNotFound_shouldThrow404AndNotKickout() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.updateStatus(999L, Constants.USER_STATUS_BANNED))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());

            verify(userMapper, never()).updateById(any(User.class));
            verify(tokenService, never()).kickoutStudent(anyLong());
        }

        @Test
        @DisplayName("status 传 null 应走解封分支，不得 NPE")
        void nullStatus_shouldNotThrowNpe() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, null);

            verify(tokenService, never()).kickoutStudent(anyLong());
        }

        @Test
        @DisplayName("禁用→解封→再禁用，登录态应只在禁用时清理")
        void banUnbanBan_shouldKickoutOnEachBan() {
            when(userMapper.selectById(42L)).thenReturn(existingUser(42L));

            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);
            adminUserService.updateStatus(42L, Constants.USER_STATUS_NORMAL);
            adminUserService.updateStatus(42L, Constants.USER_STATUS_BANNED);

            verify(tokenService, times(2)).kickoutStudent(42L);
        }
    }

    @Nested
    @DisplayName("子管理员删除/降权后登录态清理")
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
        @DisplayName("删除管理员后必须清掉其全部登录态")
        void deleteAdmin_shouldKickout() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "audit"));
            when(adminMapper.selectCount(any())).thenReturn(5L);

            adminUserService.deleteAdmin(7L, 1L);

            verify(adminMapper).deleteById(7L);
            verify(tokenService).kickoutAdmin(7L);
        }

        @Test
        @DisplayName("修改角色（如 super 降为 audit）后必须清掉其全部登录态")
        void updateAdmin_roleChanged_shouldKickout() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "super"));

            com.campus.platform.module.admin.dto.AdminSaveDTO dto = new com.campus.platform.module.admin.dto.AdminSaveDTO();
            dto.setUsername("admin7");
            dto.setPassword("newpass");
            dto.setRole("audit");
            adminUserService.updateAdmin(7L, dto);

            verify(tokenService).kickoutAdmin(7L);
        }

        @Test
        @DisplayName("角色未变化时不触发登录态清理")
        void updateAdmin_roleUnchanged_shouldNotKickout() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "audit"));

            com.campus.platform.module.admin.dto.AdminSaveDTO dto = new com.campus.platform.module.admin.dto.AdminSaveDTO();
            dto.setUsername("admin7");
            dto.setPassword("newpass");
            dto.setRole("audit");
            adminUserService.updateAdmin(7L, dto);

            verify(tokenService, never()).kickoutAdmin(anyLong());
        }

        @Test
        @DisplayName("不能删除最后一个超级管理员，且不得清理登录态")
        void deleteLastSuper_shouldBeRejected() {
            when(adminMapper.selectById(7L)).thenReturn(existingAdmin(7L, "super"));
            when(adminMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminUserService.deleteAdmin(7L, 1L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());

            verify(adminMapper, never()).deleteById(anyLong());
            verify(tokenService, never()).kickoutAdmin(anyLong());
        }
    }
}
