package com.campus.platform.module.ai.service;

import com.campus.platform.module.ai.mapper.WrongQuestionGeneratedMapper;
import com.campus.platform.module.ai.entity.WrongQuestionGenerated;
import com.campus.platform.module.ai.vo.GeneratedQuestionVO;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.dto.SessionCreateDTO;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import com.campus.platform.module.ai.mapper.AiSessionMapper;
import com.campus.platform.module.ai.entity.WrongQuestion;
import com.campus.platform.module.ai.entity.PdfDocument;
import com.campus.platform.module.ai.dto.CodeFixDTO;
import com.campus.platform.module.ai.dto.OutlineDTO;
import com.campus.platform.module.ai.dto.AiChatDTO;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.entity.AiSession;
import com.campus.platform.module.ai.dto.PdfAskDTO;
import com.campus.platform.module.ai.dto.QuizDTO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.ai.gateway.ChatMemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 会话/问答业务编排：会话 CRUD + 各场景问答出口。
 * 模型调用一律经 {@link AiGatewayService}（共享约定 #10）。
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiGatewayService aiGatewayService;
    private final ChatMemoryService chatMemoryService;
    private final WrongQuestionService wrongQuestionService;
    private final PdfDocumentMapper pdfDocumentMapper;
    private final WrongQuestionGeneratedMapper wrongQuestionGeneratedMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 新建会话 */
    public AiSession createSession(Long userId, SessionCreateDTO dto) {
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setScene(dto.getScene());
        session.setTitle(dto.getTitle() == null || dto.getTitle().isBlank() ? "新会话" : dto.getTitle());
        session.setDocId(dto.getDocId());
        aiSessionMapper.insert(session);
        return session;
    }

    /** 会话列表（按场景筛选） */
    public java.util.List<AiSession> listSessions(Long userId, String scene) {
        return aiSessionMapper.selectList(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .eq(scene != null && !scene.isBlank(), AiSession::getScene, scene)
                .orderByDesc(AiSession::getUpdateTime));
    }

    /** 重命名会话（校验归属） */
    public void renameSession(Long userId, Long sessionId, String title) {
        AiSession session = checkOwner(userId, sessionId);
        session.setTitle(title);
        aiSessionMapper.updateById(session);
    }

    /** 绑定 PDF 文档到已有 PDF 会话。 */
    public AiSession bindPdfDocument(Long userId, Long sessionId, Long docId) {
        if (docId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "文档ID不能为空");
        }
        PdfDocument document = pdfDocumentMapper.selectById(docId);
        if (document == null || !userId.equals(document.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "文档不存在");
        }
        AiSession session = checkOwner(userId, sessionId);
        if (!Constants.SCENE_PDF.equals(session.getScene())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前会话不是PDF会话");
        }
        session.setDocId(docId);
        aiSessionMapper.updateById(session);
        return session;
    }

    /** 删除会话（含消息） */
    public void deleteSession(Long userId, Long sessionId) {
        checkOwner(userId, sessionId);
        chatMemoryService.deleteBySession(sessionId);
        aiSessionMapper.deleteById(sessionId);
    }

    /** 历史消息（分页，倒序供上拉加载） */
    public PageResult<AiMessage> listMessages(Long userId, Long sessionId, int pageNum, int pageSize) {
        checkOwner(userId, sessionId);
        Page<AiMessage> page = aiMessageMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, sessionId)
                        .orderByDesc(AiMessage::getId));
        return PageResult.of(page);
    }

    /** Web SSE 流式答疑 */
    public SseEmitter askStream(Long userId, AiChatDTO dto) {
        Long sessionId = ensureSession(userId, dto.getSessionId(), Constants.SCENE_CHAT);
        return aiGatewayService.streamChat(userId, Constants.SCENE_CHAT,
                dto.getQuestion(), sessionId, Map.of());
    }

    /** 小程序一次性答疑 */
    public String askSync(Long userId, AiChatDTO dto) {
        Long sessionId = ensureSession(userId, dto.getSessionId(), Constants.SCENE_CHAT);
        return aiGatewayService.chat(userId, Constants.SCENE_CHAT,
                dto.getQuestion(), sessionId, Map.of());
    }

    /** PDF 问答（docId 注入 params，由网关截取文档片段） */
    public String pdfAsk(Long userId, PdfAskDTO dto) {
        Long sessionId = preparePdfSession(userId, dto);
        Map<String, String> params = Map.of("docId", String.valueOf(dto.getDocId()));
        return aiGatewayService.chat(userId, Constants.SCENE_PDF,
                dto.getQuestion(), sessionId, params);
    }

    /** PDF 问答 SSE 版（Web 端） */
    public SseEmitter pdfAskStream(Long userId, PdfAskDTO dto) {
        Long sessionId = preparePdfSession(userId, dto);
        Map<String, String> params = Map.of("docId", String.valueOf(dto.getDocId()));
        return aiGatewayService.streamChat(userId, Constants.SCENE_PDF,
                dto.getQuestion(), sessionId, params);
    }

    /** 校验 PDF 文档归属，并将文档绑定到当前 PDF 会话。 */
    private Long preparePdfSession(Long userId, PdfAskDTO dto) {
        PdfDocument document = pdfDocumentMapper.selectById(dto.getDocId());
        if (document == null || !userId.equals(document.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "文档不存在");
        }
        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            SessionCreateDTO create = new SessionCreateDTO();
            create.setScene(Constants.SCENE_PDF);
            create.setDocId(dto.getDocId());
            return createSession(userId, create).getId();
        }
        AiSession session = checkOwner(userId, sessionId);
        if (!Constants.SCENE_PDF.equals(session.getScene())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前会话不是PDF会话");
        }
        if (!dto.getDocId().equals(session.getDocId())) {
            session.setDocId(dto.getDocId());
            aiSessionMapper.updateById(session);
        }
        return sessionId;
    }

    /** 代码纠错（B4，无状态单轮） */
    public String codeFix(Long userId, CodeFixDTO dto) {
        Map<String, String> params = new HashMap<>();
        params.put("code", dto.getCode());
        params.put("language", dto.getLanguage());
        // question 供模板兜底与敏感词检查
        String question = dto.getExtra() == null || dto.getExtra().isBlank()
                ? "请检查以下代码" : dto.getExtra();
        return aiGatewayService.chat(userId, Constants.SCENE_CODE_FIX, question, null, params);
    }

    /**
     * 复习提纲生成（B5，v2：三种生成方式，无需手动填学科/章节/主题）。
     * <ul>
     *   <li>mode=subject：按学科错题生成（topic 可空，空则按该学科错题归纳）</li>
     *   <li>mode=selected：按勾选的错题生成</li>
     *   <li>mode=all：按全部错题生成薄弱点报告</li>
     * </ul>
     */
    public String outline(Long userId, OutlineDTO dto) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        String mode = dto.getMode() == null ? Constants.OUTLINE_MODE_SUBJECT : dto.getMode();
        Map<String, String> params = new HashMap<>();
        switch (mode) {
            case Constants.OUTLINE_MODE_SELECTED -> {
                java.util.List<WrongQuestion> list =
                        wrongQuestionService.listOwnedByIds(userId, dto.getWrongQuestionIds());
                if (list.isEmpty()) {
                    throw new BizException(ResultCode.BAD_REQUEST, "请先勾选要生成提纲的错题");
                }
                params.put("subject", "选中错题(" + list.size() + "道)");
                params.put("topic", "选中错题归纳");
                params.put("question_list", buildOutlineContext(list));
                return aiGatewayService.chat(userId, Constants.SCENE_OUTLINE,
                        "请根据以下选中的错题归纳高频知识点、容易混淆的概念、需要优先复习的内容、推荐复习顺序与自测问题。", null, params);
            }
            case Constants.OUTLINE_MODE_ALL -> {
                java.util.List<WrongQuestion> list = wrongQuestionService.listOwnedAll(userId);
                if (list.isEmpty()) {
                    throw new BizException(ResultCode.BAD_REQUEST, "错题本为空，请先收录错题");
                }
                params.put("subject", "全部错题(" + list.size() + "道)");
                params.put("topic", "全部错题薄弱点报告");
                params.put("question_list", buildOutlineContext(list));
                return aiGatewayService.chat(userId, Constants.SCENE_OUTLINE,
                        "请根据以下全部错题生成学习薄弱点报告：高频知识点、容易混淆的概念、需要优先复习的内容与自测问题。", null, params);
            }
            default -> { // subject 模式（兼容旧端 subject+chapter+topic）
                if (StrUtil.isBlank(dto.getSubject())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "请选择学科后再生成");
                }
                String subjectWithChapter = dto.getSubject()
                        + (StrUtil.isBlank(dto.getChapter()) ? "" : " " + dto.getChapter());
                params.put("subject", subjectWithChapter);
                String topic = StrUtil.isBlank(dto.getTopic())
                        ? "本学科错题归纳"
                        : dto.getTopic().trim();
                params.put("topic", topic);
                if (StrUtil.isBlank(dto.getTopic())) {
                    // 未指定主题：基于该学科错题生成
                    java.util.List<WrongQuestion> list =
                            wrongQuestionService.listOwnedBySubject(userId, dto.getSubject());
                    if (list.isEmpty()) {
                        throw new BizException(ResultCode.BAD_REQUEST, "该学科暂无错题，无法生成复习提纲");
                    }
                    params.put("question_list", buildOutlineContext(list));
                    return aiGatewayService.chat(userId, Constants.SCENE_OUTLINE,
                            "请根据「" + dto.getSubject() + "」的错题生成复习提纲：高频知识点、容易混淆的概念、需要优先复习的内容、推荐复习顺序与自测问题。",
                            null, params);
                }
                return aiGatewayService.chat(userId, Constants.SCENE_OUTLINE,
                        "生成「" + dto.getTopic() + "」复习提纲", null, params);
            }
        }
    }

    /** 组装错题列表上下文（题目 + 标签/错因/知识点） */
    private String buildOutlineContext(java.util.List<WrongQuestion> list) {
        StringBuilder sb = new StringBuilder();
        for (WrongQuestion wq : list) {
            sb.append("- ").append(wq.getQuestion());
            if (StrUtil.isNotBlank(wq.getTag())) {
                sb.append(" 【标签:").append(wq.getTag()).append("】");
            }
            if (StrUtil.isNotBlank(wq.getErrorReason())) {
                sb.append(" 【错因:").append(wq.getErrorReason()).append("】");
            }
            if (StrUtil.isNotBlank(wq.getKnowledgePoints())) {
                sb.append(" 【知识点:").append(wq.getKnowledgePoints()).append("】");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 智能习题：基于错题生成同类题（B7） */
    public String quiz(Long userId, QuizDTO dto) {
        WrongQuestion wq = wrongQuestionService.getOwned(userId, dto.getWrongQuestionId());
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        // 题目缺少答案与解析时，AI 生成的同类题质量较低：默认提示先补充，force=true 可强制生成
        if (StrUtil.isBlank(wq.getCorrectAnswer()) && StrUtil.isBlank(wq.getAnalysis())
                && !Boolean.TRUE.equals(dto.getForce())) {
            throw new BizException(ResultCode.AI_INFO_INSUFFICIENT,
                    "题目信息不足：缺少答案和解析，AI 生成的同类题质量可能较低，建议先补充");
        }
        Map<String, String> params = new HashMap<>();
        params.put("subject", wq.getSubject());
        params.put("question", wq.getQuestion());
        params.put("answer", wq.getCorrectAnswer() == null ? "（未提供）" : wq.getCorrectAnswer());
        return aiGatewayService.chat(userId, Constants.SCENE_QUIZ,
                wq.getQuestion(), null, params);
    }

    /**
     * 错题智能整理（第二阶段）：AI 识别题型/学科/章节/难度/知识点/错因并生成摘要。
     * AI 失败时错题仍正常保存，仅标记 analyzeStatus=1，前端提示「暂未完成智能整理，可稍后重试」。
     */
    public WrongQuestion analyzeWrong(Long userId, Long wrongQuestionId) {
        WrongQuestion wq = wrongQuestionService.getOwned(userId, wrongQuestionId);
        if (!aiGatewayService.isAiConfigured()) {
            wrongQuestionService.markAnalyzeFailed(userId, wrongQuestionId);
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），暂未完成智能整理，可稍后重试");
        }
        try {
            String raw = aiGatewayService.chat(userId, Constants.SCENE_WRONG_ANALYZE,
                    "请分析这道错题并输出结构化结果", null, buildWrongParams(wq));
            Map<String, String> fields = parseAnalyzeJson(raw);
            if (fields.isEmpty()) {
                throw new BizException(ResultCode.AI_INVOKE_FAIL, "智能整理失败：AI 返回内容无法解析，可稍后重试");
            }
            return wrongQuestionService.applyAiAnalysis(userId, wrongQuestionId, fields);
        } catch (BizException e) {
            wrongQuestionService.markAnalyzeFailed(userId, wrongQuestionId);
            throw e;
        } catch (Exception e) {
            wrongQuestionService.markAnalyzeFailed(userId, wrongQuestionId);
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "智能整理失败，可稍后重试");
        }
    }

    /** 错题讲解/错因分析（第二阶段）：返回 Markdown 讲解文本 */
    public String explainWrong(Long userId, Long wrongQuestionId) {
        WrongQuestion wq = wrongQuestionService.getOwned(userId, wrongQuestionId);
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        return aiGatewayService.chat(userId, Constants.SCENE_WRONG_EXPLAIN,
                "请讲解这道错题", null, buildWrongParams(wq));
    }

    /** 复习计划（第二阶段）：基于今日待复习错题生成个性化复习计划 */
    public String reviewPlan(Long userId, String subject) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        List<WrongQuestion> pending = wrongQuestionService.todayReview(userId);
        if (StrUtil.isNotBlank(subject)) {
            pending = pending.stream().filter(wq -> subject.equals(wq.getSubject())).toList();
        }
        if (pending.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    StrUtil.isBlank(subject) ? "今日没有待复习的错题，无需生成计划" : "该学科今日没有待复习的错题");
        }
        Map<String, String> params = new HashMap<>();
        params.put("subject", StrUtil.isBlank(subject) ? "全部学科" : subject);
        params.put("question_list", buildOutlineContext(pending));
        return aiGatewayService.chat(userId, Constants.SCENE_REVIEW_PLAN,
                "请生成今日复习计划", null, params);
    }

    /** 组装错题上下文参数（analyze/explain 共用） */
    private Map<String, String> buildWrongParams(WrongQuestion wq) {
        Map<String, String> params = new HashMap<>();
        params.put("subject", StrUtil.isBlank(wq.getSubject()) ? "（未填写）" : wq.getSubject());
        params.put("question_text", wq.getQuestion());
        params.put("my_answer", StrUtil.isBlank(wq.getMyAnswer()) ? "（未填写）" : wq.getMyAnswer());
        params.put("correct_answer", StrUtil.isBlank(wq.getCorrectAnswer()) ? "（未填写）" : wq.getCorrectAnswer());
        params.put("analysis", StrUtil.isBlank(wq.getAnalysis()) ? "（未填写）" : wq.getAnalysis());
        params.put("error_reason", StrUtil.isBlank(wq.getErrorReason()) ? "（未填写）" : wq.getErrorReason());
        return params;
    }

    /**
     * 生成同类练习题（第三阶段）：AI 输出结构化 JSON → 落库 wrong_question_generated（练习中），
     * 不直接入错题本；用户确认后由 saveGenerated 转正式错题。
     */
    public GeneratedQuestionVO generatePractice(Long userId, Long wrongQuestionId) {
        WrongQuestion wq = wrongQuestionService.getOwned(userId, wrongQuestionId);
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        String raw = aiGatewayService.chat(userId, Constants.SCENE_PRACTICE,
                "请生成一道同类练习题", null, buildWrongParams(wq));
        JsonNode node;
        try {
            node = extractJsonObject(raw);
        } catch (Exception e) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "智能出题失败：AI 返回内容无法解析，可重新生成");
        }
        String question = node.path("question").asText("");
        if (StrUtil.isBlank(question)) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "智能出题失败：AI 返回内容无法解析，可重新生成");
        }
        List<String> options = new ArrayList<>();
        JsonNode opt = node.get("options");
        if (opt != null && opt.isArray()) {
            opt.forEach(n -> {
                String t = n.asText().trim();
                if (!t.isEmpty()) {
                    options.add(t);
                }
            });
        }
        String answer = node.path("answer").asText("");
        String analysis = node.path("analysis").asText("");

        WrongQuestionGenerated g = new WrongQuestionGenerated();
        g.setUserId(userId);
        g.setWrongQuestionId(wrongQuestionId);
        g.setQuestion(question);
        g.setOptions(writeOptionsJson(options));
        g.setAnswer(StrUtil.isBlank(answer) ? null : answer);
        g.setAnalysis(StrUtil.isBlank(analysis) ? null : analysis);
        g.setStatus(Constants.GENERATED_STATUS_PRACTICING);
        wrongQuestionGeneratedMapper.insert(g);

        GeneratedQuestionVO vo = new GeneratedQuestionVO();
        vo.setId(g.getId());
        vo.setWrongQuestionId(wrongQuestionId);
        vo.setQuestion(question);
        vo.setOptions(options);
        vo.setAnswer(answer);
        vo.setAnalysis(analysis);
        return vo;
    }

    private String writeOptionsJson(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 从 AI 返回文本中提取 JSON 对象（容忍 ```json 包裹与前后杂文本） */
    private JsonNode extractJsonObject(String raw) throws Exception {
        String json = raw;
        int s = json.indexOf('{');
        int e = json.lastIndexOf('}');
        if (s >= 0 && e > s) {
            json = json.substring(s, e + 1);
        }
        return objectMapper.readTree(json);
    }

    /** 解析 AI 整理的 JSON（容忍 ```json 包裹与前后杂文本，缺字段不报错） */
    private Map<String, String> parseAnalyzeJson(String raw) {
        Map<String, String> fields = new HashMap<>();
        if (StrUtil.isBlank(raw)) {
            return fields;
        }
        try {
            JsonNode node = extractJsonObject(raw);
            putText(fields, node, "questionType");
            putText(fields, node, "subject");
            putText(fields, node, "chapter");
            putText(fields, node, "difficulty");
            putText(fields, node, "errorReason");
            putText(fields, node, "summary");
            JsonNode kp = node.get("knowledgePoints");
            if (kp != null && kp.isArray()) {
                List<String> list = new ArrayList<>();
                kp.forEach(n -> {
                    String t = n.asText().trim();
                    if (!t.isEmpty()) {
                        list.add(t);
                    }
                });
                if (!list.isEmpty()) {
                    fields.put("knowledgePoints", String.join("，", list));
                }
            }
        } catch (Exception e) {
            // 解析失败 → 空 map，调用方按「无法解析」处理
        }
        return fields;
    }

    private void putText(Map<String, String> fields, JsonNode node, String key) {
        JsonNode v = node.get(key);
        // 排除 null 节点：NullNode.asText() 会返回字面 "null"
        if (v != null && v.isValueNode() && !v.isNull() && !v.asText().isBlank()) {
            fields.put(key, v.asText().trim());
        }
    }

    /** 若无会话则自动创建（chat 场景） */
    private Long ensureSession(Long userId, Long sessionId, String scene) {
        if (sessionId != null) {
            checkOwner(userId, sessionId);
            return sessionId;
        }
        SessionCreateDTO create = new SessionCreateDTO();
        create.setScene(scene);
        return createSession(userId, create).getId();
    }

    /** 校验会话归属当前用户 */
    private AiSession checkOwner(Long userId, Long sessionId) {
        AiSession session = aiSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        return session;
    }
}
