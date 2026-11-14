package com.campus.platform.common.mapper;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;

public final class MapperQueries {
    private MapperQueries() {
    }

    public static <W extends AbstractWrapper<?, ?, W>> W limit(W query, int count) {
        return query.last("LIMIT " + count);
    }
}
