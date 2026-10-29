package com.campus.platform.module.favorite.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.favorite.entity.Favorite;
import com.campus.platform.module.favorite.mapper.FavoriteMapper;
import com.campus.platform.module.favorite.vo.FavoriteVO;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.mapper.PostMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一收藏：活动/闲置/失物/动态 收藏与聚合列表。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ActivityMapper activityMapper;
    private final IdleItemMapper idleItemMapper;
    private final LostFoundMapper lostFoundMapper;
    private final PostMapper postMapper;
    private final ObjectMapper objectMapper;

    /** 收藏（幂等：重复收藏直接返回） */
    @Transactional
    public void favorite(Long userId, String type, Long targetId) {
        checkTarget(type, targetId);
        Long exist = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, targetId));
        if (exist != null && exist > 0) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(type);
        favorite.setTargetId(targetId);
        favorite.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(favorite);
    }

    /** 取消收藏 */
    public void unfavorite(Long userId, String type, Long targetId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, targetId));
    }

    /** 是否已收藏 */
    public boolean isFavorited(Long userId, String type, Long targetId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, targetId));
        return count != null && count > 0;
    }

    /** 我的收藏（聚合标题与封面） */
    public PageResult<FavoriteVO> myFavorites(Long userId, int pageNum, int pageSize) {
        Page<Favorite> page = favoriteMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));
        List<Favorite> records = page.getRecords();
        List<FavoriteVO> vos = new ArrayList<>(records.size());

        Map<String, Map<Long, String[]>> meta = new HashMap<>();
        Map<String, List<Favorite>> byType = records.stream()
                .collect(Collectors.groupingBy(Favorite::getTargetType));
        for (Map.Entry<String, List<Favorite>> entry : byType.entrySet()) {
            List<Long> ids = entry.getValue().stream().map(Favorite::getTargetId).collect(Collectors.toList());
            meta.put(entry.getKey(), loadMeta(entry.getKey(), ids));
        }

        for (Favorite f : records) {
            FavoriteVO vo = new FavoriteVO();
            vo.setId(f.getId());
            vo.setTargetType(f.getTargetType());
            vo.setTargetId(f.getTargetId());
            vo.setCreateTime(f.getCreateTime());
            String[] m = meta.getOrDefault(f.getTargetType(), Map.of()).get(f.getTargetId());
            if (m != null) {
                vo.setTitle(m[0]);
                vo.setImage(m[1]);
            } else {
                vo.setTitle("内容已删除");
            }
            vos.add(vo);
        }

        PageResult<FavoriteVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(vos);
        return result;
    }

    /** 按类型批量加载 (id -> [title, firstImage]) */
    private Map<Long, String[]> loadMeta(String type, List<Long> ids) {
        Map<Long, String[]> map = new HashMap<>();
        switch (type) {
            case Constants.BIZ_ACTIVITY -> {
                for (Activity a : activityMapper.selectBatchIds(ids)) {
                    map.put(a.getId(), new String[]{a.getTitle(), firstImage(a.getImages())});
                }
            }
            case Constants.BIZ_IDLE -> {
                for (IdleItem i : idleItemMapper.selectBatchIds(ids)) {
                    map.put(i.getId(), new String[]{i.getTitle(), firstImage(i.getImages())});
                }
            }
            case Constants.BIZ_LOSTFOUND -> {
                for (LostFound lf : lostFoundMapper.selectBatchIds(ids)) {
                    map.put(lf.getId(), new String[]{lf.getTitle(), firstImage(lf.getImages())});
                }
            }
            case Constants.BIZ_POST -> {
                for (Post p : postMapper.selectBatchIds(ids)) {
                    String title = p.getContent() == null ? "动态" : p.getContent().replaceAll("\\s+", " ").trim();
                    if (title.length() > 30) {
                        title = title.substring(0, 30) + "…";
                    }
                    map.put(p.getId(), new String[]{title, firstImage(p.getImages())});
                }
            }
            default -> { }
        }
        return map;
    }

    /** 提取 images JSON 数组第一张图 */
    private String firstImage(String images) {
        if (images == null || images.isBlank() || "[]".equals(images.trim())) {
            return null;
        }
        try {
            List<String> list = objectMapper.readValue(images, new TypeReference<List<String>>() { });
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验收藏对象存在 */
    private void checkTarget(String type, Long targetId) {
        boolean ok = switch (type) {
            case Constants.BIZ_ACTIVITY -> activityMapper.selectById(targetId) != null;
            case Constants.BIZ_IDLE -> idleItemMapper.selectById(targetId) != null;
            case Constants.BIZ_LOSTFOUND -> lostFoundMapper.selectById(targetId) != null;
            case Constants.BIZ_POST -> postMapper.selectById(targetId) != null;
            default -> false;
        };
        if (!ok) {
            throw new BizException(ResultCode.NOT_FOUND, "收藏对象不存在");
        }
    }
}
