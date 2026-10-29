package com.campus.platform.module.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 校园互助问答-问题 */
@Data
@TableName("campus_question")
public class CampusQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    /** 课程/考试/技术/生活/其他 */
    private String category;
    /** 0待答 1已解决 */
    private Integer status;
    private Long acceptedAnswerId;
    private Integer viewCount;
    private LocalDateTime createTime;
}
