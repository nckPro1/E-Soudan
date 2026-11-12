package com.soudan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bật xử lý bất đồng bộ cho {@code @ApplicationModuleListener}: gửi email không làm chậm hay làm hỏng
 * request đã commit thành công.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
