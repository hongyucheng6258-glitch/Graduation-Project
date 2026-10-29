package com.campus.platform.module.admin.service;

import com.campus.platform.module.admin.dto.AdminSaveDTO;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.admin.entity.Admin;

import com.campus.platform.module.auth.service.AuthService;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端-用户与管理员管理（D1/D8）。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final AuthService authService;
    private final RedisUtils redisUtils;
    private final AdminPermissionService adminPermissionService;

    /** 用户列表/搜索（学号/昵称/状态） */
    public PageResult<User> listUsers(String keyword, Integer status, int pageNum, int pageSize) {
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<User>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(User::getStudentNo, keyword)
                                .or().like(User::getNickname, keyword))
                        .eq(status != null, User::getStatus, status)
                        .orderByDesc(User::getId));
        return PageResult.of(page);
    }

    /** 禁用/解封；禁用(1)时将该学生用户加入鉴权黑名单，使其存量 token 立即失效 */
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        // 状态机联动黑名单（key 与 JwtInterceptor 保持一致，按角色区分避免与管理员 id 冲突）：
        // 禁用 -> 写入黑名单；解封 -> 移除黑名单。
        // 角色段统一用 Constants.ROLE_STUDENT，避免将来该常量被改而写入端不同步导致黑名单静默失效
        String blackKey = "auth:blacklist:" + Constants.ROLE_STUDENT + ":" + id;
        if (status != null && status == 1) {
            redisUtils.set(blackKey, "1");
        } else {
            redisUtils.delete(blackKey);
        }
    }

    /** 重置密码（重置为 123456，提示用户尽快修改） */
    public void resetPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(authService.encode("123456"));
        userMapper.updateById(user);
    }

    // ---------- 子管理员管理（D8，P2） ----------

    /** 查询管理员（供 Controller 权限校验用） */
    public Admin getAdminById(Long id) {
        return adminMapper.selectById(id);
    }

    public PageResult<Admin> listAdmins(int pageNum, int pageSize) {
        Page<Admin> page = adminMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Admin>().orderByDesc(Admin::getId));
        return PageResult.of(page);
    }

    public Admin createAdmin(AdminSaveDTO dto) {
        Long exist = adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, dto.getUsername()));
        if (exist > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在");
        }
        if (StrUtil.isBlank(dto.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "初始密码不能为空");
        }
        Admin admin = new Admin();
        admin.setUsername(dto.getUsername());
        admin.setPassword(authService.encode(dto.getPassword()));
        admin.setNickname(dto.getNickname());
        admin.setRole(dto.getRole());
        admin.setStatus(0);
        adminMapper.insert(admin);
        return admin;
    }

    public Admin updateAdmin(Long id, AdminSaveDTO dto) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ResultCode.NOT_FOUND, "管理员不存在");
        }
        boolean roleChanged = !java.util.Objects.equals(admin.getRole(), dto.getRole());
        admin.setNickname(dto.getNickname());
        admin.setRole(dto.getRole());
        if (StrUtil.isNotBlank(dto.getPassword())) {
            admin.setPassword(authService.encode(dto.getPassword()));
        }
        adminMapper.updateById(admin);
        // 降权/改角色后旧令牌必须失效，防止按旧角色继续操作
        if (roleChanged) {
            adminPermissionService.revokeToken(id);
        }
        return admin;
    }

    public void deleteAdmin(Long id, Long currentAdminId) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ResultCode.NOT_FOUND, "管理员不存在");
        }
        if (id.equals(currentAdminId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除自己的账号");
        }
        if ("super".equals(admin.getRole())) {
            Long superCount = adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                    .eq(Admin::getRole, "super"));
            if (superCount <= 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "不能删除最后一个超级管理员");
            }
        }
        adminMapper.deleteById(id);
        // 删除后旧令牌立即失效
        adminPermissionService.revokeToken(id);
    }
}
