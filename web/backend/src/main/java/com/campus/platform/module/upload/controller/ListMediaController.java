package com.campus.platform.module.upload.controller;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.notice.mapper.NoticeMapper;
import com.campus.platform.utils.ListedImages;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequiredArgsConstructor
public class ListMediaController {
    private final ActivityMapper activities;
    private final IdleItemMapper idle;
    private final LostFoundMapper lost;
    private final UserMapper users;
    private final NoticeMapper notices;

    @GetMapping("/api/media/{resource}/{id}/{index}")
    public ResponseEntity<byte[]> image(@PathVariable String resource, @PathVariable long id,
                                        @PathVariable int index) {
        if ("avatar".equals(resource)) {
            var user = users.selectById(id);
            return ListedImages.response(user != null && index == 0
                    && !Objects.equals(user.getStatus(), Constants.USER_STATUS_BANNED) ? user.getAvatar() : null);
        }
        if ("notice".equals(resource)) {
            var notice = notices.selectById(id);
            return ListedImages.response(notice != null && index == 0
                    && Objects.equals(notice.getStatus(), Constants.NOTICE_PUBLISHED) ? notice.getCover() : null);
        }
        String images = switch (resource) {
            case "activity" -> {
                var row = activities.selectById(id);
                yield row != null && approved(row.getAuditStatus()) && !Objects.equals(row.getStatus(), Constants.ACTIVITY_OFF)
                        ? row.getImages() : null;
            }
            case "idle" -> {
                var row = idle.selectById(id);
                yield row != null && approved(row.getAuditStatus()) && !Objects.equals(row.getStatus(), Constants.IDLE_OFF_SHELF)
                        ? row.getImages() : null;
            }
            case "lostfound" -> {
                var row = lost.selectById(id);
                yield row != null && approved(row.getAuditStatus()) ? row.getImages() : null;
            }
            default -> null;
        };
        if (images == null) throw new BizException(ResultCode.NOT_FOUND, "图片不存在");
        return ListedImages.response(ListedImages.at(images, index));
    }

    private boolean approved(Integer status) {
        return Objects.equals(status, Constants.AUDIT_PASS);
    }
}
