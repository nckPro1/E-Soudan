package com.soudan.common.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Tạo S3Client khi lưu file lên S3. Thông tin xác thực lấy từ IAM role của máy chủ (không cấu hình khoá ở đây). */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
class S3Config {

	@Bean(destroyMethod = "close")
	S3Client s3Client(StorageProperties properties) {
		return S3Client.builder().region(Region.of(properties.s3Region())).build();
	}
}
