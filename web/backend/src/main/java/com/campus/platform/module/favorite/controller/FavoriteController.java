package com.campus.platform.module.favorite.controller;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.favorite.service.IFavoriteService;
import com.campus.platform.module.favorite.vo.FavoriteVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 统一收藏接口：收藏 / 取消 / 状态 / 我的收藏。
 */
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    @Resource
    private IFavoriteService favoriteService;

    @PostMapping("/{type}/{id}")
    public Result<Void> favorite(@PathVariable String type, @PathVariable Long id) {
        favoriteService.favorite(UserContext.getUid(), type, id);
        return Result.ok();
    }

    @DeleteMapping("/{type}/{id}")
    public Result<Void> unfavorite(@PathVariable String type, @PathVariable Long id) {
        favoriteService.unfavorite(UserContext.getUid(), type, id);
        return Result.ok();
    }

    @GetMapping("/{type}/{id}/status")
    public Result<Boolean> status(@PathVariable String type, @PathVariable Long id) {
        return Result.ok(favoriteService.isFavorited(UserContext.getUid(), type, id));
    }

    @GetMapping("/my")
    public Result<PageResult<FavoriteVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(favoriteService.myFavorites(UserContext.getUid(), pageNum, pageSize));
    }
}
