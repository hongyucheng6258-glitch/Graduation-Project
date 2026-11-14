package com.campus.platform.module.user.service;

import com.campus.platform.module.user.dto.PasswordUpdateDTO;
import com.campus.platform.module.user.dto.ProfileUpdateDTO;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.user.entity.User;

import com.campus.platform.module.auth.service.AuthService;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户服务：资料查询/编辑、修改密码。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final AuthService authService;

    public User getById(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /** 资料编辑（昵称/头像/性别/简介/手机号） */
    public User updateProfile(Long uid, ProfileUpdateDTO dto) {
        User user = getById(uid);
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        saveProfile(user, uid);
        return user;
    }

    private void saveProfile(User user, Long uid) {
        // 只把个人资料字段交给 MyBatis-Plus 更新。
        User profile = new User();
        profile.setId(uid);
        profile.setNickname(user.getNickname());
        profile.setAvatar(user.getAvatar());
        profile.setBio(user.getBio());
        profile.setGender(user.getGender());
        profile.setPhone(user.getPhone());
        userMapper.updateById(profile);
    }

    /** 修改密码：校验原密码 */
    public void updatePassword(Long uid, PasswordUpdateDTO dto) {
        User user = getById(uid);
        if (user.getPassword() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "小程序账号未设置密码，请先绑定账号");
        }
        if (user.getTemporaryPasswordExpiresAt() != null
                && !java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).isBefore(user.getTemporaryPasswordExpiresAt())) {
            throw new BizException(ResultCode.BAD_REQUEST, "临时密码已过期，请联系管理员重新重置");
        }
        if (!authService.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "原密码错误");
        }
        if (user.getTemporaryPasswordExpiresAt() != null && dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能与临时密码相同");
        }
        if (userMapper.replacePassword(uid, user.getPassword(), authService.encode(dto.getNewPassword()), null) != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码已变化，请重新登录");
        }
    }
}
