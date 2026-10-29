package com.campus.platform.module.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 失物 AI 智能匹配结果 */
@Data
public class LostMatchVO {

    private Long id;
    private String title;
    private String location;
    private LocalDateTime happenTime;
    private String contact;
    /** AI 匹配理由 */
    private String reason;
    /** 发布者昵称 */
    private String publisherNickname;
}
