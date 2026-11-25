package com.campus.platform.module.user.service;

import com.campus.platform.module.user.dto.PasswordUpdateDTO;
import com.campus.platform.module.user.dto.ProfileUpdateDTO;
import com.campus.platform.module.user.entity.User;

/**
 * 用户服务接口：资料查询/编辑、修改密码。
 */
public interface IUserService {

    /** 按 id 查询用户，不存在抛 404 */
    User getById(Long uid);

    /** 资料编辑（昵称/头像/性别/简介/手机号） */
    User updateProfile(Long uid, ProfileUpdateDTO dto);

    /** 修改密码：校验原密码 */
    void updatePassword(Long uid, PasswordUpdateDTO dto);
}
