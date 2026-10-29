package com.campus.platform.module.drawgame.domain;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Keeps private-room passwords one-way and applies the public/private join rule. */
public final class DrawGuessRoomAccessPolicy {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private DrawGuessRoomAccessPolicy() { }

    public static String hashPassword(String password) {
        if (password == null || password.length() < 4 || password.length() > 32) {
            throw new IllegalArgumentException("房间密码长度需为4到32位");
        }
        return PASSWORD_ENCODER.encode(password);
    }

    public static boolean canJoin(boolean isPrivate, String passwordHash, String submittedPassword) {
        if (!isPrivate) return true;
        return passwordHash != null && submittedPassword != null
                && !submittedPassword.isBlank()
                && PASSWORD_ENCODER.matches(submittedPassword, passwordHash);
    }
}
