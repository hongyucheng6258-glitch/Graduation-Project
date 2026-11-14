package com.campus.platform.module.friend;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("friendship")
public class Friendship {
    private Long id;
    private Long user1Id;
    private Long user2Id;
    private Long requesterId;
    private String greeting;
    private String status;
    private LocalDateTime updateTime;

    public Long peerId(Long userId) {
        return userId.equals(user1Id) ? user2Id : user1Id;
    }
}
