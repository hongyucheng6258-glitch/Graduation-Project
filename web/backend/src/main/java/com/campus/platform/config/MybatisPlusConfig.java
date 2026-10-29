package com.campus.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页插件（MySQL 方言）。
 *
 * <p>第8项修复：统一分页上限 setMaxLimit(100)。
 * 所有走 selectPage 的分页接口单页最大 100 条（防止 pageSize 传超大值拉全表），
 * 前端内层列表（评论/回答/成员/认领等）再按各自 pageSize 分页消费。
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大行数（全局硬上限，覆盖所有分页接口） */
    public static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
