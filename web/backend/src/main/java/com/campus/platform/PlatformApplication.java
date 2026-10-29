package com.campus.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI校园综合服务平台 - 启动类
 * 三端（Web学生前台 / Web管理后台 / 微信小程序）共用此后端服务。
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.campus.platform.module.*.mapper")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
