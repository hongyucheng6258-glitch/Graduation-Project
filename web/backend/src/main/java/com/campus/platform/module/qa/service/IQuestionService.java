package com.campus.platform.module.qa.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.qa.dto.AnswerDTO;
import com.campus.platform.module.qa.dto.QuestionDTO;
import com.campus.platform.module.qa.entity.CampusAnswer;
import com.campus.platform.module.qa.entity.CampusQuestion;
import com.campus.platform.module.qa.vo.QuestionVO;

import java.util.Map;

/** 校园互助问答服务接口：提问→回答→采纳最佳回答 */
public interface IQuestionService {

    /** 提问 */
    CampusQuestion publish(Long userId, QuestionDTO dto);

    /** 问题列表（公开，不含已下架） */
    PageResult<QuestionVO> list(String category, String keyword, Integer status, int pageNum, int pageSize);

    /** 详情 + 回答列表（公开，浏览计数） */
    Map<String, Object> detail(Long id, Long currentUid, int pageNum, int pageSize);

    /** 我的提问 */
    PageResult<QuestionVO> myList(Long userId, int pageNum, int pageSize);

    /** 回答 */
    CampusAnswer answer(Long userId, Long questionId, AnswerDTO dto);

    /** 采纳最佳回答（仅提问者；采纳后问题标记已解决） */
    void accept(Long userId, Long answerId);

    /** AI 参考回答（使用学生额度） */
    String aiAnswer(Long userId, Long questionId);
}
