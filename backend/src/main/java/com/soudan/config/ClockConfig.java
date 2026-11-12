package com.soudan.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Đồng hồ được inject để test được các quy tắc theo thời gian (hết hạn token, báo cáo mỗi ngày...). */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
