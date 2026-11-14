package com.soudan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật tác vụ theo lịch (nhắc việc, nhắc nộp báo cáo). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
