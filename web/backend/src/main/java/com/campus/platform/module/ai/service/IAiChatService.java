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
import com.campus.platform.module.ai.gateway.IAiGatewayService;
import com.campus.platform.module.ai.gateway.IChatMemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AiChatService 服务接口。
 */
public interface IAiChatService {

    AiSession createSession(Long userId, SessionCreateDTO dto);

    List<AiSession> listSessions(Long userId, String scene);

    void renameSession(Long userId, Long sessionId, String title);

    AiSession bindPdfDocument(Long userId, Long sessionId, Long docId);

    void deleteSession(Long userId, Long sessionId);

    PageResult<AiMessage> listMessages(Long userId, Long sessionId, int pageNum, int pageSize);

    SseEmitter askStream(Long userId, AiChatDTO dto);

    String askSync(Long userId, AiChatDTO dto);

    String pdfAsk(Long userId, PdfAskDTO dto);

    SseEmitter pdfAskStream(Long userId, PdfAskDTO dto);

    String codeFix(Long userId, CodeFixDTO dto);

    String outline(Long userId, OutlineDTO dto);

    String quiz(Long userId, QuizDTO dto);

    WrongQuestion analyzeWrong(Long userId, Long wrongQuestionId);

    String explainWrong(Long userId, Long wrongQuestionId);

    String reviewPlan(Long userId, String subject);

    GeneratedQuestionVO generatePractice(Long userId, Long wrongQuestionId);
}
