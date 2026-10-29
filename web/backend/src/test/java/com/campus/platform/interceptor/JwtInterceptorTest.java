package com.campus.platform.interceptor;

import com.campus.platform.module.admin.service.AdminUserService;

import com.campus.platform.common.Constants;
import com.campus.platform.common.UserContext;
import com.campus.platform.utils.JwtUtils;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P1-1 回归：JwtInterceptor 禁用用户黑名单校验。
 *
 * <p>背景：管理员禁用学生后，其存量 JWT 在自然过期前仍可调用受保护接口。
 * 修复方案为「禁用时写 Redis 黑名单 + 拦截器逐请求校验」，本测试锁定该行为，
 * 同时防止黑名单逻辑引入导致原有鉴权路径退化。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P1-1 JwtInterceptor 禁用黑名单与鉴权回归")
class JwtInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private JwtInterceptor interceptor;

    private static final String BEARER = "Bearer TOKEN_X";

    @AfterEach
    void tearDown() {
        // 拦截器只在 afterCompletion 清理，单测里手动兜底，避免 ThreadLocal 串测试
        UserContext.clear();
    }

    private MockHttpServletRequest requestWithBearer() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", BEARER);
        return req;
    }

    // ==================== 黑名单核心行为 ====================

    @Nested
    @DisplayName("禁用黑名单校验")
    class Blacklist {

        @Test
        @DisplayName("学生被禁用后，存量 token 应被拒绝并返回 401")
        void bannedStudent_shouldBeRejectedWith401() throws Exception {
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(42L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            // AdminUserService 禁用时写入的正是该 key
            when(redisUtils.hasKey("auth:blacklist:student:42")).thenReturn(true);

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(requestWithBearer(), resp, new Object());

            assertThat(pass).as("命中黑名单必须中断请求链").isFalse();
            assertThat(resp.getContentAsString())
                    .as("响应体应为统一响应体且业务码为 401，前端 request.js 依赖 body.code===401 清 token 跳登录")
                    .contains("401");
            assertThat(UserContext.get())
                    .as("被拒绝的请求绝不能写入 UserContext，否则后续逻辑会误认为已登录")
                    .isNull();
        }

        @Test
        @DisplayName("未被禁用的学生应正常放行并写入 UserContext")
        void normalStudent_shouldPass() throws Exception {
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(42L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            when(redisUtils.hasKey("auth:blacklist:student:42")).thenReturn(false);

            boolean pass = interceptor.preHandle(
                    requestWithBearer(), new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            assertThat(UserContext.getUid()).isEqualTo(42L);
            assertThat(UserContext.getRole()).isEqualTo(Constants.ROLE_STUDENT);
        }

        @Test
        @DisplayName("解封后（key 已删除）应立即恢复访问")
        void unbannedStudent_shouldRecoverAccess() throws Exception {
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(42L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            // 模拟先禁用后解封：第一次命中，第二次不命中
            when(redisUtils.hasKey("auth:blacklist:student:42")).thenReturn(true, false);

            boolean banned = interceptor.preHandle(
                    requestWithBearer(), new MockHttpServletResponse(), new Object());
            UserContext.clear();
            boolean recovered = interceptor.preHandle(
                    requestWithBearer(), new MockHttpServletResponse(), new Object());

            assertThat(banned).isFalse();
            assertThat(recovered).as("解封后同一 token 应恢复可用，无需重新登录").isTrue();
            assertThat(UserContext.getUid()).isEqualTo(42L);
        }

        @Test
        @DisplayName("学生黑名单查询必须使用学生角色命名空间")
        void blacklistKey_shouldUseTokenRoleNamespace() throws Exception {
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(7L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            when(redisUtils.hasKey("auth:blacklist:student:7")).thenReturn(false);

            boolean pass = interceptor.preHandle(
                    requestWithBearer(), new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            verify(redisUtils).hasKey("auth:blacklist:student:7");
            verify(redisUtils, never()).hasKey("auth:blacklist:admin:7");
        }

        @Test
        @DisplayName("管理员 token 应由学生端拦截器直接拒绝且不查询学生黑名单")
        void adminToken_shouldBeRejectedBeforeBlacklistLookup() throws Exception {
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(7L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_ADMIN);

            boolean pass = interceptor.preHandle(
                    requestWithBearer(), new MockHttpServletResponse(), new Object());

            assertThat(pass).isFalse();
            verify(redisUtils, never()).hasKey(anyString());
        }
    }

    // ==================== 公开读取白名单（P2 第2项修复） ====================

    @Nested
    @DisplayName("公开读取白名单（方法+端点规则）")
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
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("匿名 GET 公开评论列表应放行")
        void anonymousPublicComments_shouldPass() throws Exception {
            boolean pass = interceptor.preHandle(getRequest("/api/post/123/comments"),
                    new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
        }

        @Test
        @DisplayName("匿名 POST 写操作必须拒绝（不能因公开路径误放开写接口）")
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
        @DisplayName("带有效学生令牌访问公开列表仍解析用户上下文（个性化状态）")
        void publicListWithValidToken_shouldSetContext() throws Exception {
            MockHttpServletRequest req = getRequest("/api/post/list");
            req.addHeader("Authorization", BEARER);
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(42L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            when(redisUtils.hasKey("auth:blacklist:student:42")).thenReturn(false);

            boolean pass = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            assertThat(pass).isTrue();
            assertThat(UserContext.getUid()).isEqualTo(42L);
        }

        @Test
        @DisplayName("过期/非法令牌访问公开读取端点按匿名继续，不误清登录态")
        void invalidTokenOnPublicRead_shouldPassAsAnonymous() throws Exception {
            MockHttpServletRequest req = getRequest("/api/idle/list");
            req.addHeader("Authorization", BEARER);
            when(jwtUtils.getUid("TOKEN_X")).thenThrow(new RuntimeException("expired"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());

            assertThat(pass).as("公开页不能被过期 token 弹登录").isTrue();
            assertThat(UserContext.get()).isNull();
            assertThat(resp.getContentAsString()).doesNotContain("401");
        }

        @Test
        @DisplayName("过期令牌访问受保护端点仍应 401 引导登录")
        void invalidTokenOnProtected_shouldReturn401() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/chat/1/messages");
            req.addHeader("Authorization", BEARER);
            when(jwtUtils.getUid("TOKEN_X")).thenThrow(new RuntimeException("expired"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
        }
    }

    // ==================== 原有鉴权路径防退化 ====================

    @Nested
    @DisplayName("原有鉴权行为不退化")
    class NoRegression {
        @Test
        @DisplayName("无 Authorization 头应 401，且不查 Redis")
        void noToken_shouldReturn401WithoutRedisLookup() throws Exception {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(new MockHttpServletRequest(), resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
            verify(redisUtils, never()).hasKey(anyString());
        }

        @Test
        @DisplayName("token 非法/过期（解析抛异常）应 401 而非 500")
        void invalidToken_shouldReturn401NotCrash() throws Exception {
            when(jwtUtils.getUid("TOKEN_X")).thenThrow(new RuntimeException("JWT expired"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(requestWithBearer(), resp, new Object());

            assertThat(pass).isFalse();
            assertThat(resp.getContentAsString()).contains("401");
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("SSE 场景：token 从 query 参数取值仍可用（EventSource 无法自定义 Header）")
        void sseQueryParamToken_shouldStillWork() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setParameter("token", "TOKEN_X");
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(9L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            when(redisUtils.hasKey("auth:blacklist:student:9")).thenReturn(false);

            boolean pass = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            assertThat(pass).as("B1 流式问答依赖 query token，不能被黑名单改动破坏").isTrue();
            assertThat(UserContext.getUid()).isEqualTo(9L);
        }

        @Test
        @DisplayName("被禁用户走 SSE query token 同样应被拦截（不能绕过黑名单）")
        void bannedStudentViaQueryToken_shouldAlsoBeBlocked() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setParameter("token", "TOKEN_X");
            when(jwtUtils.getUid("TOKEN_X")).thenReturn(9L);
            when(jwtUtils.getRole("TOKEN_X")).thenReturn(Constants.ROLE_STUDENT);
            when(redisUtils.hasKey("auth:blacklist:student:9")).thenReturn(true);

            boolean pass = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            assertThat(pass).as("query token 是常见绕过点，必须与 Header 路径一致校验").isFalse();
        }

        @Test
        @DisplayName("afterCompletion 应清理 ThreadLocal，防止线程复用串号")
        void afterCompletion_shouldClearUserContext() {
            UserContext.set(42L, Constants.ROLE_STUDENT);

            interceptor.afterCompletion(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), new Object(), null);

            assertThat(UserContext.get())
                    .as("Tomcat 线程池复用，不清理会导致下一个请求顶替他人身份")
                    .isNull();
        }
    }
}
