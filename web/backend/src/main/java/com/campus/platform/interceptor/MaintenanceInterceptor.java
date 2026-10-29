package com.campus.platform.interceptor;

import com.campus.platform.common.R;
import com.campus.platform.config.SystemConfigHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 维护模式拦截器：当系统配置 maintenance_mode=true 时，
 * 学生端所有 API 返回 503 维护提示（管理端 /api/admin/** 不受影响，管理员可随时关闭维护）。
 */
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    private final SystemConfigHolder systemConfigHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (systemConfigHolder.isMaintenanceMode()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                    R.fail(503, "系统维护中，请稍后再试")));
            return false;
        }
        return true;
    }
}
