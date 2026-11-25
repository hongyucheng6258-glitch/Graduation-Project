package com.campus.platform.module.ai.controller;

import com.campus.platform.module.ai.service.WrongQuestionService;
import com.campus.platform.module.ai.vo.WrongQuestionStatsVO;
import com.campus.platform.module.ai.vo.GeneratedQuestionVO;
import com.campus.platform.module.ai.dto.WrongQuestionDTO;
import com.campus.platform.module.ai.dto.WrongReviewDTO;
import com.campus.platform.module.ai.service.AiChatService;
import com.campus.platform.module.ai.entity.WrongQuestion;
import com.campus.platform.module.ai.vo.WeakPointsVO;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wrong-question")
@RequiredArgsConstructor
public class WrongQuestionController {
    private final WrongQuestionService wrongQuestionService;
    private final AiChatService aiChatService;

    /** 分页列表：subject / status 筛选 + sort 排序 */
    @GetMapping("/list")
    public Result<PageResult<WrongQuestion>> list(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(wrongQuestionService.list(UserContext.getUid(), subject, status, sort, pageNum, pageSize));
    }

    /** 当前用户全部学科标签 */
    @GetMapping("/subjects")
    public Result<List<String>> subjects() {
        return Result.ok(wrongQuestionService.subjects(UserContext.getUid()));
    }

    /** 顶部数据概览 */
    @GetMapping("/stats")
    public Result<WrongQuestionStatsVO> stats() {
        return Result.ok(wrongQuestionService.stats(UserContext.getUid()));
    }

    /** 今日待复习列表 */
    @GetMapping("/today")
    public Result<List<WrongQuestion>> today() {
        return Result.ok(wrongQuestionService.todayReview(UserContext.getUid()));
    }

    /** 提交复习反馈（作答 + 掌握程度 → 更新状态与下次复习时间） */
    @PostMapping("/review")
    public Result<WrongQuestion> review(@Valid @RequestBody WrongReviewDTO dto) {
        return Result.ok(wrongQuestionService.reviewFeedback(UserContext.getUid(), dto));
    }

    /** 薄弱知识点报告（统计型） */
    @GetMapping("/weak-points")
    public Result<WeakPointsVO> weakPoints() {
        return Result.ok(wrongQuestionService.weakPoints(UserContext.getUid()));
    }

    /** AI 智能整理（识别题型/学科/章节/难度/知识点/错因 + 摘要） */
    @PostMapping("/{id}/analyze")
    public Result<WrongQuestion> analyze(@PathVariable Long id) {
        return Result.ok(aiChatService.analyzeWrong(UserContext.getUid(), id));
    }

    /** AI 讲解这道题（错因分析 + 知识点讲解） */
    @PostMapping("/{id}/explain")
    public Result<String> explain(@PathVariable Long id) {
        return Result.ok(aiChatService.explainWrong(UserContext.getUid(), id));
    }

    /** AI 生成今日复习计划（可带 subject 筛选） */
    @PostMapping("/review-plan")
    public Result<String> reviewPlan(@RequestBody(required = false) Map<String, String> body) {
        String subject = body == null ? null : body.get("subject");
        return Result.ok(aiChatService.reviewPlan(UserContext.getUid(), subject));
    }

    /** AI 生成同类练习题（结构化，落库练习中，不直接入错题本） */
    @PostMapping("/{id}/practice")
    public Result<GeneratedQuestionVO> practice(@PathVariable Long id) {
        return Result.ok(aiChatService.generatePractice(UserContext.getUid(), id));
    }

    /** 保存练习题到错题本（幂等） */
    @PostMapping("/practice/{id}/save")
    public Result<WrongQuestion> savePractice(@PathVariable Long id) {
        return Result.ok(wrongQuestionService.saveGenerated(UserContext.getUid(), id));
    }

    @GetMapping("/{id}")
    public Result<WrongQuestion> get(@PathVariable Long id) {
        return Result.ok(wrongQuestionService.getOwned(UserContext.getUid(), id));
    }

    /** 快速收录：仅题目必填，学科可选（空 = 待整理） */
    @PostMapping
    public Result<WrongQuestion> create(@Valid @RequestBody WrongQuestionDTO dto) {
        String source = dto.getSource() == null || dto.getSource().isBlank() ? "manual" : dto.getSource();
        return Result.ok(wrongQuestionService.create(UserContext.getUid(), dto, source));
    }

    @PutMapping("/{id}")
    public Result<WrongQuestion> update(@PathVariable Long id, @Valid @RequestBody WrongQuestionDTO dto) {
        return Result.ok(wrongQuestionService.update(UserContext.getUid(), id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wrongQuestionService.delete(UserContext.getUid(), id);
        return Result.ok();
    }
}
