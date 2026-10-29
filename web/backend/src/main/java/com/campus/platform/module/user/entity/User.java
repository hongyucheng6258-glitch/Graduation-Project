package com.campus.platform.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表：Web 账号密码登录。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号（唯一，仅格式校验不做真认证） */
    private String studentNo;

    private String nickname;

    /** BCrypt 密码，永不返回给前端 */
    @JsonIgnore
    private String password;

    /** 手机号 */
    private String phone;


    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender;

    private String bio;

    /** 0正常 1禁用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
