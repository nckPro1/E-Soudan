package com.soudan.common.util;

import java.util.regex.Pattern;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Nội dung soạn thảo rich-text (từ trình soạn thảo Tiptap ở frontend) được lưu dưới dạng HTML. HTML do người dùng gửi
 * lên KHÔNG bao giờ được tin: luôn lọc bằng danh sách cho phép trước khi lưu, nên script, thuộc tính sự kiện
 * ({@code onerror}...), iframe, style, ảnh nhúng... đều bị loại. Frontend vẫn lọc thêm một lớp khi hiển thị.
 */
public final class RichText {

	/** Chỉ các thẻ mà thanh công cụ của trình soạn thảo tạo ra: tiêu đề, đậm/nghiêng/gạch, danh sách, trích dẫn, mã, link. */
	private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
			.allowElements("p", "br", "h1", "h2", "h3", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li",
					"blockquote", "code", "pre", "hr", "a")
			.allowUrlProtocols("http", "https", "mailto")
			.allowAttributes("href").onElements("a")
			.requireRelsOnLinks("noopener", "noreferrer", "nofollow")
			.toFactory();

	private static final Pattern TAGS = Pattern.compile("<[^>]*>");

	private RichText() {
	}

	/** HTML đã lọc; null thành chuỗi rỗng. */
	public static String sanitize(String html) {
		return html == null ? "" : POLICY.sanitize(html).strip();
	}

	/** Phần chữ thuần của một đoạn HTML (đã lọc), để biết bài có nội dung thật hay chỉ toàn thẻ rỗng. */
	public static String plainText(String sanitizedHtml) {
		String text = TAGS.matcher(sanitizedHtml == null ? "" : sanitizedHtml).replaceAll(" ");
		return text.replace(' ', ' ').replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&").replace("&lt;", "<")
				.replace("&gt;", ">").replace("&#34;", "\"").replace("&#39;", "'").strip();
	}
}
