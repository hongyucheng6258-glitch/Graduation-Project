package com.campus.platform.module.favorite.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.favorite.vo.FavoriteVO;

/**
 * 收藏服务接口：活动/闲置/失物/动态的收藏与聚合列表。
 */
public interface IFavoriteService {

    /** 收藏（幂等：重复收藏直接返回） */
    void favorite(Long userId, String type, Long targetId);

    /** 取消收藏 */
    void unfavorite(Long userId, String type, Long targetId);

    /** 是否已收藏 */
    boolean isFavorited(Long userId, String type, Long targetId);

    /** 我的收藏（聚合标题与封面） */
    PageResult<FavoriteVO> myFavorites(Long userId, int pageNum, int pageSize);
}
