package com.soudan.common.file;

import java.nio.file.Path;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Cấu hình lưu file ({@code app.storage.*}).
 *
 * @param dir         thư mục gốc lưu file khi {@code type=local}; nên nằm ngoài thư mục mã nguồn
 * @param maxFileSize dung lượng tối đa mỗi file
 * @param type        {@code local} (ổ đĩa của máy chủ) hoặc {@code s3} (Amazon S3, quyền lấy từ IAM role)
 * @param s3Bucket    tên bucket khi {@code type=s3}
 * @param s3Region    vùng AWS của bucket khi {@code type=s3}, ví dụ ap-southeast-1
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(
		@DefaultValue("") String dir,
		@DefaultValue("10MB") DataSize maxFileSize,
		@DefaultValue("local") String type,
		@DefaultValue("") String s3Bucket,
		@DefaultValue("") String s3Region) {

	private static final Set<String> TYPES = Set.of("local", "s3");

	/**
	 * Chưa cấu hình thư mục thì dùng thư mục tạm của hệ điều hành. Không dùng chuỗi "${java.io.tmpdir}" làm giá trị mặc định:
	 * {@code @DefaultValue} không thay biến, nên file bị ghi vào một thư mục tên đúng như vậy trong thư mục làm việc.
	 * Cấu hình sai (loại lạ, thiếu bucket/vùng khi dùng S3) làm ứng dụng không khởi động, thay vì lỗi lúc người dùng tải file.
	 */
	@ConstructorBinding
	public StorageProperties {
		if (!TYPES.contains(type)) {
			throw new IllegalArgumentException("app.storage.type phải là local hoặc s3, nhận được: " + type);
		}
		if ("s3".equals(type) && (s3Bucket.isBlank() || s3Region.isBlank())) {
			throw new IllegalArgumentException("app.storage.type=s3 cần app.storage.s3-bucket và app.storage.s3-region");
		}
		if (dir == null || dir.isBlank()) {
			dir = Path.of(System.getProperty("java.io.tmpdir"), "soudan-uploads").toString();
		}
	}

	/** Lưu trên ổ đĩa với thư mục và giới hạn cho trước (dùng trong test). */
	public StorageProperties(String dir, DataSize maxFileSize) {
		this(dir, maxFileSize, "local", "", "");
	}
}
