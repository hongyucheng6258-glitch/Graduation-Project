package com.campus.platform.module.chat.vo;

import com.campus.platform.module.user.entity.User;

public record ChatUserVO(Long id, String nickname, String avatar) {
    public static ChatUserVO from(User user) {
        return new ChatUserVO(user.getId(), user.getNickname(), user.getAvatar());
    }
}
