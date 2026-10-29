package com.campus.platform.module.auth;

import com.campus.platform.module.auth.dto.AdminLoginDTO;
import com.campus.platform.module.auth.vo.AdminLoginVO;
import com.campus.platform.module.auth.service.AuthService;
import com.campus.platform.module.auth.dto.RegisterDTO;
import com.campus.platform.module.auth.dto.LoginDTO;
import com.campus.platform.module.auth.vo.LoginVO;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.JwtUtils;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;

/**
 * 认证服务测试（对应 PRD A1/A5）。
 *
 * 核心验证点：Web 密码登录签发 student JWT；管理员独立签发 admin JWT。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("认证服务")
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private AdminMapper adminMapper;
    @Mock private JwtUtils jwtUtils;
    @Mock private SystemConfigHolder systemConfigHolder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUpSystemConfigDefaults() {
        when(systemConfigHolder.isRegisterEnabled()).thenReturn(true);
        when(systemConfigHolder.getInt("user_default_status", Constants.USER_STATUS_NORMAL))
                .thenReturn(Constants.USER_STATUS_NORMAL);
    }

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ==================== A1 Web 注册/登录 ====================

    @Nested
    @DisplayName("A1 Web 账号密码注册与登录")
    class WebAuth {

        @Test
        @DisplayName("注册成功应 BCrypt 加密密码、状态正常，并签发 student JWT")
        void register_shouldEncryptPasswordAndIssueStudentToken() {
            when(userMapper.selectCount(any())).thenReturn(0L);
            when(jwtUtils.generate(any(), eq(Constants.ROLE_STUDENT))).thenReturn("TOKEN_STUDENT");

            RegisterDTO dto = new RegisterDTO();
            dto.setStudentNo("2021001");
            dto.setNickname("小明");
            dto.setPassword("123456");

            LoginVO vo = authService.register(dto);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getStudentNo()).isEqualTo("2021001");
            assertThat(saved.getStatus()).isEqualTo(Constants.USER_STATUS_NORMAL);
            // 密码必须加密存储，不得明文
            assertThat(saved.getPassword()).isNotEqualTo("123456");
            assertThat(saved.getPassword()).startsWith("$2a$");
            assertThat(encoder.matches("123456", saved.getPassword())).isTrue();
            assertThat(saved.getLastLoginTime()).isNotNull();
            assertThat(vo.getToken()).isEqualTo("TOKEN_STUDENT");
        }

        @Test
        @DisplayName("学号重复应拒绝注册")
        void register_shouldRejectDuplicateStudentNo() {
            when(userMapper.selectCount(any())).thenReturn(1L);

            RegisterDTO dto = new RegisterDTO();
            dto.setStudentNo("2021001");
            dto.setPassword("123456");

            assertThatThrownBy(() -> authService.register(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("该学号已注册");

            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("登录成功应更新最后登录时间并签发 student JWT")
        void login_shouldSucceedAndUpdateLastLoginTime() {
            User user = buildUser(1L, "2021001", encoder.encode("123456"), Constants.USER_STATUS_NORMAL);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(jwtUtils.generate(eq(1L), eq(Constants.ROLE_STUDENT))).thenReturn("TOKEN_STUDENT");

            LoginDTO dto = new LoginDTO();
            dto.setStudentNo("2021001");
            dto.setPassword("123456");

            LoginVO vo = authService.login(dto);

            assertThat(vo.getToken()).isEqualTo("TOKEN_STUDENT");
            assertThat(user.getLastLoginTime()).isNotNull();
            verify(userMapper).updateById(user);
            // 口径校验：JWT 角色必须是 student，不能是 admin
            verify(jwtUtils, never()).generate(anyLong(), eq(Constants.ROLE_ADMIN));
        }

        @Test
        @DisplayName("密码错误应拒绝登录，且错误信息不泄漏账号是否存在")
        void login_shouldRejectWrongPassword() {
            User user = buildUser(1L, "2021001", encoder.encode("correct"), Constants.USER_STATUS_NORMAL);
            when(userMapper.selectOne(any())).thenReturn(user);

            LoginDTO dto = new LoginDTO();
            dto.setStudentNo("2021001");
            dto.setPassword("wrong");

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessage("学号或密码错误");
        }

        @Test
        @DisplayName("账号不存在应返回与密码错误相同的提示（防账号枚举）")
        void login_shouldRejectUnknownAccountWithSameMessage() {
            when(userMapper.selectOne(any())).thenReturn(null);

            LoginDTO dto = new LoginDTO();
            dto.setStudentNo("nobody");
            dto.setPassword("123456");

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessage("学号或密码错误");
        }

        @Test
        @DisplayName("D1：被禁用账号应 403 无法登录")
        void login_shouldRejectBannedUser() {
            User user = buildUser(1L, "2021001", encoder.encode("123456"), Constants.USER_STATUS_BANNED);
            when(userMapper.selectOne(any())).thenReturn(user);

            LoginDTO dto = new LoginDTO();
            dto.setStudentNo("2021001");
            dto.setPassword("123456");

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode())
                    .hasMessageContaining("账号已被禁用");
        }

        @Test
        @DisplayName("无密码账号（password=null）不得被空密码登录绕过")
        void login_shouldRejectNullPasswordAccount() {
            User user = buildUser(1L, "2021001", null, Constants.USER_STATUS_NORMAL);
            when(userMapper.selectOne(any())).thenReturn(user);

            LoginDTO dto = new LoginDTO();
            dto.setStudentNo("2021001");
            dto.setPassword("anything");

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessage("学号或密码错误");
        }
    }

    // ==================== A5 管理员登录 ====================

    @Nested
    @DisplayName("A5 管理员独立登录")
    class AdminAuth {

        @Test
        @DisplayName("管理员登录应签发 role=admin 的 JWT（与学生端隔离）")
        void adminLogin_shouldIssueAdminToken() {
            Admin admin = new Admin();
            admin.setId(1L);
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("admin123"));
            admin.setStatus(0);
            when(adminMapper.selectOne(any())).thenReturn(admin);
            when(jwtUtils.generate(1L, Constants.ROLE_ADMIN)).thenReturn("TOKEN_ADMIN");

            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("admin123");

            AdminLoginVO vo = authService.adminLogin(dto);

            assertThat(vo.getToken()).isEqualTo("TOKEN_ADMIN");
            verify(jwtUtils).generate(1L, Constants.ROLE_ADMIN);
            // 绝不能给管理员发 student 角色
            verify(jwtUtils, never()).generate(anyLong(), eq(Constants.ROLE_STUDENT));
        }

        /**
         * 从 schema.sql 实时解析内置管理员的 BCrypt 哈希。
         * 刻意不硬编码哈希字符串——否则 schema.sql 一改，测试里的副本就失效，
         * 这条约定实际上就失去了回归保护能力（本用例曾因此误报过一次）。
         */
        private String readBuiltinAdminHashFromSchema() {
            java.nio.file.Path schema = java.nio.file.Paths.get("src/main/resources/db/schema.sql");
            assertThat(java.nio.file.Files.exists(schema))
                    .as("schema.sql 必须存在于 %s，否则数据库初始化脚本缺失", schema.toAbsolutePath())
                    .isTrue();
            String sql;
            try {
                sql = new String(java.nio.file.Files.readAllBytes(schema),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("读取 schema.sql 失败", e);
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("'admin'\\s*,\\s*'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'")
                    .matcher(sql);
            assertThat(m.find())
                    .as("schema.sql 中应存在内置管理员 admin 的 BCrypt 密码种子记录")
                    .isTrue();
            return m.group(1);
        }

        @Test
        @DisplayName("schema.sql 内置的 admin/admin123 BCrypt 应可校验通过")
        void adminLogin_builtinCredentialShouldMatch() {
            String hashInSchema = readBuiltinAdminHashFromSchema();

            // 架构设计 10.4 约定：schema.sql 内置 admin/admin123
            assertThat(encoder.matches("admin123", hashInSchema))
                    .as("架构设计约定内置账号为 admin/admin123，该哈希必须能被 admin123 校验通过，"
                            + "否则答辩现场无法登录管理后台（D1/D2/D4/D7 全部演示不了）。实际哈希=" + hashInSchema)
                    .isTrue();
        }

        @Test
        @DisplayName("诊断：探明 schema.sql 内置哈希对应的真实明文")
        void diagnose_builtinAdminHashPlaintext() {
            String hashInSchema = readBuiltinAdminHashFromSchema();
            String[] candidates = {"admin123", "123456", "admin", "password", "admin@123", "12345678"};

            StringBuilder report = new StringBuilder("内置哈希明文探测：");
            String matched = null;
            for (String c : candidates) {
                boolean ok = encoder.matches(c, hashInSchema);
                report.append("\n  ").append(c).append(" -> ").append(ok);
                if (ok) {
                    matched = c;
                }
            }
            System.out.println(report);

            assertThat(matched)
                    .as("内置管理员哈希应对应文档约定的 admin123；实际探测结果见控制台输出")
                    .isEqualTo("admin123");
        }

        @Test
        @DisplayName("管理员密码错误应拒绝")
        void adminLogin_shouldRejectWrongPassword() {
            Admin admin = new Admin();
            admin.setId(1L);
            admin.setPassword(encoder.encode("admin123"));
            admin.setStatus(0);
            when(adminMapper.selectOne(any())).thenReturn(admin);

            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("wrong");

            assertThatThrownBy(() -> authService.adminLogin(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessage("用户名或密码错误");
        }

        @Test
        @DisplayName("被禁用管理员应 403")
        void adminLogin_shouldRejectDisabledAdmin() {
            Admin admin = new Admin();
            admin.setId(1L);
            admin.setPassword(encoder.encode("admin123"));
            admin.setStatus(1);
            when(adminMapper.selectOne(any())).thenReturn(admin);

            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("admin123");

            assertThatThrownBy(() -> authService.adminLogin(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        }
    }

    // ==================== 工具方法 ====================

    @Test
    @DisplayName("encode/matches 应可互相校验（供改密复用）")
    void encodeAndMatches_shouldRoundTrip() {
        String encoded = authService.encode("myPassword");

        assertThat(encoded).isNotEqualTo("myPassword");
        assertThat(authService.matches("myPassword", encoded)).isTrue();
        assertThat(authService.matches("otherPassword", encoded)).isFalse();
    }

    private static User buildUser(Long id, String studentNo, String password, int status) {
        User user = new User();
        user.setId(id);
        user.setStudentNo(studentNo);
        user.setNickname("测试用户");
        user.setPassword(password);
        user.setStatus(status);
        return user;
    }
}
