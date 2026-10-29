package com.campus.platform.interceptor;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.R;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端拦截器：校验 JWT 且 role=admin，并核对管理员当前仍存在、可用、未被撤销。
 * <p>P1 修复：不能只相信 JWT 内的 role=admin —— 删除/禁用/降权后的管理员，
 * 其存量令牌在本拦截器内即被拒绝（配合 AdminPermissionService 的鉴权黑名单与 DB 状态核对）。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final AdminPermissionService adminPermissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) {
            write(response, ResultCode.UNAUTHORIZED);
            return false;
        }
        try {
            Long uid = jwtUtils.getUid(token);
            String role = jwtUtils.getRole(token);
            if (!Constants.ROLE_ADMIN.equals(role)) {
                write(response, ResultCode.FORBIDDEN);
                return false;
            }
            // 核对管理员记录存在、状态正常，且令牌签发时间晚于撤销时间（旧令牌失效、重登新令牌可用）
            Long issuedAt = jwtUtils.getIssuedAtMillis(token);
            adminPermissionService.requireActive(uid, issuedAt);
            UserContext.set(uid, role);
            return true;
        } catch (BizException e) {
            // 管理员不存在/被禁用/被撤销 → 登录态失效
            write(response, ResultCode.UNAUTHORIZED);
            return false;
        } catch (Exception e) {
            write(response, ResultCode.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void write(HttpServletResponse response, ResultCode rc) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(rc)));
    }
}
