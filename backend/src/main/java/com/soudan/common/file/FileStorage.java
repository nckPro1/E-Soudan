package com.soudan.common.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * Nơi lưu file nộp. Nghiệp vụ chỉ biết "khoá" (chuỗi ngẫu nhiên do hệ thống sinh), không biết đường dẫn thật,
 * nên có thể đổi sang S3 sau này mà không sửa module nghiệp vụ.
 */
public interface FileStorage {

	/** Lưu nội dung và trả về khoá. Tên file người dùng đặt không bao giờ được dùng làm tên lưu. */
	String save(InputStream content) throws IOException;

	/** Mở file theo khoá. Ném {@link java.io.FileNotFoundException} nếu không có. Người gọi đóng stream. */
	InputStream load(String key) throws IOException;

	/** Xoá file nếu còn; không lỗi khi file đã mất. */
	void delete(String key) throws IOException;
}
