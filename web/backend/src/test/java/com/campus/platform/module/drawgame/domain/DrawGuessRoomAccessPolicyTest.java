package com.campus.platform.module.drawgame.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrawGuessRoomAccessPolicyTest {
    @Test
    void privateRoomRequiresItsPasswordAndPublicRoomDoesNot() {
        String passwordHash = DrawGuessRoomAccessPolicy.hashPassword("campus-88");

        assertTrue(DrawGuessRoomAccessPolicy.canJoin(true, passwordHash, "campus-88"));
        assertFalse(DrawGuessRoomAccessPolicy.canJoin(true, passwordHash, "wrong"));
        assertFalse(DrawGuessRoomAccessPolicy.canJoin(true, passwordHash, null));
        assertTrue(DrawGuessRoomAccessPolicy.canJoin(false, null, null));
    }
}
