package com.campus.platform.module.admin.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.audit.AdminPermission;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.admin.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 管理端权限校验组件。
 *
 * <p>所有 /api/admin/** 入口先由 AdminInterceptor 校验管理员会话，
 * 再通过本组件按业务角色（super / audit）做细粒度限制。
 * 管理员被删除、禁用或降权时，会话由 TokenService#kickoutAdmin 清除。
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final AdminMapper adminMapper;

    /**
     * 校验管理员当前仍存在且可用（未被禁用）。
     *
     * @param adminId 管理员的 id
     */
    public Admin requireActive(Long adminId) {
        if (adminId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "管理员账号不存在，请重新登录");
        }
        if (admin.getStatus() != null && admin.getStatus() == 1) {
            throw new BizException(ResultCode.UNAUTHORIZED, "管理员账号已被禁用");
        }
        return admin;
    }

    public Admin currentAdmin() {
        return requireActive(UserContext.getUid());
    }

    public void check(Admin admin, AdminPermission permission) {
        if (!permission.allows(admin.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限执行此操作");
        }
    }

    public void require(AdminPermission permission) {
        check(currentAdmin(), permission);
    }

    public List<String> permissionsFor(Admin admin) {
        return Arrays.stream(AdminPermission.values())
                .filter(permission -> permission.allows(admin.getRole()))
                .map(AdminPermission::getCode)
                .toList();
    }

    /** 仅超级管理员可操作（当前登录管理员必须是 super） */
    public Admin requireSuper() {
        Admin admin = requireActive(UserContext.getUid());
        if (!"super".equals(admin.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限：仅超级管理员可操作");
        }
        return admin;
    }
}
