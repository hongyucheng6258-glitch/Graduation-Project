package com.campus.platform.module.favorite.controller;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.favorite.service.FavoriteService;
import com.campus.platform.module.favorite.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 统一收藏接口：收藏 / 取消 / 状态 / 我的收藏。
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{type}/{id}")
    public R<Void> favorite(@PathVariable String type, @PathVariable Long id) {
        favoriteService.favorite(UserContext.getUid(), type, id);
        return R.ok();
    }

    @DeleteMapping("/{type}/{id}")
    public R<Void> unfavorite(@PathVariable String type, @PathVariable Long id) {
        favoriteService.unfavorite(UserContext.getUid(), type, id);
        return R.ok();
    }

    @GetMapping("/{type}/{id}/status")
    public R<Boolean> status(@PathVariable String type, @PathVariable Long id) {
        return R.ok(favoriteService.isFavorited(UserContext.getUid(), type, id));
    }

    @GetMapping("/my")
    public R<PageResult<FavoriteVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(favoriteService.myFavorites(UserContext.getUid(), pageNum, pageSize));
    }
}
