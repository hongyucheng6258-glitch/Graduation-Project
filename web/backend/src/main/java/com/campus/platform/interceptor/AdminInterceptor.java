package com.campus.platform.interceptor;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.Result;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.auth.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端登录拦截器：校验管理员 Sa-Token 会话，并核对管理员当前仍存在、可用。
 *
 * <p>管理员被删除、禁用或降权时，TokenService#kickoutAdmin 会清掉其全部会话，
 * 存量 token 在下一次请求即失效。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final AdminPermissionService adminPermissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        Long adminId = tokenService.adminId(token);
        if (adminId == null) {
            write(response, ResultCode.UNAUTHORIZED);
            return false;
        }
        try {
            adminPermissionService.requireActive(adminId);
            UserContext.set(adminId, Constants.ROLE_ADMIN);
            return true;
        } catch (BizException e) {
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

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }

    private void write(HttpServletResponse response, ResultCode rc) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(rc)));
    }
}
