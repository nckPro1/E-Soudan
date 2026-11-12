package com.soudan.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

/** Chỉ emoji thật mới được làm biểu cảm; mọi thứ khác bị từ chối. */
class CommentReactionTest {

	private static ErrorCode codeOf(String value) {
		try {
			CommentReaction.requireEmoji(value);
		} catch (BusinessException e) {
			return e.getCode();
		}
		return null;
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"👍", "❤️", "😂", "🎉", "👀", "🙏",
			"👍🏽", // màu da
			"👨‍👩‍👧‍👦", // gia đình (ghép bằng ZWJ)
			"🇻🇳", // cờ
			"1️⃣", // keycap
			"🏴󠁧󠁢󠁥󠁮󠁧󠁿" // cờ vùng (tag sequence)
	})
	void should_accept_realEmoji(String emoji) {
		assertThat(codeOf(emoji)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "a", "abc", "1", "#", "*", "<script>", "👍a", "👍 ", " ", "", "\n", "👍\u0000", "‮" })
	void should_reject_nonEmoji(String value) {
		assertThat(codeOf(value)).isEqualTo(ErrorCode.INVALID_EMOJI);
	}

	@Test
	void should_reject_nullAndOverlongValues() {
		assertThat(codeOf(null)).isEqualTo(ErrorCode.INVALID_EMOJI);
		assertThat(codeOf("👍".repeat(20))).isEqualTo(ErrorCode.INVALID_EMOJI);
	}
}
