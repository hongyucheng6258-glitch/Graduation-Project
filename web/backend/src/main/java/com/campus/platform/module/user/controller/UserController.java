package com.campus.platform.module.user.controller;

import com.campus.platform.module.user.dto.PasswordUpdateDTO;
import com.campus.platform.module.user.dto.ProfileUpdateDTO;
import com.campus.platform.module.user.service.UserPublicService;
import com.campus.platform.module.user.service.UserService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.vo.UserContentVO;
import com.campus.platform.module.user.vo.UserProfileVO;
import com.campus.platform.module.user.vo.UserReviewVO;

import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserPublicService userPublicService;

    @GetMapping("/info")
    public R<User> info() {
        return R.ok(userService.getById(UserContext.getUid()));
    }

    @PutMapping("/profile")
    public R<User> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        return R.ok(userService.updateProfile(UserContext.getUid(), dto));
    }

    @PutMapping("/password")
    public R<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        userService.updatePassword(UserContext.getUid(), dto);
        return R.ok();
    }

    /* ---------- 他人主页（公开只读，脱敏） ---------- */

    @GetMapping("/profile/{id}")
    public R<UserProfileVO> profile(@PathVariable Long id) {
        return R.ok(userPublicService.profile(id));
    }

    @GetMapping("/{id}/idles")
    public R<List<UserContentVO>> idles(@PathVariable Long id) {
        return R.ok(userPublicService.idles(id));
    }

    @GetMapping("/{id}/posts")
    public R<List<UserContentVO>> posts(@PathVariable Long id) {
        return R.ok(userPublicService.posts(id));
    }

    @GetMapping("/{id}/activities")
    public R<List<UserContentVO>> activities(@PathVariable Long id) {
        return R.ok(userPublicService.activities(id));
    }

    @GetMapping("/{id}/lostfounds")
    public R<List<UserContentVO>> lostfounds(@PathVariable Long id) {
        return R.ok(userPublicService.lostfounds(id));
    }

    @GetMapping("/{id}/reviews")
    public R<List<UserReviewVO>> reviews(@PathVariable Long id) {
        return R.ok(userPublicService.reviews(id));
    }
}
