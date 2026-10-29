package com.campus.platform.module.ai;

import com.campus.platform.module.ai.mapper.WrongQuestionGeneratedMapper;
import com.campus.platform.module.ai.mapper.WrongQuestionReviewMapper;
import com.campus.platform.module.ai.entity.WrongQuestionGenerated;
import com.campus.platform.module.ai.service.WrongQuestionService;
import com.campus.platform.module.ai.vo.WrongQuestionStatsVO;
import com.campus.platform.module.ai.mapper.WrongQuestionMapper;
import com.campus.platform.module.ai.entity.WrongQuestionReview;
import com.campus.platform.module.ai.dto.WrongQuestionDTO;
import com.campus.platform.module.ai.dto.WrongReviewDTO;
import com.campus.platform.module.ai.entity.WrongQuestion;
import com.campus.platform.module.ai.vo.WeakPointsVO;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("错题本 v2：快速收录 / 复习反馈状态机 / 统计")
class WrongQuestionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private WrongQuestionReviewMapper wrongQuestionReviewMapper;
    @Mock
    private WrongQuestionGeneratedMapper wrongQuestionGeneratedMapper;

    @InjectMocks
    private WrongQuestionService service;

    // ---------- 快速收录 ----------

    @Test
    @DisplayName("学科为空时保存为「待整理」，初始状态为待复习且当天可复习")
    void create_withoutSubject_shouldUseUnsortedAndPending() {
        WrongQuestionDTO dto = new WrongQuestionDTO();
        dto.setQuestion("1+1=？");

        WrongQuestion saved = service.create(USER_ID, dto, "manual");

        assertThat(saved.getSubject()).isEqualTo(Constants.WQ_SUBJECT_UNSORTED);
        assertThat(saved.getStatus()).isEqualTo(Constants.WQ_STATUS_PENDING);
        assertThat(saved.getWrongCount()).isEqualTo(1);
        assertThat(saved.getReviewCount()).isEqualTo(0);
        assertThat(saved.getMasteryScore()).isEqualTo(0);
        assertThat(saved.getNextReviewTime()).isNotNull();
        verify(wrongQuestionMapper).insert(saved);
    }

    @Test
    @DisplayName("新端 correctAnswer 与旧端 answer 字段均能归一化为正确答案")
    void create_shouldAcceptBothAnswerFieldNames() {
        WrongQuestionDTO newField = new WrongQuestionDTO();
        newField.setQuestion("题");
        newField.setCorrectAnswer("新答案");
        WrongQuestion w1 = service.create(USER_ID, newField, "manual");
        assertThat(w1.getCorrectAnswer()).isEqualTo("新答案");

        WrongQuestionDTO oldField = new WrongQuestionDTO();
        oldField.setQuestion("题");
        oldField.setAnswer("旧答案");
        WrongQuestion w2 = service.create(USER_ID, oldField, "manual");
        assertThat(w2.getCorrectAnswer()).isEqualTo("旧答案");
    }

    @Test
    @DisplayName("完整收录：全部可选字段落库")
    void create_withFullFields() {
        WrongQuestionDTO dto = new WrongQuestionDTO();
        dto.setSubject("Java");
        dto.setTag("多线程");
        dto.setQuestion("synchronized 锁的是什么？");
        dto.setCorrectAnswer("对象");
        dto.setMyAnswer("类");
        dto.setErrorReason("概念不清");
        dto.setQuestionType("简答");
        dto.setChapter("并发");
        dto.setDifficulty("难");
        dto.setKnowledgePoints("锁,并发");

        WrongQuestion saved = service.create(USER_ID, dto, "manual");

        assertThat(saved.getSubject()).isEqualTo("Java");
        assertThat(saved.getMyAnswer()).isEqualTo("类");
        assertThat(saved.getErrorReason()).isEqualTo("概念不清");
        assertThat(saved.getDifficulty()).isEqualTo("难");
        assertThat(saved.getKnowledgePoints()).isEqualTo("锁,并发");
    }

    @Test
    @DisplayName("越权访问他人错题应被拒绝")
    void getOwned_shouldRejectOtherUser() {
        WrongQuestion other = new WrongQuestion();
        other.setId(99L);
        other.setUserId(2L);
        when(wrongQuestionMapper.selectById(99L)).thenReturn(other);

        assertThatThrownBy(() -> service.getOwned(USER_ID, 99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问");
    }

    // ---------- 复习反馈状态机 ----------

    private WrongQuestion owned(Long id, int status, int wrongCount, int consecutive) {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(id);
        wq.setUserId(USER_ID);
        wq.setSubject("Java");
        wq.setQuestion("题");
        wq.setStatus(status);
        wq.setWrongCount(wrongCount);
        wq.setConsecutiveCorrectCount(consecutive);
        wq.setReviewCount(0);
        wq.setMasteryScore(0);
        return wq;
    }

    private WrongReviewDTO review(Long id, int level, int isCorrect) {
        WrongReviewDTO dto = new WrongReviewDTO();
        dto.setWrongQuestionId(id);
        dto.setMasteryLevel(level);
        dto.setIsCorrect(isCorrect);
        dto.setUserAnswer("我的作答");
        return dto;
    }

    @Test
    @DisplayName("仍然不会：回到待复习、当天复习、错误次数+1、连续答对清零")
    void review_stillWrong_shouldResetToPending() {
        WrongQuestion wq = owned(1L, Constants.WQ_STATUS_BASIC, 3, 2);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        WrongQuestion result = service.reviewFeedback(USER_ID, review(1L, Constants.WQ_LEVEL_STILL_WRONG, 0));

        assertThat(result.getStatus()).isEqualTo(Constants.WQ_STATUS_PENDING);
        assertThat(result.getWrongCount()).isEqualTo(4);
        assertThat(result.getConsecutiveCorrectCount()).isZero();
        assertThat(result.getReviewCount()).isEqualTo(1);
        assertThat(result.getMasteryScore()).isEqualTo(5);
        assertThat(result.getNextReviewTime()).isNotNull();
        verify(wrongQuestionReviewMapper).insert(any(WrongQuestionReview.class));
        verify(wrongQuestionMapper).updateById(wq);
    }

    @Test
    @DisplayName("有点理解：复习中、1 天后复习、连续答对清零")
    void review_aLittle_shouldSetReviewing() {
        WrongQuestion wq = owned(1L, Constants.WQ_STATUS_PENDING, 1, 0);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        WrongQuestion result = service.reviewFeedback(USER_ID, review(1L, Constants.WQ_LEVEL_A_LITTLE, 1));

        assertThat(result.getStatus()).isEqualTo(Constants.WQ_STATUS_REVIEWING);
        assertThat(result.getConsecutiveCorrectCount()).isZero();
        assertThat(result.getNextReviewTime()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("基本掌握：连续答对+1，3 天后复习；连续 3 次升至已掌握")
    void review_basic_shouldAdvanceConsecutive() {
        WrongQuestion wq = owned(1L, Constants.WQ_STATUS_PENDING, 1, 1);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        WrongQuestion result = service.reviewFeedback(USER_ID, review(1L, Constants.WQ_LEVEL_BASIC, 1));

        assertThat(result.getConsecutiveCorrectCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(Constants.WQ_STATUS_BASIC);

        // 再答对一次 → 连续 3 次 → 已掌握
        WrongQuestion wq2 = owned(2L, Constants.WQ_STATUS_BASIC, 1, 2);
        when(wrongQuestionMapper.selectById(2L)).thenReturn(wq2);
        WrongQuestion result2 = service.reviewFeedback(USER_ID, review(2L, Constants.WQ_LEVEL_BASIC, 1));
        assertThat(result2.getStatus()).isEqualTo(Constants.WQ_STATUS_MASTERED);
        assertThat(result2.getMasteryScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("已完全掌握：连续 2 次升至已掌握，3 天后复习")
    void review_fully_shouldMaster() {
        WrongQuestion wq = owned(1L, Constants.WQ_STATUS_REVIEWING, 1, 1);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        WrongQuestion result = service.reviewFeedback(USER_ID, review(1L, Constants.WQ_LEVEL_FULLY, 1));

        assertThat(result.getStatus()).isEqualTo(Constants.WQ_STATUS_MASTERED);
        assertThat(result.getConsecutiveCorrectCount()).isEqualTo(2);
        assertThat(result.getNextReviewTime()).isAfter(LocalDateTime.now().plusDays(2));
        assertThat(result.getNextReviewTime()).isBefore(LocalDateTime.now().plusDays(4));
    }

    // ---------- 统计 / 今日复习 / 筛选 ----------

    @Test
    @DisplayName("统计概览：总数、各状态数量、本周复习次数")
    void stats_shouldAggregate() {
        // total/pending/reviewing/basic/mastered/todayPending 共 6 次 selectCount
        when(wrongQuestionMapper.selectCount(any(Wrapper.class)))
                .thenReturn(10L, 3L, 2L, 1L, 4L, 6L);
        when(wrongQuestionReviewMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        WrongQuestionStatsVO vo = service.stats(USER_ID);

        assertThat(vo.getTotal()).isEqualTo(10L);
        assertThat(vo.getPending()).isEqualTo(3L);
        assertThat(vo.getMastered()).isEqualTo(4L);
        assertThat(vo.getTodayPending()).isEqualTo(6L);
        assertThat(vo.getWeekReviewCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("今日复习：待复习或已到下次复习时间")
    void todayReview_shouldFilterPendingOrDue() {
        WrongQuestion due = owned(1L, Constants.WQ_STATUS_PENDING, 1, 0);
        due.setNextReviewTime(LocalDateTime.now());
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(due));

        List<WrongQuestion> list = service.todayReview(USER_ID);

        assertThat(list).hasSize(1);
        verify(wrongQuestionMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("删除错题时级联删除复习记录")
    void delete_shouldCascadeReviews() {
        WrongQuestion wq = owned(1L, 0, 1, 0);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        service.delete(USER_ID, 1L);

        verify(wrongQuestionMapper).deleteById(1L);
        verify(wrongQuestionReviewMapper).delete(any(Wrapper.class));
    }

    // ---------- 第二阶段：AI 智能整理 / 薄弱点报告 ----------

    @Test
    @DisplayName("应用 AI 整理：学科为待整理时自动填充，用户已填字段不覆盖")
    void applyAiAnalysis_shouldFillOnlyMissingFields() {
        WrongQuestion wq = owned(1L, 0, 1, 0);
        wq.setSubject(Constants.WQ_SUBJECT_UNSORTED);
        wq.setErrorReason("用户自填错因");
        wq.setQuestionType("用户自填题型");
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        Map<String, String> fields = Map.of(
                "questionType", "简答", // 用户已填 → 不覆盖
                "subject", "Java",
                "chapter", "并发",
                "difficulty", "难",
                "knowledgePoints", "多线程，锁",
                "errorReason", "AI推测错因", // 用户已填 → 不覆盖
                "summary", "synchronized 锁的两种粒度"
        );
        WrongQuestion result = service.applyAiAnalysis(USER_ID, 1L, fields);

        assertThat(result.getSubject()).isEqualTo("Java");
        assertThat(result.getQuestionType()).isEqualTo("用户自填题型");
        assertThat(result.getDifficulty()).isEqualTo("难");
        assertThat(result.getKnowledgePoints()).isEqualTo("多线程，锁");
        assertThat(result.getErrorReason()).isEqualTo("用户自填错因");
        assertThat(result.getAnalysis()).isEqualTo("synchronized 锁的两种粒度");
        assertThat(result.getAnalyzeStatus()).isEqualTo(2);
        verify(wrongQuestionMapper).updateById(wq);
    }

    @Test
    @DisplayName("AI 整理失败：标记 analyzeStatus=1 且不丢任何数据")
    void markAnalyzeFailed_shouldMarkStatus() {
        WrongQuestion wq = owned(1L, 0, 1, 0);
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wq);

        service.markAnalyzeFailed(USER_ID, 1L);

        assertThat(wq.getAnalyzeStatus()).isEqualTo(1);
        verify(wrongQuestionMapper).updateById(wq);
    }

    @Test
    @DisplayName("薄弱点报告：知识点聚合、错因分布、最常错题目排序")
    void weakPoints_shouldAggregate() {
        WrongQuestion w1 = owned(1L, 0, 3, 0);
        w1.setKnowledgePoints("多线程，锁");
        w1.setErrorReason("概念不清");
        WrongQuestion w2 = owned(2L, 0, 2, 0);
        w2.setKnowledgePoints("多线程");
        w2.setErrorReason("概念不清");
        WrongQuestion w3 = owned(3L, 3, 1, 3);
        w3.setKnowledgePoints("排序");
        w3.setErrorReason("公式记错");
        when(wrongQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(w1, w2, w3));

        WeakPointsVO vo = service.weakPoints(USER_ID);

        assertThat(vo.getKnowledgePoints()).hasSize(3);
        // 多线程出现 2 次且 2 道都待复习 → 排第一
        assertThat(vo.getKnowledgePoints().get(0).getName()).isEqualTo("多线程");
        assertThat(vo.getKnowledgePoints().get(0).getWrongCount()).isEqualTo(2);
        assertThat(vo.getKnowledgePoints().get(0).getPendingCount()).isEqualTo(2);
        assertThat(vo.getErrorReasons().get(0).getReason()).isEqualTo("概念不清");
        assertThat(vo.getErrorReasons().get(0).getCount()).isEqualTo(2);
        assertThat(vo.getMostWrong().get(0).getId()).isEqualTo(1L); // wrong_count=3 最前
    }

    // ---------- 第三阶段：AI 练习题 ----------

    @Test
    @DisplayName("保存练习题到错题本：继承来源错题信息，抢占式标记已保存")
    void saveGenerated_shouldConvertToWrongQuestion() {
        WrongQuestionGenerated g = new WrongQuestionGenerated();
        g.setId(11L);
        g.setUserId(USER_ID);
        g.setWrongQuestionId(1L);
        g.setQuestion("练习题题目");
        g.setAnswer("B");
        g.setAnalysis("解析");
        g.setStatus(Constants.GENERATED_STATUS_PRACTICING);
        when(wrongQuestionGeneratedMapper.selectById(11L)).thenReturn(g);
        when(wrongQuestionGeneratedMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        WrongQuestion source = owned(1L, 0, 1, 0);
        source.setSubject("Java");
        source.setTag("多线程");
        source.setQuestionType("选择");
        source.setDifficulty("中");
        when(wrongQuestionMapper.selectById(1L)).thenReturn(source);

        WrongQuestion saved = service.saveGenerated(USER_ID, 11L);

        assertThat(saved.getSubject()).isEqualTo("Java");
        assertThat(saved.getTag()).isEqualTo("多线程");
        assertThat(saved.getQuestion()).isEqualTo("练习题题目");
        assertThat(saved.getCorrectAnswer()).isEqualTo("B");
        assertThat(saved.getAnalysis()).isEqualTo("解析");
        assertThat(saved.getSource()).isEqualTo("ai");
        assertThat(saved.getStatus()).isEqualTo(Constants.WQ_STATUS_PENDING);
        verify(wrongQuestionGeneratedMapper).update(any(), any(Wrapper.class));
        verify(wrongQuestionMapper).insert(saved);
    }

    @Test
    @DisplayName("同一练习题不能重复保存到错题本（条件更新抢占失败）")
    void saveGenerated_shouldRejectDuplicate() {
        WrongQuestionGenerated g = new WrongQuestionGenerated();
        g.setId(11L);
        g.setUserId(USER_ID);
        g.setWrongQuestionId(1L);
        g.setQuestion("练习题");
        g.setStatus(Constants.GENERATED_STATUS_SAVED);
        when(wrongQuestionGeneratedMapper.selectById(11L)).thenReturn(g);
        when(wrongQuestionGeneratedMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.saveGenerated(USER_ID, 11L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已加入错题本");
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("他人练习题不能保存到自己的错题本")
    void saveGenerated_shouldRejectForeignGenerated() {
        WrongQuestionGenerated g = new WrongQuestionGenerated();
        g.setId(11L);
        g.setUserId(999L);
        g.setWrongQuestionId(1L);
        when(wrongQuestionGeneratedMapper.selectById(11L)).thenReturn(g);

        assertThatThrownBy(() -> service.saveGenerated(USER_ID, 11L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }
}
