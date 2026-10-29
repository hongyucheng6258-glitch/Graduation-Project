package com.campus.platform.module.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 校园互助问答-回答 */
@Data
@TableName("campus_answer")
public class CampusAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Long userId;
    private String content;
    private Integer isAccepted;
    private LocalDateTime createTime;
}
