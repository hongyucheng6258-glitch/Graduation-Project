package com.campus.platform.module.qa.controller;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.qa.dto.AnswerDTO;
import com.campus.platform.module.qa.dto.QuestionDTO;
import com.campus.platform.module.qa.entity.CampusAnswer;
import com.campus.platform.module.qa.entity.CampusQuestion;
import com.campus.platform.module.qa.service.IQuestionService;
import com.campus.platform.module.qa.vo.QuestionVO;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 校园互助问答 */
@RestController
@RequestMapping("/api/qa")
public class QuestionController {

    @Resource
    private IQuestionService questionService;

    /** 提问 */
    @PostMapping
    public Result<CampusQuestion> publish(@Valid @RequestBody QuestionDTO dto) {
        return Result.ok(questionService.publish(UserContext.getUid(), dto));
    }

    /** 问题列表（公开） */
    @GetMapping("/list")
    public Result<PageResult<QuestionVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(questionService.list(category, keyword, status, pageNum, pageSize));
    }

    /** 我的提问 */
    @GetMapping("/my")
    public Result<PageResult<QuestionVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(questionService.myList(UserContext.getUid(), pageNum, pageSize));
    }

    /** 详情（含回答列表；第8项修复：回答分页） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        int capped = Math.min(pageSize == null ? 10 : Math.max(1, pageSize), 100);
        return Result.ok(questionService.detail(id, uid, Math.max(1, pageNum == null ? 1 : pageNum), capped));
    }

    /** 回答 */
    @PostMapping("/{id}/answer")
    public Result<CampusAnswer> answer(@PathVariable Long id, @Valid @RequestBody AnswerDTO dto) {
        return Result.ok(questionService.answer(UserContext.getUid(), id, dto));
    }

    /** 采纳最佳回答 */
    @PutMapping("/answer/{answerId}/accept")
    public Result<Void> accept(@PathVariable Long answerId) {
        questionService.accept(UserContext.getUid(), answerId);
        return Result.ok();
    }

    /** AI 参考回答 */
    @PostMapping("/{id}/ai-answer")
    public Result<String> aiAnswer(@PathVariable Long id) {
        return Result.ok(questionService.aiAnswer(UserContext.getUid(), id));
    }
}
