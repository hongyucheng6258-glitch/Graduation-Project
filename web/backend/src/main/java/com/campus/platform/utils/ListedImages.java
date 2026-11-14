package com.campus.platform.utils;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class ListedImages {
    private static final Pattern INLINE = Pattern.compile(
            "^data:(image/(?:jpeg|jpg|png|gif|webp|avif|bmp));base64,", Pattern.CASE_INSENSITIVE);

    private ListedImages() {}

    public static List<String> urls(String resource, long id, List<String> images) {
        return IntStream.range(0, images.size()).mapToObj(index -> url(images.get(index),
                "/api/media/" + resource + "/" + id + "/" + index)).toList();
    }

    public static String avatar(Long userId, String avatar) {
        return userId == null ? avatar : url(avatar, "/api/media/avatar/" + userId + "/0");
    }

    public static String url(String image, String replacement) {
        return image != null && INLINE.matcher(image).lookingAt() ? replacement : image;
    }

    public static String at(String images, int index) {
        var values = ImageListJson.parseJson(images);
        if (index < 0 || index >= values.size()) throw missing();
        return values.get(index);
    }

    public static ResponseEntity<byte[]> response(String image) {
        if (image == null) throw missing();
        var match = INLINE.matcher(image);
        if (!match.lookingAt()) throw missing();
        try {
            byte[] bytes = Base64.getDecoder().decode(image.substring(match.end()));
            return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                    .eTag("\"" + org.springframework.util.DigestUtils.md5DigestAsHex(bytes) + "\"")
                    .contentType(MediaType.parseMediaType(match.group(1).toLowerCase(Locale.ROOT)))
                    .header("X-Content-Type-Options", "nosniff").body(bytes);
        } catch (IllegalArgumentException e) {
            throw missing();
        }
    }

    private static BizException missing() {
        return new BizException(ResultCode.NOT_FOUND, "图片不存在");
    }
}
