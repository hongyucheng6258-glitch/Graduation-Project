package com.campus.platform.interceptor;

import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.utils.JwtUtils;
import com.campus.platform.utils.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import com.campus.platform.common.R;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 学生端 JWT 拦截器（共享约定 #3）。
 * 从 Header Authorization: Bearer <token> 解析 {uid, role} 写入 UserContext。
 *
 * <p>P1/P2 鉴权统一修复（第2项）：
 * 公开读取采用「HTTP 方法 + 明确端点」规则——匿名 GET 列表/详情允许进入且不设用户上下文；
 * 携带有效学生令牌时仍解析上下文，使点赞/收藏等个性化状态正常返回；
 * 无效/过期令牌在公开读取端点上按匿名继续（保证公开页可访问），在受保护端点返回 401 引导登录。
 * 写操作（POST/PUT/DELETE）一律不在公开白名单内，匿名提交被拒绝。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    /** 公开读取白名单（仅 GET；含 {id} 的详情路径用数字约束，避免误放开写接口或“my”等个人端点） */
    private static final List<String> PUBLIC_READ_PATTERNS = List.of(
            // 公开列表
            "/api/idle/list",
            "/api/activity/list",
            "/api/lostfound/list",
            "/api/notice/list",
            "/api/post/list",
            "/api/qa/list",
            "/api/partner/list",
            "/api/home/aggregate",
            "/api/site/config",
            // 公开详情（数字 id；带路径参数的写操作不在白名单内）
            "/api/idle/{id:[0-9]+}",
            "/api/activity/{id:[0-9]+}",
            "/api/lostfound/{id:[0-9]+}",
            "/api/notice/{id:[0-9]+}",
            "/api/qa/{id:[0-9]+}",
            "/api/partner/{id:[0-9]+}",
            "/api/post/{id:[0-9]+}",
            "/api/post/{id:[0-9]+}/comments"
    );

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        if (token == null) {
            if (isPublicRead(request)) {
                // 匿名公开读取：放行，不设用户上下文（个性化状态由业务层按空上下文处理）
                return true;
            }
            writeUnauthorized(response, ResultCode.UNAUTHORIZED);
            return false;
        }
        try {
            Long uid = jwtUtils.getUid(token);
            String role = jwtUtils.getRole(token);
            // BUG-06 修复：管理员 Token 不能当学生 Token 用
            if (Constants.ROLE_ADMIN.equals(role)) {
                writeUnauthorized(response, ResultCode.FORBIDDEN);
                return false;
            }
            // 禁用用户黑名单校验：管理员禁用学生后，其存量 token 在下一次请求即失效
            if (redisUtils.hasKey("auth:blacklist:" + role + ":" + uid)) {
                writeUnauthorized(response, ResultCode.UNAUTHORIZED);
                return false;
            }
            UserContext.set(uid, role);
            return true;
        } catch (Exception e) {
            // 无效/过期令牌：公开读取端点按匿名继续；受保护端点返回 401 引导登录
            if (isPublicRead(request)) {
                return true;
            }
            writeUnauthorized(response, ResultCode.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 是否属于公开读取端点（GET + 白名单路径） */
    private boolean isPublicRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        for (String pattern : PUBLIC_READ_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    protected String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 兼容 query 参数（SSE EventSource 无法自定义 Header 的场景）
        return request.getParameter("token");
    }

    protected void writeUnauthorized(HttpServletResponse response, ResultCode rc) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(rc)));
    }
}
