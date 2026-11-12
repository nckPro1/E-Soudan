package com.soudan.common.file;

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
		@DefaultValue("${java.io.tmpdir}/soudan-uploads") String dir,
		@DefaultValue("10MB") DataSize maxFileSize) {
}
