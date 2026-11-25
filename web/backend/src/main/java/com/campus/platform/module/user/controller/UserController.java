package com.campus.platform.module.user.controller;

import com.campus.platform.module.user.dto.PasswordUpdateDTO;
import com.campus.platform.module.user.dto.ProfileUpdateDTO;
import com.campus.platform.module.user.service.IUserPublicService;
import com.campus.platform.module.user.service.IUserService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.vo.UserContentVO;
import com.campus.platform.module.user.vo.UserProfileVO;
import com.campus.platform.module.user.vo.UserReviewVO;

import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Resource
    private IUserService userService;

    @Resource
    private IUserPublicService userPublicService;

    @GetMapping("/info")
    public Result<User> info() {
        return Result.ok(userService.getById(UserContext.getUid()));
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        return Result.ok(userService.updateProfile(UserContext.getUid(), dto));
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        userService.updatePassword(UserContext.getUid(), dto);
        return Result.ok();
    }

    /* ---------- 他人主页（公开只读，脱敏） ---------- */

    @GetMapping("/profile/{id}")
    public Result<UserProfileVO> profile(@PathVariable Long id) {
        return Result.ok(userPublicService.profile(id));
    }

    @GetMapping("/{id}/idles")
    public Result<List<UserContentVO>> idles(@PathVariable Long id) {
        return Result.ok(userPublicService.idles(id));
    }

    @GetMapping("/{id}/posts")
    public Result<List<UserContentVO>> posts(@PathVariable Long id) {
        return Result.ok(userPublicService.posts(id));
    }

    @GetMapping("/{id}/activities")
    public Result<List<UserContentVO>> activities(@PathVariable Long id) {
        return Result.ok(userPublicService.activities(id));
    }

    @GetMapping("/{id}/lostfounds")
    public Result<List<UserContentVO>> lostfounds(@PathVariable Long id) {
        return Result.ok(userPublicService.lostfounds(id));
    }

    @GetMapping("/{id}/reviews")
    public Result<List<UserReviewVO>> reviews(@PathVariable Long id) {
        return Result.ok(userPublicService.reviews(id));
    }
}
