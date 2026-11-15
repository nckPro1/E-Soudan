package com.soudan.common.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Lưu file trên Amazon S3 (bật bằng {@code app.storage.type=s3}). Quyền truy cập bucket lấy từ IAM role gắn vào máy chủ
 * (chuỗi thông tin xác thực mặc định của AWS SDK), nên không có khoá nào nằm trong mã nguồn hay cấu hình.
 *
 * <p>Giống {@link LocalFileStorage}: tên đối tượng là UUID ngẫu nhiên, mọi khoá nhận từ ngoài phải đúng dạng UUID nên không
 * thể trỏ sang đối tượng khác trong bucket. Mỗi file nhỏ (đã bị giới hạn bởi {@link UploadPolicy}) nên được đọc hết vào bộ nhớ
 * để biết độ dài trước khi gửi.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

	private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	private static final String PREFIX = "uploads/";

	private final S3Client client;
	private final String bucket;
	private final long maxBytes;

	public S3FileStorage(S3Client client, StorageProperties properties) {
		this.client = client;
		this.bucket = properties.s3Bucket();
		this.maxBytes = properties.maxFileSize().toBytes();
	}

	@Override
	public String save(InputStream content) throws IOException {
		byte[] bytes = readLimited(content);
		String key = UUID.randomUUID().toString();
		try {
			client.putObject(PutObjectRequest.builder().bucket(bucket).key(PREFIX + key).build(), RequestBody.fromBytes(bytes));
		} catch (SdkException e) {
			throw new IOException("Không lưu được file lên S3", e);
		}
		return key;
	}

	@Override
	public InputStream load(String key) throws IOException {
		String objectKey = objectKey(key);
		try {
			return client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
		} catch (NoSuchKeyException e) {
			throw new FileNotFoundException(key);
		} catch (SdkException e) {
			throw new IOException("Không đọc được file từ S3", e);
		}
	}

	@Override
	public void delete(String key) throws IOException {
		String objectKey = objectKey(key);
		try {
			// S3 không báo lỗi khi đối tượng không còn, đúng với hợp đồng "xoá nếu còn"
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
		} catch (SdkException e) {
			throw new IOException("Không xoá được file trên S3", e);
		}
	}

	private static String objectKey(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Khoá file không hợp lệ");
		}
		return PREFIX + key;
	}

	/** Đọc hết nội dung nhưng không quá giới hạn dung lượng một file (phòng nội dung lớn bất thường làm tràn bộ nhớ). */
	private byte[] readLimited(InputStream content) throws IOException {
		byte[] bytes = content.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
		if (bytes.length > maxBytes) {
			throw new IOException("File vượt quá dung lượng cho phép");
		}
		return bytes;
	}
}
