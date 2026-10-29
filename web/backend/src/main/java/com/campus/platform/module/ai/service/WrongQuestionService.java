package com.campus.platform.module.ai.service;

import com.campus.platform.module.ai.mapper.WrongQuestionGeneratedMapper;
import com.campus.platform.module.ai.mapper.WrongQuestionReviewMapper;
import com.campus.platform.module.ai.entity.WrongQuestionGenerated;
import com.campus.platform.module.ai.vo.WrongQuestionStatsVO;
import com.campus.platform.module.ai.mapper.WrongQuestionMapper;
import com.campus.platform.module.ai.entity.WrongQuestionReview;
import com.campus.platform.module.ai.dto.WrongQuestionDTO;
import com.campus.platform.module.ai.dto.WrongReviewDTO;
import com.campus.platform.module.ai.entity.WrongQuestion;
import com.campus.platform.module.ai.vo.WeakPointsVO;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本服务（B6，v2）：快速收录 + 状态筛选排序 + 今日复习 + 复习反馈状态机。
 */
@Service
@RequiredArgsConstructor
public class WrongQuestionService {

    private final WrongQuestionMapper wrongQuestionMapper;
    private final WrongQuestionReviewMapper wrongQuestionReviewMapper;
    private final WrongQuestionGeneratedMapper wrongQuestionGeneratedMapper;

    /** 排序方式 */
    private static final String SORT_CREATE_DESC = "create_desc";       // 最近收录
    private static final String SORT_LAST_REVIEW_ASC = "last_review_asc"; // 最久未复习
    private static final String SORT_WRONG_DESC = "wrong_desc";          // 错误次数最多
    private static final String SORT_DIFFICULTY_DESC = "difficulty_desc";// 难度最高

    /** 分页列表：学科 + 掌握状态筛选，四种排序 */
    public PageResult<WrongQuestion> list(Long userId, String subject, Integer status,
                                          String sort, int pageNum, int pageSize) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(StrUtil.isNotBlank(subject), WrongQuestion::getSubject, subject)
                .eq(status != null, WrongQuestion::getStatus, status);
        applySort(wrapper, sort);
        Page<WrongQuestion> page = wrongQuestionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page);
    }

    private void applySort(LambdaQueryWrapper<WrongQuestion> wrapper, String sort) {
        if (SORT_LAST_REVIEW_ASC.equals(sort)) {
            // 从未复习（null）最优先
            wrapper.last("ORDER BY last_review_time IS NULL DESC, last_review_time ASC, id DESC");
        } else if (SORT_WRONG_DESC.equals(sort)) {
            wrapper.orderByDesc(WrongQuestion::getWrongCount).orderByDesc(WrongQuestion::getId);
        } else if (SORT_DIFFICULTY_DESC.equals(sort)) {
            wrapper.last("ORDER BY FIELD(difficulty, '难', '中', '易') DESC, id DESC");
        } else {
            wrapper.orderByDesc(WrongQuestion::getId);
        }
    }

    public WrongQuestion getOwned(Long userId, Long id) {
        WrongQuestion wq = wrongQuestionMapper.selectById(id);
        if (wq == null) {
            throw new BizException(ResultCode.NOT_FOUND, "错题不存在");
        }
        if (!wq.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该错题");
        }
        return wq;
    }

    /** 批量按 ID 取归属当前用户的错题（AI 提纲 selected 模式用） */
    public List<WrongQuestion> listOwnedByIds(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .in(WrongQuestion::getId, ids)
                .orderByDesc(WrongQuestion::getId));
    }

    /** 当前用户全部错题（AI 提纲 all 模式用） */
    public List<WrongQuestion> listOwnedAll(Long userId) {
        return wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .orderByDesc(WrongQuestion::getId));
    }

    /** 按学科取错题（AI 提纲 subject 模式用） */
    public List<WrongQuestion> listOwnedBySubject(Long userId, String subject) {
        return wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getSubject, subject)
                .orderByDesc(WrongQuestion::getId));
    }

    /**
     * 快速收录（v2）：仅题目必填，学科为空保存为「待整理」。
     * 初始状态：待复习、错误次数 1、下次复习时间 = 现在（当天复习）。
     */
    public WrongQuestion create(Long userId, WrongQuestionDTO dto, String source) {
        WrongQuestion wq = new WrongQuestion();
        wq.setUserId(userId);
        wq.setSubject(normalizeSubject(dto.getSubject()));
        wq.setTag(blankToNull(dto.getTag()));
        wq.setQuestion(dto.getQuestion());
        wq.setCorrectAnswer(blankToNull(dto.resolveCorrectAnswer()));
        wq.setAnalysis(blankToNull(dto.getAnalysis()));
        wq.setMyAnswer(blankToNull(dto.getMyAnswer()));
        wq.setErrorReason(blankToNull(dto.getErrorReason()));
        wq.setQuestionType(blankToNull(dto.getQuestionType()));
        wq.setChapter(blankToNull(dto.getChapter()));
        wq.setDifficulty(blankToNull(dto.getDifficulty()));
        wq.setKnowledgePoints(blankToNull(dto.getKnowledgePoints()));
        wq.setQuestionImage(blankToNull(dto.getQuestionImage()));
        wq.setNote(blankToNull(dto.getNote()));
        wq.setSource(source);
        wq.setStatus(Constants.WQ_STATUS_PENDING);
        wq.setMasteryScore(0);
        wq.setReviewCount(0);
        wq.setWrongCount(1);
        wq.setConsecutiveCorrectCount(0);
        wq.setNextReviewTime(LocalDateTime.now());
        wrongQuestionMapper.insert(wq);
        return wq;
    }

    public WrongQuestion update(Long userId, Long id, WrongQuestionDTO dto) {
        WrongQuestion wq = getOwned(userId, id);
        wq.setSubject(normalizeSubject(dto.getSubject()));
        wq.setTag(blankToNull(dto.getTag()));
        wq.setQuestion(dto.getQuestion());
        wq.setCorrectAnswer(blankToNull(dto.resolveCorrectAnswer()));
        wq.setAnalysis(blankToNull(dto.getAnalysis()));
        wq.setMyAnswer(blankToNull(dto.getMyAnswer()));
        wq.setErrorReason(blankToNull(dto.getErrorReason()));
        wq.setQuestionType(blankToNull(dto.getQuestionType()));
        wq.setChapter(blankToNull(dto.getChapter()));
        wq.setDifficulty(blankToNull(dto.getDifficulty()));
        wq.setKnowledgePoints(blankToNull(dto.getKnowledgePoints()));
        wq.setQuestionImage(blankToNull(dto.getQuestionImage()));
        wq.setNote(blankToNull(dto.getNote()));
        wrongQuestionMapper.updateById(wq);
        return wq;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        getOwned(userId, id);
        wrongQuestionMapper.deleteById(id);
        wrongQuestionReviewMapper.delete(new LambdaQueryWrapper<WrongQuestionReview>()
                .eq(WrongQuestionReview::getWrongQuestionId, id));
    }

    /** 当前用户全部学科标签（筛选条用） */
    public List<String> subjects(Long userId) {
        return wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .select(WrongQuestion::getSubject)
                        .groupBy(WrongQuestion::getSubject))
                .stream().map(WrongQuestion::getSubject).toList();
    }

    /** 顶部数据概览：总数 / 待复习 / 已掌握 / 本周复习次数 等 */
    public WrongQuestionStatsVO stats(Long userId) {
        WrongQuestionStatsVO vo = new WrongQuestionStatsVO();
        vo.setTotal(count(userId, null));
        vo.setPending(count(userId, Constants.WQ_STATUS_PENDING));
        vo.setReviewing(count(userId, Constants.WQ_STATUS_REVIEWING));
        vo.setBasic(count(userId, Constants.WQ_STATUS_BASIC));
        vo.setMastered(count(userId, Constants.WQ_STATUS_MASTERED));
        vo.setTodayPending(todayPendingCount(userId));

        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        vo.setWeekReviewCount(wrongQuestionReviewMapper.selectCount(
                new LambdaQueryWrapper<WrongQuestionReview>()
                        .eq(WrongQuestionReview::getUserId, userId)
                        .ge(WrongQuestionReview::getReviewTime, weekStart)));
        return vo;
    }

    /** 今日待复习数量：待复习状态 或 已到下次复习时间 */
    private long todayPendingCount(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .and(w -> w.eq(WrongQuestion::getStatus, Constants.WQ_STATUS_PENDING)
                        .or().le(WrongQuestion::getNextReviewTime, now)));
    }

    private long count(Long userId, Integer status) {
        return wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(status != null, WrongQuestion::getStatus, status));
    }

    /** 今日待复习：待复习状态 或 已到下次复习时间 */
    public List<WrongQuestion> todayReview(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .and(w -> w.eq(WrongQuestion::getStatus, Constants.WQ_STATUS_PENDING)
                        .or().le(WrongQuestion::getNextReviewTime, now))
                .orderByAsc(WrongQuestion::getNextReviewTime)
                .last("LIMIT 50"));
    }

    /**
     * 复习反馈状态机（v2 核心闭环）：
     * <pre>
     * 仍然不会  → 待复习，当天复习，错误次数+1，连续答对清零
     * 有点理解  → 复习中，1 天后复习，连续答对清零
     * 基本掌握  → 连续答对+1，3 天后复习（≥3 次升至已掌握）
     * 已完全掌握 → 连续答对+1，7 天后复习（≥2 次升至已掌握）
     * </pre>
     */
    @Transactional
    public WrongQuestion reviewFeedback(Long userId, WrongReviewDTO dto) {
        WrongQuestion wq = getOwned(userId, dto.getWrongQuestionId());

        // 1. 写复习记录（isCorrect 由服务端按掌握程度推导，不信任客户端传值）
        WrongQuestionReview record = new WrongQuestionReview();
        record.setUserId(userId);
        record.setWrongQuestionId(wq.getId());
        record.setUserAnswer(blankToNull(dto.getUserAnswer()));
        record.setIsCorrect(dto.getMasteryLevel() >= Constants.WQ_LEVEL_BASIC ? 1 : 0);
        record.setMasteryLevel(dto.getMasteryLevel());
        record.setReviewNote(blankToNull(dto.getReviewNote()));
        record.setReviewTime(LocalDateTime.now());
        wrongQuestionReviewMapper.insert(record);

        // 2. 状态机更新
        int level = dto.getMasteryLevel();
        int reviewCount = (wq.getReviewCount() == null ? 0 : wq.getReviewCount()) + 1;
        int consecutive = wq.getConsecutiveCorrectCount() == null ? 0 : wq.getConsecutiveCorrectCount();
        LocalDateTime now = LocalDateTime.now();

        switch (level) {
            case Constants.WQ_LEVEL_STILL_WRONG -> {
                wq.setStatus(Constants.WQ_STATUS_PENDING);
                wq.setWrongCount((wq.getWrongCount() == null ? 1 : wq.getWrongCount()) + 1);
                wq.setConsecutiveCorrectCount(0);
                wq.setNextReviewTime(now); // 当天复习
            }
            case Constants.WQ_LEVEL_A_LITTLE -> {
                wq.setStatus(Constants.WQ_STATUS_REVIEWING);
                wq.setConsecutiveCorrectCount(0);
                wq.setNextReviewTime(now.plusDays(1));
            }
            case Constants.WQ_LEVEL_BASIC -> {
                consecutive++;
                wq.setConsecutiveCorrectCount(consecutive);
                wq.setStatus(consecutive >= 3 ? Constants.WQ_STATUS_MASTERED : Constants.WQ_STATUS_BASIC);
                wq.setNextReviewTime(now.plusDays(reviewIntervalDays(consecutive)));
            }
            case Constants.WQ_LEVEL_FULLY -> {
                consecutive++;
                wq.setConsecutiveCorrectCount(consecutive);
                wq.setStatus(consecutive >= 2 ? Constants.WQ_STATUS_MASTERED : Constants.WQ_STATUS_BASIC);
                wq.setNextReviewTime(now.plusDays(reviewIntervalDays(consecutive)));
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST, "掌握程度取值0-3");
        }

        wq.setReviewCount(reviewCount);
        wq.setMasteryScore(masteryScore(wq.getStatus()));
        wq.setLastReviewTime(now);
        wrongQuestionMapper.updateById(wq);
        return wq;
    }

    /** 复习间隔（天）：连续答对 1/2/3+ 次 → 1/3/7 天 */
    private int reviewIntervalDays(int consecutive) {
        int index = Math.min(consecutive, Constants.WQ_REVIEW_INTERVALS_DAYS.length - 1);
        return Constants.WQ_REVIEW_INTERVALS_DAYS[index];
    }

    /** 掌握状态 → 掌握度分数 */
    private int masteryScore(int status) {
        return switch (status) {
            case Constants.WQ_STATUS_PENDING -> 5;
            case Constants.WQ_STATUS_REVIEWING -> 30;
            case Constants.WQ_STATUS_BASIC -> 70;
            case Constants.WQ_STATUS_MASTERED -> 100;
            default -> 0;
        };
    }

    /** 学科归一化：空 → 待整理 */
    private String normalizeSubject(String subject) {
        return StrUtil.isBlank(subject) ? Constants.WQ_SUBJECT_UNSORTED : subject.trim();
    }

    private String blankToNull(String s) {
        return StrUtil.isBlank(s) ? null : s.trim();
    }

    // ==================== 第二阶段：AI 智能整理与薄弱点报告 ====================

    /**
     * 应用 AI 智能整理结果：只填用户尚未填写的字段（不覆盖），标记已整理。
     *
     * @param fields key: questionType/subject/chapter/difficulty/knowledgePoints/errorReason/summary
     */
    public WrongQuestion applyAiAnalysis(Long userId, Long id, Map<String, String> fields) {
        WrongQuestion wq = getOwned(userId, id);
        if (StrUtil.isNotBlank(fields.get("questionType")) && StrUtil.isBlank(wq.getQuestionType())) {
            wq.setQuestionType(fields.get("questionType"));
        }
        if (StrUtil.isNotBlank(fields.get("subject"))
                && (wq.getSubject() == null || Constants.WQ_SUBJECT_UNSORTED.equals(wq.getSubject()))) {
            wq.setSubject(fields.get("subject"));
        }
        if (StrUtil.isNotBlank(fields.get("chapter")) && StrUtil.isBlank(wq.getChapter())) {
            wq.setChapter(fields.get("chapter"));
        }
        if (StrUtil.isNotBlank(fields.get("difficulty")) && StrUtil.isBlank(wq.getDifficulty())) {
            wq.setDifficulty(fields.get("difficulty"));
        }
        if (StrUtil.isNotBlank(fields.get("knowledgePoints")) && StrUtil.isBlank(wq.getKnowledgePoints())) {
            wq.setKnowledgePoints(fields.get("knowledgePoints"));
        }
        if (StrUtil.isNotBlank(fields.get("errorReason")) && StrUtil.isBlank(wq.getErrorReason())) {
            wq.setErrorReason(fields.get("errorReason"));
        }
        if (StrUtil.isNotBlank(fields.get("summary")) && StrUtil.isBlank(wq.getAnalysis())) {
            wq.setAnalysis(fields.get("summary"));
        }
        wq.setAnalyzeStatus(2);
        wrongQuestionMapper.updateById(wq);
        return wq;
    }

    /** AI 整理失败：标记 analyzeStatus=1，错题本身不受影响 */
    public void markAnalyzeFailed(Long userId, Long id) {
        WrongQuestion wq = getOwned(userId, id);
        wq.setAnalyzeStatus(1);
        wrongQuestionMapper.updateById(wq);
    }

    /** 薄弱知识点报告：知识点聚合 + 错因分布 + 最常错题目 */
    public WeakPointsVO weakPoints(Long userId) {        List<WrongQuestion> all = listOwnedAll(userId);
        WeakPointsVO vo = new WeakPointsVO();

        Map<String, int[]> kpAgg = new LinkedHashMap<>(); // 知识点 -> [错题数, 待复习数]
        Map<String, Integer> reasonAgg = new LinkedHashMap<>();
        for (WrongQuestion wq : all) {
            if (StrUtil.isNotBlank(wq.getKnowledgePoints())) {
                for (String kp : wq.getKnowledgePoints().split("[，,、;；\\s]+")) {
                    if (StrUtil.isBlank(kp)) {
                        continue;
                    }
                    int[] v = kpAgg.computeIfAbsent(kp, k -> new int[2]);
                    v[0]++;
                    if (wq.getStatus() != null && wq.getStatus() == Constants.WQ_STATUS_PENDING) {
                        v[1]++;
                    }
                }
            }
            if (StrUtil.isNotBlank(wq.getErrorReason())) {
                reasonAgg.merge(wq.getErrorReason(), 1, Integer::sum);
            }
        }

        vo.setKnowledgePoints(kpAgg.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .map(e -> new WeakPointsVO.KnowledgeStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .limit(10)
                .toList());
        vo.setErrorReasons(reasonAgg.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new WeakPointsVO.ReasonStat(e.getKey(), e.getValue()))
                .toList());
        vo.setMostWrong(all.stream()
                .sorted(Comparator.comparing(WrongQuestion::getWrongCount,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .limit(5)
                .toList());
        return vo;
    }

    // ==================== 第三阶段：AI 练习题 ====================

    /**
     * 把 AI 生成的练习题转正式错题（source=ai，继承原错题学科/标签/题型等）。
     * 幂等：先条件更新抢占 status=0→1（并发下仅一个成功），再插入错题。
     */
    @Transactional
    public WrongQuestion saveGenerated(Long userId, Long generatedId) {
        WrongQuestionGenerated g = wrongQuestionGeneratedMapper.selectById(generatedId);
        if (g == null || !g.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "练习题不存在");
        }
        // 抢占式幂等：0 行更新说明已被保存或不存在
        // （UpdateWrapper 用字符串列名，避免 mock 环境下 LambdaUpdateWrapper 的 lambda 缓存问题）
        int claimed = wrongQuestionGeneratedMapper.update(null,
                new UpdateWrapper<WrongQuestionGenerated>()
                        .eq("id", generatedId)
                        .eq("status", Constants.GENERATED_STATUS_PRACTICING)
                        .set("status", Constants.GENERATED_STATUS_SAVED));
        if (claimed == 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该练习题已加入错题本");
        }
        WrongQuestion source = getOwned(userId, g.getWrongQuestionId());

        WrongQuestion wq = new WrongQuestion();
        wq.setUserId(userId);
        wq.setSubject(source.getSubject());
        wq.setTag(source.getTag());
        wq.setQuestion(g.getQuestion());
        wq.setCorrectAnswer(g.getAnswer());
        wq.setAnalysis(g.getAnalysis());
        wq.setQuestionType(source.getQuestionType());
        wq.setChapter(source.getChapter());
        wq.setDifficulty(source.getDifficulty());
        wq.setKnowledgePoints(source.getKnowledgePoints());
        wq.setSource("ai");
        wq.setStatus(Constants.WQ_STATUS_PENDING);
        wq.setMasteryScore(0);
        wq.setReviewCount(0);
        wq.setWrongCount(1);
        wq.setConsecutiveCorrectCount(0);
        wq.setNextReviewTime(LocalDateTime.now());
        wrongQuestionMapper.insert(wq);
        return wq;
    }
}
