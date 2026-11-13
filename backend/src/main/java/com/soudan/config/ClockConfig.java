package com.soudan.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Đồng hồ được inject để test được các quy tắc theo thời gian (hết hạn token, báo cáo mỗi ngày...). */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** Múi giờ nghiệp vụ ({@code app.timezone}): "hôm nay" của báo cáo, lịch, trợ lý AI tính theo múi giờ này, không theo UTC. */
	@Bean
	ZoneId appZone(@Value("${app.timezone}") String timezone) {
		return ZoneId.of(timezone);
	}
}
