package com.campus.platform.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果统一封装。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long total;
    private Long pages;
    private List<T> list;

    /**
     * 从 MyBatis-Plus 的 IPage 直接转换。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(page.getRecords());
        return result;
    }

    /**
     * 从 MyBatis-Plus 的 IPage 转换，并按 mapper 将实体映射为 VO。
     */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> mapper) {
        PageResult<V> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(page.getRecords().stream().map(mapper).collect(Collectors.toList()));
        return result;
    }
}
