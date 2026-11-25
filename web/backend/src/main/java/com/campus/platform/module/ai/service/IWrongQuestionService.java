package com.campus.platform.module.ai.service;

import com.campus.platform.common.mapper.MapperQueries;
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
 * WrongQuestionService 服务接口。
 */
public interface IWrongQuestionService {

    PageResult<WrongQuestion> list(Long userId, String subject, Integer status, String sort, int pageNum, int pageSize);

    WrongQuestion getOwned(Long userId, Long id);

    List<WrongQuestion> listOwnedByIds(Long userId, List<Long> ids);

    List<WrongQuestion> listOwnedAll(Long userId);

    List<WrongQuestion> listOwnedBySubject(Long userId, String subject);

    WrongQuestion create(Long userId, WrongQuestionDTO dto, String source);

    WrongQuestion update(Long userId, Long id, WrongQuestionDTO dto);

    void delete(Long userId, Long id);

    List<String> subjects(Long userId);

    WrongQuestionStatsVO stats(Long userId);

    List<WrongQuestion> todayReview(Long userId);

    WrongQuestion reviewFeedback(Long userId, WrongReviewDTO dto);

    WrongQuestion applyAiAnalysis(Long userId, Long id, Map<String, String> fields);

    void markAnalyzeFailed(Long userId, Long id);

    WeakPointsVO weakPoints(Long userId);

    WrongQuestion saveGenerated(Long userId, Long generatedId);
}
