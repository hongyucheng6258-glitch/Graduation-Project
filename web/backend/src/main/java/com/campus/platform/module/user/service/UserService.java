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
        userMapper.updateById(user);
        return user;
    }

    /** 修改密码：校验原密码 */
    public void updatePassword(Long uid, PasswordUpdateDTO dto) {
        User user = getById(uid);
        if (user.getPassword() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "小程序账号未设置密码，请先绑定账号");
        }
        if (!authService.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "原密码错误");
        }
        user.setPassword(authService.encode(dto.getNewPassword()));
        userMapper.updateById(user);
    }
}
