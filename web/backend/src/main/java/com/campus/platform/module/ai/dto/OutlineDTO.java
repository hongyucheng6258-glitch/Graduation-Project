package com.campus.platform.module.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 复习提纲生成请求（v2：三种生成方式，无需用户手动填学科/章节/主题）。
 * <ul>
 *   <li>mode=subject（默认）：按当前筛选学科生成，subject 必填</li>
 *   <li>mode=selected：按选中的错题生成，wrongQuestionIds 必填</li>
 *   <li>mode=all：按全部错题生成薄弱点报告</li>
 * </ul>
 * 兼容旧端：直接传 subject+chapter+topic 也按 subject 模式处理。
 */
@Data
public class OutlineDTO {

    /** 生成方式：subject / selected / all，缺省按 subject 兼容旧端 */
    private String mode;

    /** subject 模式：学科 */
    private String subject;

    /** subject 模式：章节（可选） */
    private String chapter;

    /** subject 模式：主题（可选，缺省由系统根据学科错题归纳） */
    private String topic;

    /** selected 模式：选中的错题ID列表 */
    private List<Long> wrongQuestionIds;
}
