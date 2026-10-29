package com.campus.platform.module.ai.vo;

import com.campus.platform.module.ai.entity.WrongQuestion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 薄弱知识点报告（统计型）：知识点聚合 + 错因分布 + 最常错题目。
 */
@Data
public class WeakPointsVO {

    /** 知识点聚合（按关联错题数降序，Top 10） */
    private List<KnowledgeStat> knowledgePoints;

    /** 错因分布（按次数降序） */
    private List<ReasonStat> errorReasons;

    /** 最常错 Top 5（按错误次数降序） */
    private List<WrongQuestion> mostWrong;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeStat {
        private String name;
        /** 关联错题数 */
        private long wrongCount;
        /** 其中待复习数 */
        private long pendingCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonStat {
        private String reason;
        private long count;
    }
}
