package com.campus.platform.module.auth.service;

import com.campus.platform.module.auth.dto.AdminLoginDTO;
import com.campus.platform.module.auth.vo.AdminLoginVO;
import com.campus.platform.module.auth.dto.RegisterDTO;
import com.campus.platform.module.auth.dto.LoginDTO;
import com.campus.platform.module.auth.vo.LoginVO;

import com.campus.platform.module.user.service.UserService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.admin.entity.Admin;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.admin.mapper.AdminMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务：Web 注册/登录、管理员登录。
 * 统一签发 student JWT（claims: uid, role）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final JwtUtils jwtUtils;
    private final SystemConfigHolder systemConfigHolder;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Web 注册（学号+密码+昵称）。
     */
    public LoginVO register(RegisterDTO dto) {
        if (!systemConfigHolder.isRegisterEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "当前已关闭注册，请联系管理员");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, dto.getStudentNo()));
        if (count > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该学号已注册");
        }
        User user = new User();
        user.setStudentNo(dto.getStudentNo());
        user.setNickname(dto.getNickname());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 新用户默认状态从系统配置读取（0正常 1禁用），管理端可在线调整
        user.setStatus(systemConfigHolder.getInt("user_default_status", Constants.USER_STATUS_NORMAL));
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.insert(user);
        return buildLoginVO(user);
    }

    /**
     * Web 账号密码登录。
     */
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, dto.getStudentNo()));
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "学号或密码错误");
        }
        if (Constants.USER_STATUS_BANNED == user.getStatus()) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        return buildLoginVO(user);
    }

    /**
     * 管理员独立登录（role=admin 的 JWT）。
     */
    public AdminLoginVO adminLogin(AdminLoginDTO dto) {
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, dto.getUsername()));
        if (admin == null || !passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名或密码错误");
        }
        if (admin.getStatus() != null && admin.getStatus() == 1) {
            throw new BizException(ResultCode.FORBIDDEN, "管理员账号已被禁用");
        }
        String token = jwtUtils.generate(admin.getId(), Constants.ROLE_ADMIN);
        return new AdminLoginVO(token, admin);
    }

    /** 密码校验（供 UserService 改密复用） */
    public boolean matches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }

    /** BCrypt 加密 */
    public String encode(String raw) {
        return passwordEncoder.encode(raw);
    }

    private LoginVO buildLoginVO(User user) {
        String token = jwtUtils.generate(user.getId(), Constants.ROLE_STUDENT);
        return new LoginVO(token, user);
    }
}
