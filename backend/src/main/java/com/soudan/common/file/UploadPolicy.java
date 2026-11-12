package com.soudan.common.file;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ValidationException;

/**
 * Quy định file được phép nộp: PDF, Word, Excel, ảnh, zip; tối đa theo cấu hình (mặc định 10 MB).
 * Kiểm tra cả đuôi file lẫn "chữ ký" đầu file nên đổi đuôi một file thực thi thành .pdf không qua được.
 */
@Component
public class UploadPolicy {

	private enum Kind { PDF, PNG, JPEG, GIF, WEBP, ZIP, OLE }

	private static final Map<String, Kind> EXTENSIONS = Map.ofEntries(
			Map.entry("pdf", Kind.PDF),
			Map.entry("png", Kind.PNG),
			Map.entry("jpg", Kind.JPEG),
			Map.entry("jpeg", Kind.JPEG),
			Map.entry("gif", Kind.GIF),
			Map.entry("webp", Kind.WEBP),
			Map.entry("zip", Kind.ZIP),
			Map.entry("docx", Kind.ZIP),
			Map.entry("xlsx", Kind.ZIP),
			Map.entry("doc", Kind.OLE),
			Map.entry("xls", Kind.OLE));

	/** Số byte đầu file cần đọc để nhận dạng. */
	public static final int HEAD_BYTES = 16;

	private static final Set<Character> UNSAFE_NAME_CHARS = Set.of('/', '\\', ':', '*', '?', '"', '<', '>', '|');

	private final long maxBytes;

	public UploadPolicy(StorageProperties properties) {
		this.maxBytes = properties.maxFileSize().toBytes();
	}

	public long maxBytes() {
		return maxBytes;
	}

	/**
	 * Kiểm tra một file sắp nộp.
	 *
	 * @param head vài byte đầu của file (tối đa {@link #HEAD_BYTES})
	 * @throws ValidationException nếu file rỗng, quá lớn, sai loại hoặc nội dung không khớp đuôi file
	 */
	public void validate(String originalName, long sizeBytes, byte[] head) {
		if (sizeBytes <= 0) {
			throw new ValidationException(ErrorCode.INVALID_FILE, "File rỗng");
		}
		if (sizeBytes > maxBytes) {
			throw new ValidationException(ErrorCode.FILE_TOO_LARGE,
					"File vượt quá " + (maxBytes / (1024 * 1024)) + " MB");
		}
		Kind expected = EXTENSIONS.get(extensionOf(originalName));
		if (expected == null) {
			throw new ValidationException(ErrorCode.INVALID_FILE, "Chỉ nhận PDF, Word, Excel, ảnh và zip");
		}
		if (!matches(expected, head)) {
			throw new ValidationException(ErrorCode.INVALID_FILE, "Nội dung file không khớp với đuôi file");
		}
	}

	/** Content-Type suy từ đuôi file (không tin giá trị client khai báo). */
	public String contentTypeOf(String name) {
		return switch (extensionOf(name)) {
			case "pdf" -> "application/pdf";
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			case "zip" -> "application/zip";
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			case "doc" -> "application/msword";
			case "xls" -> "application/vnd.ms-excel";
			default -> "application/octet-stream";
		};
	}

	/** Tên hiển thị an toàn: bỏ đường dẫn và ký tự điều khiển/đặc biệt, giới hạn độ dài. */
	public String safeName(String originalName) {
		String name = originalName == null ? "" : originalName;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		StringBuilder sb = new StringBuilder();
		for (char c : name.toCharArray()) {
			if (c >= 0x20 && c != 0x7f && !UNSAFE_NAME_CHARS.contains(c)) {
				sb.append(c);
			}
		}
		String cleaned = sb.toString().strip();
		if (cleaned.length() > 200) {
			String ext = extensionOf(cleaned);
			cleaned = cleaned.substring(0, 190) + (ext.isEmpty() ? "" : "." + ext);
		}
		return cleaned.isEmpty() ? "file" : cleaned;
	}

	private static String extensionOf(String name) {
		if (name == null) {
			return "";
		}
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT).strip();
	}

	private static boolean matches(Kind kind, byte[] h) {
		return switch (kind) {
			case PDF -> startsWith(h, '%', 'P', 'D', 'F');
			case PNG -> startsWith(h, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
			case JPEG -> startsWith(h, 0xFF, 0xD8, 0xFF);
			case GIF -> startsWith(h, 'G', 'I', 'F', '8');
			case WEBP -> startsWith(h, 'R', 'I', 'F', 'F') && h.length >= 12 && h[8] == 'W' && h[9] == 'E'
					&& h[10] == 'B' && h[11] == 'P';
			case ZIP -> startsWith(h, 'P', 'K', 0x03, 0x04) || startsWith(h, 'P', 'K', 0x05, 0x06);
			case OLE -> startsWith(h, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
		};
	}

	private static boolean startsWith(byte[] head, int... expected) {
		if (head == null || head.length < expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((head[i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}
}
