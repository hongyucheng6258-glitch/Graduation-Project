package com.campus.platform.module.ai;

import com.campus.platform.module.ai.mapper.WrongQuestionGeneratedMapper;
import com.campus.platform.module.ai.entity.WrongQuestionGenerated;
import com.campus.platform.module.ai.service.WrongQuestionService;
import com.campus.platform.module.ai.vo.GeneratedQuestionVO;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import com.campus.platform.module.ai.mapper.AiSessionMapper;
import com.campus.platform.module.ai.service.AiChatService;
import com.campus.platform.module.ai.entity.WrongQuestion;
import com.campus.platform.module.ai.entity.PdfDocument;
import com.campus.platform.module.ai.entity.AiSession;
import com.campus.platform.module.ai.dto.PdfAskDTO;
import com.campus.platform.module.ai.dto.OutlineDTO;
import com.campus.platform.module.ai.dto.QuizDTO;

import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.ai.gateway.ChatMemoryService;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI PDF 会话绑定")
class AiChatServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long SESSION_ID = 88L;
    private static final Long DOC_ID = 99L;

    @Mock
    private AiSessionMapper aiSessionMapper;
    @Mock
    private AiMessageMapper aiMessageMapper;
    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private ChatMemoryService chatMemoryService;
    @Mock
    private WrongQuestionService wrongQuestionService;
    @Mock
    private PdfDocumentMapper pdfDocumentMapper;
    @Mock
    private WrongQuestionGeneratedMapper wrongQuestionGeneratedMapper;

    @InjectMocks
    private AiChatService service;

    @Test
    @DisplayName("已有 PDF 会话提问时应持久化最新文档ID")
    void pdfAsk_shouldPersistDocIdOnExistingSession() {
        AiSession session = session(Constants.SCENE_PDF);
        PdfDocument document = document(USER_ID);
        when(aiSessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(pdfDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_PDF), eq("问题"),
                eq(SESSION_ID), anyMap())).thenReturn("答案");

        PdfAskDTO dto = request();
        String answer = service.pdfAsk(USER_ID, dto);

        verify(aiSessionMapper).updateById(session);
        verify(aiGatewayService).chat(eq(USER_ID), eq(Constants.SCENE_PDF), eq("问题"),
                eq(SESSION_ID), anyMap());
        org.assertj.core.api.Assertions.assertThat(answer).isEqualTo("答案");
    }

    @Test
    @DisplayName("普通聊天会话不能用于 PDF 问答")
    void pdfAsk_shouldRejectNonPdfSession() {
        AiSession session = session(Constants.SCENE_CHAT);
        when(aiSessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(pdfDocumentMapper.selectById(DOC_ID)).thenReturn(document(USER_ID));

        assertThatThrownBy(() -> service.pdfAsk(USER_ID, request()))
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("不属于当前用户的 PDF 文档不能用于问答")
    void pdfAsk_shouldRejectForeignDocument() {
        when(pdfDocumentMapper.selectById(DOC_ID)).thenReturn(document(2002L));

        assertThatThrownBy(() -> service.pdfAsk(USER_ID, request()))
                .hasMessageContaining("文档");
    }

    // ---------- 智能习题（B7）：AI 兜底 ----------

    @Test
    @DisplayName("AI 未配置时生成同类题应明确提示，而非笼统失败")
    void quiz_shouldRejectWhenAiNotConfigured() {
        when(aiGatewayService.isAiConfigured()).thenReturn(false);
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setUserId(USER_ID);
        wq.setQuestion("题");
        wq.setCorrectAnswer("答");
        when(wrongQuestionService.getOwned(USER_ID, 1L)).thenReturn(wq);

        QuizDTO dto = new QuizDTO();
        dto.setWrongQuestionId(1L);

        assertThatThrownBy(() -> service.quiz(USER_ID, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("题目缺答案与解析且未强制时应提示信息不足")
    void quiz_shouldRejectInfoInsufficient() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setUserId(USER_ID);
        wq.setQuestion("只有题干没有答案");
        when(wrongQuestionService.getOwned(USER_ID, 1L)).thenReturn(wq);

        QuizDTO dto = new QuizDTO();
        dto.setWrongQuestionId(1L);

        assertThatThrownBy(() -> service.quiz(USER_ID, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("信息不足");
    }

    @Test
    @DisplayName("force=true 可跳过信息不足检查并正常调用 AI")
    void quiz_forceShouldProceed() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion wq = new WrongQuestion();
        wq.setId(1L);
        wq.setUserId(USER_ID);
        wq.setSubject("Java");
        wq.setQuestion("只有题干");
        when(wrongQuestionService.getOwned(USER_ID, 1L)).thenReturn(wq);
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_QUIZ), eq("只有题干"), isNull(), anyMap()))
                .thenReturn("新题");

        QuizDTO dto = new QuizDTO();
        dto.setWrongQuestionId(1L);
        dto.setForce(true);

        assertThat(service.quiz(USER_ID, dto)).isEqualTo("新题");
    }

    @Test
    @DisplayName("本学科错题提纲未指定主题时应填充安全默认主题")
    void outline_subjectModeWithoutTopic_shouldUseDefaultTopic() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion wrongQuestion = new WrongQuestion();
        wrongQuestion.setId(1L);
        wrongQuestion.setQuestion("二分查找的时间复杂度是什么？");
        when(wrongQuestionService.listOwnedBySubject(USER_ID, "数据结构"))
                .thenReturn(List.of(wrongQuestion));
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_OUTLINE), any(), isNull(), anyMap()))
                .thenReturn("复习提纲");
        OutlineDTO dto = new OutlineDTO();
        dto.setMode(Constants.OUTLINE_MODE_SUBJECT);
        dto.setSubject("数据结构");

        assertThat(service.outline(USER_ID, dto)).isEqualTo("复习提纲");

        org.mockito.ArgumentCaptor<Map<String, String>> params =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(aiGatewayService).chat(eq(USER_ID), eq(Constants.SCENE_OUTLINE), any(), isNull(), params.capture());
        assertThat(params.getValue()).containsEntry("topic", "本学科错题归纳");
        assertThat(params.getValue().get("question_list")).contains("二分查找");
    }

    @Test
    @DisplayName("选中错题和全部错题提纲都应填充主题参数")
    void outline_selectedAndAllModes_shouldUseSpecificTopics() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion wrongQuestion = new WrongQuestion();
        wrongQuestion.setId(1L);
        wrongQuestion.setQuestion("二分查找的时间复杂度是什么？");
        when(wrongQuestionService.listOwnedByIds(USER_ID, List.of(1L))).thenReturn(List.of(wrongQuestion));
        when(wrongQuestionService.listOwnedAll(USER_ID)).thenReturn(List.of(wrongQuestion));
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_OUTLINE), any(), isNull(), anyMap()))
                .thenReturn("复习提纲");

        OutlineDTO selected = new OutlineDTO();
        selected.setMode(Constants.OUTLINE_MODE_SELECTED);
        selected.setWrongQuestionIds(List.of(1L));
        service.outline(USER_ID, selected);
        OutlineDTO all = new OutlineDTO();
        all.setMode(Constants.OUTLINE_MODE_ALL);
        service.outline(USER_ID, all);

        org.mockito.ArgumentCaptor<Map<String, String>> params =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(aiGatewayService, org.mockito.Mockito.times(2)).chat(
                eq(USER_ID), eq(Constants.SCENE_OUTLINE), any(), isNull(), params.capture());
        assertThat(params.getAllValues()).extracting(values -> values.get("topic"))
                .containsExactly("选中错题归纳", "全部错题薄弱点报告");
    }

    // ---------- 第二阶段：AI 智能整理 / 讲解 / 复习计划 ----------

    private WrongQuestion analyzedWq() {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(7L);
        wq.setUserId(USER_ID);
        wq.setSubject("Java");
        wq.setQuestion("synchronized 锁的是什么？");
        wq.setMyAnswer("锁的是类");
        wq.setCorrectAnswer("锁的是对象");
        return wq;
    }

    @Test
    @DisplayName("AI 智能整理：成功解析 JSON 并应用（容忍 ```json 包裹）")
    void analyzeWrong_shouldParseAndApply() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion wq = analyzedWq();
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(wq);
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_WRONG_ANALYZE), any(), isNull(), anyMap()))
                .thenReturn("```json\n{\"questionType\":\"简答\",\"subject\":\"Java\",\"chapter\":\"并发\","
                        + "\"difficulty\":\"难\",\"knowledgePoints\":[\"多线程\",\"锁\"],"
                        + "\"errorReason\":\"概念不清\",\"summary\":\"synchronized 锁的粒度\"}\n```");
        when(wrongQuestionService.applyAiAnalysis(eq(USER_ID), eq(7L), anyMap()))
                .thenAnswer(inv -> inv.getArgument(2, Map.class).isEmpty() ? wq : wq);

        WrongQuestion result = service.analyzeWrong(USER_ID, 7L);

        verify(wrongQuestionService).applyAiAnalysis(eq(USER_ID), eq(7L), anyMap());
        assertThat(result).isSameAs(wq);
        verify(wrongQuestionService, never()).markAnalyzeFailed(anyLong(), anyLong());
    }

    @Test
    @DisplayName("AI 返回部分 null 字段：不会把字面 \"null\" 写入结果")
    void analyzeWrong_shouldSkipNullFields() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(analyzedWq());
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_WRONG_ANALYZE), any(), isNull(), anyMap()))
                .thenReturn("{\"questionType\":null,\"subject\":\"Java\",\"chapter\":\"\",\"knowledgePoints\":[\"锁\"],"
                        + "\"errorReason\":null,\"summary\":null}");
        when(wrongQuestionService.applyAiAnalysis(eq(USER_ID), eq(7L), anyMap())).thenReturn(analyzedWq());

        service.analyzeWrong(USER_ID, 7L);

        org.mockito.ArgumentCaptor<Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(wrongQuestionService).applyAiAnalysis(eq(USER_ID), eq(7L), captor.capture());
        Map<String, String> fields = captor.getValue();
        assertThat(fields).doesNotContainKey("questionType");
        assertThat(fields).doesNotContainKey("errorReason");
        assertThat(fields).doesNotContainValue("null");
        assertThat(fields.get("subject")).isEqualTo("Java");
        assertThat(fields.get("knowledgePoints")).isEqualTo("锁");
    }

    @Test
    @DisplayName("AI 未配置时智能整理：标记失败并明确提示，不阻塞错题")
    void analyzeWrong_shouldMarkFailedWhenNotConfigured() {
        when(aiGatewayService.isAiConfigured()).thenReturn(false);
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(analyzedWq());

        assertThatThrownBy(() -> service.analyzeWrong(USER_ID, 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未配置");
        verify(wrongQuestionService).markAnalyzeFailed(USER_ID, 7L);
    }

    @Test
    @DisplayName("AI 返回非 JSON 时：标记整理失败并提示可重试")
    void analyzeWrong_shouldMarkFailedOnUnparseable() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(analyzedWq());
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_WRONG_ANALYZE), any(), isNull(), anyMap()))
                .thenReturn("抱歉，我无法分析这道题");

        assertThatThrownBy(() -> service.analyzeWrong(USER_ID, 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("智能整理失败");
        verify(wrongQuestionService).markAnalyzeFailed(USER_ID, 7L);
    }

    @Test
    @DisplayName("AI 讲解：返回 Markdown 讲解文本")
    void explainWrong_shouldReturnExplanation() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(analyzedWq());
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_WRONG_EXPLAIN), any(), isNull(), anyMap()))
                .thenReturn("【错误分析】…");

        assertThat(service.explainWrong(USER_ID, 7L)).isEqualTo("【错误分析】…");
    }

    @Test
    @DisplayName("复习计划：基于今日待复习生成，可按学科过滤")
    void reviewPlan_shouldUseTodayPending() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion pending = new WrongQuestion();
        pending.setId(1L);
        pending.setSubject("Java");
        pending.setQuestion("锁的粒度");
        when(wrongQuestionService.todayReview(USER_ID)).thenReturn(List.of(pending));
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_REVIEW_PLAN), any(), isNull(), anyMap()))
                .thenReturn("今日复习计划…");

        assertThat(service.reviewPlan(USER_ID, "Java")).isEqualTo("今日复习计划…");
    }

    @Test
    @DisplayName("复习计划：今日无待复习错题时给出明确提示")
    void reviewPlan_shouldRejectWhenEmpty() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        when(wrongQuestionService.todayReview(USER_ID)).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.reviewPlan(USER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有待复习");
    }

    // ---------- 第三阶段：AI 练习题 ----------

    @Test
    @DisplayName("生成练习题：解析结构化 JSON 并落库（练习中，不直接入错题本）")
    void generatePractice_shouldPersistGenerated() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        WrongQuestion wq = analyzedWq();
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(wq);
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_PRACTICE), any(), isNull(), anyMap()))
                .thenReturn("{\"question\":\"synchronized 修饰实例方法锁的是什么？\","
                        + "\"options\":[\"A. 类对象\",\"B. 实例对象\"],\"answer\":\"B\",\"analysis\":\"锁的是当前实例\"}");

        GeneratedQuestionVO vo = service.generatePractice(USER_ID, 7L);

        assertThat(vo.getQuestion()).contains("synchronized");
        assertThat(vo.getOptions()).hasSize(2);
        assertThat(vo.getAnswer()).isEqualTo("B");
        assertThat(vo.getAnalysis()).isEqualTo("锁的是当前实例");
        // 落库：练习中状态
        org.mockito.ArgumentCaptor<WrongQuestionGenerated> captor =
                org.mockito.ArgumentCaptor.forClass(WrongQuestionGenerated.class);
        verify(wrongQuestionGeneratedMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Constants.GENERATED_STATUS_PRACTICING);
        assertThat(captor.getValue().getWrongQuestionId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("生成练习题：AI 返回非 JSON 时明确失败且不落库")
    void generatePractice_shouldRejectUnparseable() {
        when(aiGatewayService.isAiConfigured()).thenReturn(true);
        when(wrongQuestionService.getOwned(USER_ID, 7L)).thenReturn(analyzedWq());
        when(aiGatewayService.chat(eq(USER_ID), eq(Constants.SCENE_PRACTICE), any(), isNull(), anyMap()))
                .thenReturn("抱歉，出题失败");

        assertThatThrownBy(() -> service.generatePractice(USER_ID, 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法解析");
        verify(wrongQuestionGeneratedMapper, never()).insert(any(WrongQuestionGenerated.class));
    }

    private PdfAskDTO request() {
        PdfAskDTO dto = new PdfAskDTO();
        dto.setDocId(DOC_ID);
        dto.setSessionId(SESSION_ID);
        dto.setQuestion("问题");
        return dto;
    }

    private AiSession session(String scene) {
        AiSession session = new AiSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setScene(scene);
        return session;
    }

    private PdfDocument document(Long userId) {
        PdfDocument document = new PdfDocument();
        document.setId(DOC_ID);
        document.setUserId(userId);
        document.setTextContent("文档正文");
        document.setStatus(1);
        return document;
    }
}
