package com.campus.platform.module.idle.vo;

public record IdleGuideCard(long itemId, String title, String category, String expectItem,
                            String description, Integer status, boolean owned, Long publisherId) {
    public boolean isVisible() {
        return status != null;
    }
}
