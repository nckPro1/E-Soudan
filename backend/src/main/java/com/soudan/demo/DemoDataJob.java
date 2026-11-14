package com.soudan.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Làm mới dữ liệu demo: một lần khi ứng dụng khởi động và mỗi ngày lúc 03:00 (theo {@code app.timezone}), để những gì người xem
 * đã thử không tích tụ và ngày hạn trong dữ liệu mẫu luôn tính từ hôm nay. Lỗi khi làm mới chỉ được ghi log, không làm dừng ứng dụng.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoDataJob {

	private static final Logger log = LoggerFactory.getLogger(DemoDataJob.class);

	private final DemoDataService service;
	private final DemoProperties properties;

	DemoDataJob(DemoDataService service, DemoProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		if (properties.seedOnStartup()) {
			run();
		}
	}

	@Scheduled(cron = "${app.demo.reset-cron:0 0 3 * * *}", zone = "${app.timezone}")
	void daily() {
		run();
	}

	private void run() {
		try {
			service.reseed();
		} catch (RuntimeException e) {
			log.error("Không làm mới được dữ liệu demo", e);
		}
	}
}
