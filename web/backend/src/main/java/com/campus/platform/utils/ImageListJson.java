package com.campus.platform.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import java.util.Collections;
import java.util.List;

/** 数据库图片列表字段的 JSON 转换。 */
public final class ImageListJson {

    private ImageListJson() {
    }

    public static String toJson(List<String> images) {
        return images == null || images.isEmpty() ? null : JSONUtil.toJsonStr(images);
    }

    public static List<String> parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static String firstImage(String json) {
        List<String> list = parseJson(json);
        return list.isEmpty() ? null : list.get(0);
    }
}
