package com.soudan.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RichTextTest {

	@Test
	void should_keepAllowedFormatting_when_toolbarTagsUsed() {
		String html = "<h1>A</h1><h2>B</h2><h3>C</h3><p><strong>đậm</strong> <em>nghiêng</em> <u>gạch</u></p>"
				+ "<ul><li>x</li></ul><ol><li>y</li></ol><blockquote>q</blockquote>";
		assertThat(RichText.sanitize(html)).isEqualTo(html);
	}

	@Test
	void should_removeScriptsAndEventHandlers_when_htmlIsHostile() {
		String out = RichText.sanitize("<p onclick=\"x()\">hi</p><script>alert(1)</script><iframe src=\"//evil\"></iframe>"
				+ "<img src=x onerror=alert(1)><style>p{}</style>");
		assertThat(out).isEqualTo("<p>hi</p>");
	}

	@Test
	void should_dropDangerousLinks_when_protocolIsNotAllowed() {
		assertThat(RichText.sanitize("<a href=\"javascript:alert(1)\">x</a>")).doesNotContain("javascript");
		String ok = RichText.sanitize("<a href=\"https://example.com\">ok</a>");
		assertThat(ok).contains("href=\"https://example.com\"").contains("nofollow").contains("noopener").contains("noreferrer");
	}

	@Test
	void should_reportEmptyText_when_onlyEmptyTagsOrSpaces() {
		assertThat(RichText.plainText(RichText.sanitize("<p></p><h2> </h2><p>&nbsp;</p>"))).isEmpty();
		assertThat(RichText.plainText(RichText.sanitize("<p>Xin <b>chào</b></p>"))).contains("Xin").contains("chào");
	}

	@Test
	void should_returnEmpty_when_nullInput() {
		assertThat(RichText.sanitize(null)).isEmpty();
	}
}
