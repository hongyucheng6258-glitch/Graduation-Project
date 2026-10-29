package com.campus.platform.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员表：区分 super（超级管理员）/audit（审核员）两种角色。
 */
@Data
@TableName("admin")
public class Admin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密码，永不返回给前端 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    /** super/audit */
    private String role;

    /** 0正常 1禁用 */
    private Integer status;

    private LocalDateTime createTime;
}
