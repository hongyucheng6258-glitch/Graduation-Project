package com.campus.platform.module.post.controller;

import com.campus.platform.module.post.dto.PostPublishDTO;
import com.campus.platform.module.post.entity.PostComment;
import com.campus.platform.module.post.service.PostService;
import com.campus.platform.module.post.dto.CommentDTO;
import com.campus.platform.module.post.vo.CommentVO;
import com.campus.platform.module.post.vo.PostVO;
import com.campus.platform.module.post.entity.Post;

import com.campus.platform.common.BizException;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.config.SystemConfigHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;
    private final SystemConfigHolder systemConfigHolder;

    @PostMapping
    public R<Post> publish(@Valid @RequestBody PostPublishDTO dto) {
        if (!systemConfigHolder.isPostPublishEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "当前已关闭动态发布，请联系管理员");
        }
        return R.ok(postService.publish(UserContext.getUid(), dto));
    }

    @GetMapping("/list")
    public R<PageResult<PostVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return R.ok(postService.list(uid, keyword, pageNum, pageSize));
    }

    /** 动态详情（分享直达；仅返回审核通过的可见动态） */
    @GetMapping("/{id}")
    public R<PostVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return R.ok(postService.detail(uid, id));
    }

    @PostMapping("/{id}/like")
    public R<Void> like(@PathVariable Long id) {
        postService.like(UserContext.getUid(), id);
        return R.ok();
    }

    @DeleteMapping("/{id}/like")
    public R<Void> unlike(@PathVariable Long id) {
        postService.unlike(UserContext.getUid(), id);
        return R.ok();
    }

    @PostMapping("/{id}/comment")
    public R<PostComment> comment(@PathVariable Long id, @Valid @RequestBody CommentDTO dto) {
        return R.ok(postService.comment(UserContext.getUid(), id, dto));
    }

    @GetMapping("/{id}/comments")
    public R<PageResult<CommentVO>> comments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(postService.comments(id, pageNum, pageSize));
    }
}
