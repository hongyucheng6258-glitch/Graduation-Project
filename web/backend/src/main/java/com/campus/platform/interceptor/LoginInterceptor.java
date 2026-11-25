package com.campus.platform.interceptor;

import com.campus.platform.common.Constants;
import com.campus.platform.common.Result;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.auth.service.TokenService;
import com.campus.platform.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 学生端登录拦截器。
 *
 * <p>从 {@code Authorization: Bearer <token>} 或 {@code ?token=} 取出 Sa-Token 会话，
 * 解析出学生 id 写入 {@link UserContext}。公开读取接口允许匿名访问；
 * 其余接口未登录返回 401，临时密码未修改时只放行改密接口。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    /** 公开读取白名单（仅 GET；含 {id} 的详情路径用数字约束，避免误放开写接口或个人端点） */
    private static final List<String> PUBLIC_READ_PATTERNS = List.of(
            "/api/idle/list",
            "/api/media/{resource}/{id:[0-9]+}/{index:[0-9]+}",
            "/api/activity/list",
            "/api/lostfound/list",
            "/api/notice/list",
            "/api/post/list",
            "/api/qa/list",
            "/api/partner/list",
            "/api/home/aggregate",
            "/api/site/config",
            "/api/idle/{id:[0-9]+}",
            "/api/activity/{id:[0-9]+}",
            "/api/lostfound/{id:[0-9]+}",
            "/api/notice/{id:[0-9]+}",
            "/api/qa/{id:[0-9]+}",
            "/api/partner/{id:[0-9]+}",
            "/api/post/{id:[0-9]+}",
            "/api/post/{id:[0-9]+}/comments",
            "/api/post/{id:[0-9]+}/images/{index:[0-9]+}"
    );

    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            Long uid = tokenService.studentId(resolveToken(request));
            if (uid == null) {
                if (isPublicRead(request)) {
                    return true;
                }
                writeUnauthorized(response, ResultCode.UNAUTHORIZED);
                return false;
            }
            // 临时密码登录后必须先改密，改密接口之外一律拒绝
            boolean pendingChange = userMapper.requiresPasswordChange(uid);
            if (pendingChange && !isPasswordUpdate(request)) {
                writeUnauthorized(response, ResultCode.FORBIDDEN);
                return false;
            }
            UserContext.set(uid, Constants.ROLE_STUDENT);
            return true;
        } catch (Exception e) {
            // 无效/过期会话：公开读取按匿名继续，受保护接口返回 401
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

    private boolean isPasswordUpdate(HttpServletRequest request) {
        return "PUT".equals(request.getMethod()) && "/api/user/password".equals(requestPath(request));
    }

    private String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return servletPath == null || servletPath.isEmpty() ? request.getRequestURI() : servletPath;
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
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(rc)));
    }
}
