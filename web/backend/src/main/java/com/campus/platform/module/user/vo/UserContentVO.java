package com.campus.platform.module.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 他人主页 · 内容聚合项（闲置/动态/活动/失物统一精简结构）。
 */
@Data
public class UserContentVO {

    private Long id;

    /** idle / post / activity / lostfound */
    private String type;

    private String title;

    /** 首图 URL */
    private String image;

    /** 业务状态 */
    private Integer status;

    /** 状态文案 */
    private String statusText;

    /** 补充信息：闲置=期望换物；活动=开始时间；失物=地点 */
    private String extra;

    private LocalDateTime createTime;
}
