package com.campus.platform.module.chat.vo;

import com.campus.platform.module.user.entity.User;
import lombok.Data;

@Data
public class BlockedUserVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String bio;

    public static BlockedUserVO from(User user) {
        BlockedUserVO vo = new BlockedUserVO();
        vo.id = user.getId();
        vo.nickname = user.getNickname();
        vo.avatar = user.getAvatar();
        vo.bio = user.getBio();
        return vo;
    }
}
