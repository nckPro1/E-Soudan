package com.soudan.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.soudan.notification.service.MailContent.Row;
import com.soudan.notification.service.MailContent.Tone;

/** Giao diện email: đúng thương hiệu, và nội dung người dùng nhập không chèn được HTML. */
class MailTemplateTest {

	private static MailContent content(Tone tone, String title, String quote) {
		return new MailContent("subject", "Dòng xem trước", "Chờ duyệt", tone, "Tiêu đề lớn", "Minh",
				"Câu dẫn của thư", List.of(new Row("Công việc", title), new Row("Nhóm", "Alpha")), quote, "Mở công việc",
				"https://soudan.example.com/tasks/9");
	}

	@Test
	void should_useBrandColorsAndSeal() {
		String html = MailTemplate.html(content(Tone.INFO, "Báo cáo", null));

		assertThat(html).contains("報", "E-Soudan")
				.contains("#F7F4EC") // nền washi
				.contains("#C8402A") // con dấu son
				.contains("#274A78"); // nút chàm
		assertThat(html).contains("Xin chào Minh,", "Câu dẫn của thư", "Dòng xem trước", "Mở công việc");
		assertThat(html).contains("href=\"https://soudan.example.com/tasks/9\"");
	}

	@Test
	void should_showOrangeBadge_forWarningAndIndigoForInfo() {
		assertThat(MailTemplate.html(content(Tone.WARNING, "x", null))).contains("color:#D9822B");
		String info = MailTemplate.html(content(Tone.INFO, "x", null));
		assertThat(info).doesNotContain("color:#D9822B");
	}

	@Test
	void should_listAllInfoRows() {
		String html = MailTemplate.html(content(Tone.INFO, "Báo cáo quý", null));

		assertThat(html).contains("Công việc", "Báo cáo quý", "Nhóm", "Alpha");
	}

	@Test
	void should_escapeUserTextSoNoHtmlCanBeInjected() {
		String evil = "<script>alert(1)</script><img src=x onerror=alert(2)>";

		String html = MailTemplate.html(content(Tone.INFO, evil, evil));

		assertThat(html).doesNotContain("<script>").doesNotContain("<img src=x");
		assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
	}

	@Test
	void should_escapeQuotesInLinkAndNames() {
		MailContent c = new MailContent("s", "p", null, Tone.INFO, "h", "Nguyễn \"A\" <b>", "i", List.of(), null, "Mở",
				"https://x.test/?a=1&b=\"2\"");

		String html = MailTemplate.html(c);

		assertThat(html).doesNotContain("<b>").doesNotContain("href=\"https://x.test/?a=1&b=\"2\"\"");
		assertThat(html).contains("&amp;b=&quot;2&quot;");
	}

	@Test
	void should_keepLineBreaksInQuote() {
		String html = MailTemplate.html(content(Tone.INFO, "x", "Dòng 1\nDòng 2"));

		assertThat(html).contains("Dòng 1<br>Dòng 2");
	}

	@Test
	void should_omitBadgeAndQuote_whenAbsent() {
		MailContent c = new MailContent("s", "p", null, Tone.INFO, "h", "A", "i", List.of(), null, "Mở", "https://x.test");

		String html = MailTemplate.html(c);

		assertThat(html).doesNotContain("border-left:3px solid");
		assertThat(html).doesNotContain("display:inline-block;padding:3px 10px");
	}

	@Test
	void should_buildReadablePlainTextFallback() {
		String text = MailTemplate.text(content(Tone.INFO, "Báo cáo", "Dòng 1\nDòng 2"));

		assertThat(text).startsWith("Xin chào Minh,")
				.contains("Câu dẫn của thư", "Công việc: Báo cáo", "Nhóm: Alpha", "  > Dòng 1", "  > Dòng 2",
						"Mở công việc: https://soudan.example.com/tasks/9")
				.doesNotContain("<").doesNotContain("&lt;");
	}
}
