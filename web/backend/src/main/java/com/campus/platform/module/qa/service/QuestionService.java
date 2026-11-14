package com.campus.platform.module.qa.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.qa.dto.AnswerDTO;
import com.campus.platform.module.qa.dto.QuestionDTO;
import com.campus.platform.module.qa.entity.CampusAnswer;
import com.campus.platform.module.qa.entity.CampusQuestion;
import com.campus.platform.module.qa.mapper.CampusAnswerMapper;
import com.campus.platform.module.qa.mapper.CampusQuestionMapper;
import com.campus.platform.module.qa.vo.AnswerVO;
import com.campus.platform.module.qa.vo.QuestionVO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.*;

/** 校园互助问答服务：提问→回答→采纳最佳回答 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final CampusQuestionMapper questionMapper;
    private final CampusAnswerMapper answerMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final AiGatewayService aiGatewayService;

    private static final List<String> CATEGORIES = List.of("课程", "考试", "技术", "生活", "其他");

    /** 提问 */
    public CampusQuestion publish(Long userId, QuestionDTO dto) {
        CampusQuestion q = new CampusQuestion();
        q.setUserId(userId);
        q.setTitle(dto.getTitle().trim());
        q.setContent(StrUtil.trimToEmpty(dto.getContent()));
        String cat = StrUtil.blankToDefault(dto.getCategory(), "其他");
        q.setCategory(CATEGORIES.contains(cat) ? cat : "其他");
        q.setStatus(0);
        q.setViewCount(0);
        questionMapper.insert(q);
        return q;
    }

    /** 问题列表（公开，不含已下架） */
    public PageResult<QuestionVO> list(String category, String keyword, Integer status, int pageNum, int pageSize) {
        Page<CampusQuestion> page = questionMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CampusQuestion>()
                        .eq(StrUtil.isNotBlank(category), CampusQuestion::getCategory, category)
                        .eq(status != null, CampusQuestion::getStatus, status)
                        .ne(CampusQuestion::getStatus, Constants.QA_OFF)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(CampusQuestion::getTitle, keyword)
                                .or().like(CampusQuestion::getContent, keyword))
                        .orderByDesc(CampusQuestion::getId));
        return toQuestionPage(page, null);
    }

    /** 详情 + 回答列表（公开，浏览计数；已下架不可见；第8项修复：回答分页返回） */
    public Map<String, Object> detail(Long id, Long currentUid, int pageNum, int pageSize) {
        CampusQuestion q = questionMapper.selectById(id);
        if (q == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在");
        }
        if (q.getStatus() != null && q.getStatus() == Constants.QA_OFF) {
            throw new BizException(ResultCode.NOT_FOUND, "该问题已下架");
        }
        // 浏览计数
        q.setViewCount(q.getViewCount() == null ? 1 : q.getViewCount() + 1);
        questionMapper.update(null, new LambdaUpdateWrapper<CampusQuestion>()
                .eq(CampusQuestion::getId, id).setSql("view_count=COALESCE(view_count,0)+1"));
        QuestionVO qvo = toVO(q, currentUid);
        qvo.setViewCount(q.getViewCount());

        Page<CampusAnswer> answerPage = answerMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CampusAnswer>()
                        .eq(CampusAnswer::getQuestionId, id)
                        .orderByDesc(CampusAnswer::getIsAccepted)
                        .orderByAsc(CampusAnswer::getId));
        List<CampusAnswer> answers = answerPage.getRecords();
        Map<Long, User> authors = userMapper.findPublisherProfiles(answers.stream().map(CampusAnswer::getUserId).distinct().toList());
        PageResult<AnswerVO> answerPageResult = PageResult.of(answerPage,
                answer -> toAnswerView(answer, authors.get(answer.getUserId())));
        qvo.setAnswerCount(answerPage.getTotal());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", qvo);
        result.put("answers", answerPageResult);
        return result;
    }

    /** 我的提问 */
    public PageResult<QuestionVO> myList(Long userId, int pageNum, int pageSize) {
        Page<CampusQuestion> page = questionMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CampusQuestion>()
                        .eq(CampusQuestion::getUserId, userId)
                        .orderByDesc(CampusQuestion::getId));
        return toQuestionPage(page, userId);
    }

    /** 回答 */
    public CampusAnswer answer(Long userId, Long questionId, AnswerDTO dto) {
        CampusQuestion q = questionMapper.selectById(questionId);
        if (q == null || q.getStatus() == Constants.QA_OFF) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在或已下架");
        }
        if (q.getUserId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能回答自己提的问题");
        }
        CampusAnswer a = new CampusAnswer();
        a.setQuestionId(questionId);
        a.setUserId(userId);
        a.setContent(dto.getContent().trim());
        a.setIsAccepted(0);
        answerMapper.insert(a);
        messageService.send(q.getUserId(), Constants.MSG_INTERACT, "你的问题有新回答",
                String.format("有人回答了你的问题「%s」，快去看看吧。", q.getTitle()),
                "qa", questionId);
        return a;
    }

    /** 采纳最佳回答（仅提问者；采纳后问题标记已解决） */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void accept(Long userId, Long answerId) {
        CampusAnswer a = answerMapper.selectById(answerId);
        if (a == null) {
            throw new BizException(ResultCode.NOT_FOUND, "回答不存在");
        }
        CampusQuestion q = questionMapper.lockById(a.getQuestionId());
        if (q == null || !q.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有提问者可以采纳回答");
        }
        if (q.getStatus() == Constants.QA_OFF) {
            throw new BizException(ResultCode.BAD_REQUEST, "问题已下架");
        }
        if (Objects.equals(q.getAcceptedAnswerId(), answerId)) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该回答已被采纳");
        }
        answerMapper.update(null, new LambdaUpdateWrapper<CampusAnswer>()
                .eq(CampusAnswer::getQuestionId, q.getId())
                .eq(CampusAnswer::getIsAccepted, 1)
                .set(CampusAnswer::getIsAccepted, 0));
        a.setIsAccepted(1);
        answerMapper.updateById(a);
        q.setAcceptedAnswerId(answerId);
        q.setStatus(1);
        questionMapper.updateById(q);
        messageService.send(a.getUserId(), Constants.MSG_INTERACT, "你的回答被采纳了",
                String.format("你在「%s」下的回答被提问者采纳为最佳回答，感谢你的帮助！", q.getTitle()),
                "qa", q.getId());
    }

    /** AI 参考回答（使用学生额度） */
    public String aiAnswer(Long userId, Long questionId) {
        CampusQuestion q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在");
        }
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        Map<String, String> params = new HashMap<>();
        params.put("q_title", q.getTitle());
        params.put("q_content", StrUtil.blankToDefault(q.getContent(), ""));
        return aiGatewayService.chat(userId, Constants.SCENE_QA_ANSWER,
                "请参考回答这个校园互助问题", null, params).trim();
    }

    private PageResult<QuestionVO> toQuestionPage(Page<CampusQuestion> page, Long currentUid) {
        List<Long> questionIds = page.getRecords().stream().map(CampusQuestion::getId).toList();
        Map<Long, Long> answerCounts = countAnswers(questionIds);
        return PageResult.of(page, question -> {
            QuestionVO questionView = toVO(question, currentUid);
            questionView.setAnswerCount(answerCounts.getOrDefault(question.getId(), 0L));
            return questionView;
        });
    }

    private AnswerVO toAnswerView(CampusAnswer answer, User author) {
        AnswerVO answerView = new AnswerVO();
        answerView.setId(answer.getId());
        answerView.setQuestionId(answer.getQuestionId());
        answerView.setUserId(answer.getUserId());
        answerView.setContent(answer.getContent());
        answerView.setIsAccepted(answer.getIsAccepted());
        answerView.setCreateTime(answer.getCreateTime());
        answerView.setAnswererNickname(author == null ? "" : author.getNickname());
        answerView.setAnswererAvatar(author == null ? null : author.getAvatar());
        return answerView;
    }

    private Map<Long, Long> countAnswers(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (var row : answerMapper.countByQuestionIds(questionIds)) {
            counts.put(((Number) row.get("question_id")).longValue(), ((Number) row.get("answer_count")).longValue());
        }
        return counts;
    }

    private QuestionVO toVO(CampusQuestion q, Long currentUid) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setUserId(q.getUserId());
        vo.setTitle(q.getTitle());
        vo.setContent(q.getContent());
        vo.setCategory(q.getCategory());
        vo.setStatus(q.getStatus());
        vo.setAcceptedAnswerId(q.getAcceptedAnswerId());
        vo.setViewCount(q.getViewCount());
        vo.setCreateTime(q.getCreateTime());
        User u = userMapper.selectById(q.getUserId());
        vo.setPublisherNickname(u == null ? "" : u.getNickname());
        vo.setPublisherAvatar(u == null ? null : u.getAvatar());
        vo.setIsOwner(currentUid != null && currentUid.equals(q.getUserId()));
        return vo;
    }
}
