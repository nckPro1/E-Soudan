package com.soudan.common.util;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ValidationException;

/** Kiểm tra một chuỗi có đúng là emoji hay không (dùng cho biểu cảm ở bình luận task và báo cáo). */
public final class Emoji {

	private static final int MAX_CODE_UNITS = 32;

	private Emoji() {
	}

	/**
	 * Chỉ nhận chuỗi emoji thật (kể cả chuỗi ghép như gia đình, cờ, màu da, keycap); chữ thường, số trần, ký tự
	 * điều khiển hay HTML đều bị từ chối.
	 */
	public static String require(String value) {
		if (value == null || value.isBlank() || value.length() > MAX_CODE_UNITS || !isSequence(value)) {
			throw new ValidationException(ErrorCode.INVALID_EMOJI, "Biểu cảm không hợp lệ");
		}
		return value;
	}

	private static boolean isSequence(String value) {
		boolean hasNonAscii = false;
		for (int i = 0; i < value.length();) {
			int cp = value.codePointAt(i);
			i += Character.charCount(cp);
			boolean joiner = cp == 0x200D || cp == 0xFE0F || cp == 0x20E3;
			boolean tag = cp >= 0xE0020 && cp <= 0xE007F;
			if (!joiner && !tag && !Character.isEmoji(cp)) {
				return false;
			}
			if (cp > 0x7F) {
				hasNonAscii = true;
			}
		}
		// Chữ số, # và * cũng mang thuộc tính emoji; chỉ hợp lệ khi là keycap (có FE0F/20E3), tức đã có ký tự > 0x7F.
		return hasNonAscii;
	}
}
