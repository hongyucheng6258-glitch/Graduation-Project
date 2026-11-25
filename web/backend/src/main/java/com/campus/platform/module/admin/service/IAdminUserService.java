package com.campus.platform.module.admin.service;

import com.campus.platform.module.admin.dto.AdminSaveDTO;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.auth.service.IAuthService;
import com.campus.platform.module.auth.service.TokenService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AdminUserService 服务接口。
 */
public interface IAdminUserService {

    PageResult<User> listUsers(String keyword, Integer status, Long record, int pageNum, int pageSize);

    void updateStatus(Long id, Integer status);

    void setRecoveredPassword(User user, String password);

    String resetPassword(Long id);

    Admin getAdminById(Long id);

    PageResult<Admin> listAdmins(int pageNum, int pageSize);

    Admin createAdmin(AdminSaveDTO dto);

    Admin updateAdmin(Long id, AdminSaveDTO dto);

    void deleteAdmin(Long id, Long currentAdminId);
}
