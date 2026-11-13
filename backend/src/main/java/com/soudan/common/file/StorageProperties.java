package com.soudan.common.file;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Cấu hình lưu file ({@code app.storage.*}). {@code dir} nên nằm ngoài thư mục mã nguồn.
 *
 * @param dir thư mục gốc lưu file
 * @param maxFileSize dung lượng tối đa mỗi file
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(
		@DefaultValue("") String dir,
		@DefaultValue("10MB") DataSize maxFileSize) {

	/**
	 * Chưa cấu hình thì dùng thư mục tạm của hệ điều hành. Không dùng chuỗi "${java.io.tmpdir}" làm giá trị mặc định:
	 * {@code @DefaultValue} không thay biến, nên file bị ghi vào một thư mục tên đúng như vậy trong thư mục làm việc.
	 * Môi trường thật phải đặt {@code STORAGE_DIR} (thư mục tạm có thể bị hệ điều hành dọn).
	 */
	public StorageProperties {
		if (dir == null || dir.isBlank()) {
			dir = Path.of(System.getProperty("java.io.tmpdir"), "soudan-uploads").toString();
		}
	}
}
