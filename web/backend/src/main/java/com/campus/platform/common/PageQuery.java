package com.campus.platform.common;

import lombok.Data;

/**
 * 分页查询基类：对 pageNum/pageSize 的 null 做默认值处理，并限制 pageSize 上限。
 */
@Data
public class PageQuery {

    private static final int MAX_PAGE_SIZE = 100;

    private Integer pageNum;
    private Integer pageSize;

    /**
     * 页码默认 1。
     */
    public Integer getPageNum() {
        return pageNum == null ? 1 : pageNum;
    }

    /**
     * 每页条数默认 10，上限 {@value #MAX_PAGE_SIZE}。
     */
    public Integer getPageSize() {
        if (pageSize == null) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
