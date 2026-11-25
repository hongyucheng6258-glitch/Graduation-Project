package com.campus.platform.interceptor;

import com.campus.platform.common.Constants;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.auth.service.TokenService;
import com.campus.platform.module.user.mapper.UserMapper;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 学生端登录拦截器：Sa-Token 会话解析、公开读取白名单、临时密码限制。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoginInterceptor 登录态与权限回归")
class LoginInterceptorTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private LoginInterceptor interceptor;

    private static final String BEARER = "Bearer TOKEN_X";

    @org.junit.jupiter.api.BeforeEach
    void stubAnonymousToken() {
        // 没有 token 的请求：Sa-Token 解析结果为空
        when(tokenService.studentId(null)).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private MockHttpServletRequest requestWithBearer() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", BEARER);
        return req;
    }

    @Test
    @DisplayName("临时密码会话只能改密，改密后旧会话由 kickout 清理")
    void temporarySessionCanOnlyChangePassword() throws Exception {
        when(tokenService.studentId("TOKEN_X")).thenReturn(42L);
        when(userMapper.requiresPasswordChange(42L)).thenReturn(true);

        var request = requestWithBearer();
        request.setMethod("POST");
        request.setRequestURI("/api/chat/ws-ticket");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isFalse();

        request.setMethod("PUT");
        request.setRequestURI("/api/user/password");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    // ==================== 登录态解析 ====================

    @Nested
    @DisplayName("登录态解析")
    class Session {

        @Test
        @DisplayName("有效学生 token 应放行并写入 UserContext")
        void validStudentToken_shouldPass() throws Exception {
            when(tokenService.studentId("TOKEN_X")).thenReturn(42L);
            when(userMapper.requiresPasswordChange(42L)).thenReturn(false);

            boolean pass = interceptor.preHandle(requestWithBearer(), new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            assertThat(UserContext.getUid()).isEqualTo(42L);
            assertThat(UserContext.getRole()).isEqualTo(Constants.ROLE_STUDENT);
        }

        @Test
        @DisplayName("无 token 访问受保护接口应 401")
        void noToken_shouldReturn401() throws Exception {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(new MockHttpServletRequest(), resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("token 无效/过期应 401 而非 500")
        void invalidToken_shouldReturn401() throws Exception {
            when(tokenService.studentId("TOKEN_X")).thenThrow(new RuntimeException("invalid token"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(requestWithBearer(), resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("管理员 token 在学生端解析不到会话，应 401（双账号体系隔离）")
        void adminToken_shouldNotPassStudentInterceptor() throws Exception {
            when(tokenService.studentId("TOKEN_X")).thenReturn(null);

            boolean pass = interceptor.preHandle(requestWithBearer(), new MockHttpServletResponse(), new Object());

            assertThat(pass).isFalse();
        }

        @Test
        @DisplayName("SSE 场景：token 从 query 参数取值仍可用")
        void sseQueryParamToken_shouldWork() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setParameter("token", "TOKEN_X");
            when(tokenService.studentId("TOKEN_X")).thenReturn(9L);
            when(userMapper.requiresPasswordChange(9L)).thenReturn(false);

            boolean pass = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            assertThat(UserContext.getUid()).isEqualTo(9L);
        }

        @Test
        @DisplayName("afterCompletion 应清理 ThreadLocal")
        void afterCompletion_shouldClearUserContext() {
            UserContext.set(42L, Constants.ROLE_STUDENT);

            interceptor.afterCompletion(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new Object(), null);

            assertThat(UserContext.get()).isNull();
        }
    }

    // ==================== 公开读取白名单 ====================

    @Nested
    @DisplayName("公开读取白名单（方法 + 端点规则）")
    class PublicRead {

        private MockHttpServletRequest getRequest(String uri) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setMethod("GET");
            req.setRequestURI(uri);
            return req;
        }

        @Test
        @DisplayName("匿名 GET 公开列表应放行且不设用户上下文")
        void anonymousPublicList_shouldPassWithoutContext() throws Exception {
            boolean pass = interceptor.preHandle(getRequest("/api/post/list"),
                    new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("匿名 GET 公开详情（数字 id）应放行")
        void anonymousPublicDetail_shouldPass() throws Exception {
            boolean pass = interceptor.preHandle(getRequest("/api/activity/123"),
                    new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
        }

        @Test
        @DisplayName("匿名 GET 公开评论列表应放行")
        void anonymousPublicComments_shouldPass() throws Exception {
            boolean pass = interceptor.preHandle(getRequest("/api/post/123/comments"),
                    new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
        }

        @Test
        @DisplayName("匿名 POST 写操作必须拒绝")
        void anonymousWrite_shouldBeRejected() throws Exception {
            MockHttpServletRequest req = getRequest("/api/post");
            req.setMethod("POST");

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
        }

        @Test
        @DisplayName("个人端点 /api/post/my 不属于公开读取，匿名应 401")
        void anonymousMyEndpoint_shouldBeRejected() throws Exception {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(getRequest("/api/post/my"), resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
        }

        @Test
        @DisplayName("带有效学生令牌访问公开列表仍解析用户上下文")
        void publicListWithValidToken_shouldSetContext() throws Exception {
            MockHttpServletRequest req = getRequest("/api/post/list");
            req.addHeader("Authorization", BEARER);
            when(tokenService.studentId("TOKEN_X")).thenReturn(42L);
            when(userMapper.requiresPasswordChange(42L)).thenReturn(false);

            boolean pass = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            assertThat(UserContext.getUid()).isEqualTo(42L);
        }

        @Test
        @DisplayName("过期/非法令牌访问公开读取端点按匿名继续")
        void invalidTokenOnPublicRead_shouldPassAsAnonymous() throws Exception {
            MockHttpServletRequest req = getRequest("/api/idle/list");
            req.addHeader("Authorization", BEARER);
            when(tokenService.studentId("TOKEN_X")).thenThrow(new RuntimeException("expired"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());

            assertThat(pass).as("公开页不能被过期 token 弹登录").isTrue();
            assertThat(UserContext.get()).isNull();
            assertThat(resp.getContentAsString()).doesNotContain("401");
        }

        @Test
        @DisplayName("过期令牌访问受保护端点仍应 401")
        void invalidTokenOnProtected_shouldReturn401() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/chat/1/messages");
            req.addHeader("Authorization", BEARER);
            when(tokenService.studentId("TOKEN_X")).thenThrow(new RuntimeException("expired"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
        }
    }

    // ==================== 临时密码 ====================

    @Nested
    @DisplayName("临时密码限制")
    class TemporaryPassword {

        @Test
        @DisplayName("临时密码未修改时，受保护接口一律 403，只放行改密接口")
        void pendingChange_shouldOnlyAllowPasswordUpdate() throws Exception {
            when(tokenService.studentId("TOKEN_X")).thenReturn(42L);
            when(userMapper.requiresPasswordChange(42L)).thenReturn(true);

            MockHttpServletRequest protectedReq = requestWithBearer();
            protectedReq.setMethod("POST");
            protectedReq.setRequestURI("/api/idle");

            MockHttpServletResponse resp = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(protectedReq, resp, new Object())).isFalse();
            assertThat(resp.getContentAsString()).contains("403");

            MockHttpServletRequest passwordReq = requestWithBearer();
            passwordReq.setMethod("PUT");
            passwordReq.setRequestURI("/api/user/password");
            assertThat(interceptor.preHandle(passwordReq, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        @DisplayName("改密完成后不再限制其他接口")
        void afterChange_shouldAllowOtherEndpoints() throws Exception {
            when(tokenService.studentId("TOKEN_X")).thenReturn(42L);
            when(userMapper.requiresPasswordChange(42L)).thenReturn(false);

            MockHttpServletRequest req = requestWithBearer();
            req.setMethod("POST");
            req.setRequestURI("/api/idle");

            assertThat(interceptor.preHandle(req, new MockHttpServletResponse(), new Object())).isTrue();
        }
    }
}
