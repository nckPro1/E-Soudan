package com.soudan.notification.service;

import org.springframework.web.util.HtmlUtils;

import com.soudan.notification.service.MailContent.Row;
import com.soudan.notification.service.MailContent.Tone;

/**
 * Dựng email HTML theo giao diện của E-Soudan (nền washi, con dấu 報 màu son, nút chàm) và bản chữ thuần dự phòng.
 * Dùng bảng và CSS nội tuyến vì các trình đọc mail (Gmail, Outlook...) bỏ qua hoặc làm hỏng CSS hiện đại.
 * Mọi chuỗi đi vào HTML đều được escape: tiêu đề task hay nội dung bình luận do người dùng nhập không thể chèn HTML.
 */
final class MailTemplate {

	// Màu lấy từ design token của web (frontend/src/shared/styles/tokens.css)
	private static final String WASHI = "#F7F4EC";
	private static final String SURFACE = "#FFFFFF";
	private static final String SURFACE_MUTED = "#FAF8F5";
	private static final String BORDER = "#DAD6CB";
	private static final String TEXT = "#2B2B2B";
	private static final String TEXT_SECONDARY = "#6B6B66";
	private static final String INDIGO = "#274A78";
	private static final String VERMILION = "#C8402A";
	private static final String ORANGE = "#D9822B";

	private static final String SANS = "'Noto Sans JP','Segoe UI',Roboto,Helvetica,Arial,sans-serif";
	private static final String SERIF = "'Noto Serif JP','Hiragino Mincho ProN','Yu Mincho',Georgia,serif";

	private MailTemplate() {
	}

	static String html(MailContent c) {
		String accent = c.tone() == Tone.WARNING ? ORANGE : INDIGO;
		StringBuilder rows = new StringBuilder();
		for (Row row : c.rows()) {
			rows.append("""
					<tr>
					  <td style="padding:6px 16px 6px 0;font-size:13px;color:%s;white-space:nowrap;vertical-align:top;">%s</td>
					  <td style="padding:6px 0;font-size:14px;color:%s;font-weight:500;vertical-align:top;">%s</td>
					</tr>
					""".formatted(TEXT_SECONDARY, esc(row.label()), TEXT, esc(row.value())));
		}

		String badge = c.badge() == null ? "" : """
				<span style="display:inline-block;padding:3px 10px;border:1px solid %s;border-radius:2px;font-size:12px;font-weight:500;color:%s;">%s</span>
				""".formatted(accent, accent, esc(c.badge()));

		String quote = c.quote() == null ? "" : """
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
				  <tr>
				    <td style="padding:12px 16px;background:%s;border:1px solid %s;border-left:3px solid %s;border-radius:2px;font-size:14px;line-height:1.6;color:%s;">%s</td>
				  </tr>
				</table>
				""".formatted(SURFACE_MUTED, BORDER, accent, TEXT, multiline(c.quote()));

		return """
				<!DOCTYPE html>
				<html lang="vi">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width,initial-scale=1">
				<title>%1$s</title>
				</head>
				<body style="margin:0;padding:0;background:%2$s;font-family:%3$s;color:%4$s;">
				<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:%2$s;">%5$s</div>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%2$s;">
				  <tr>
				    <td align="center" style="padding:32px 16px;">
				      <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="width:100%%;max-width:560px;">
				        <tr>
				          <td style="padding:0 0 16px 4px;">
				            <table role="presentation" cellpadding="0" cellspacing="0">
				              <tr>
				                <td width="36" height="36" align="center" valign="middle" style="width:36px;height:36px;background:%6$s;border-radius:3px;color:#FFFFFF;font-family:%7$s;font-size:20px;font-weight:700;line-height:36px;">報</td>
				                <td style="padding-left:12px;font-family:%7$s;font-size:18px;font-weight:600;letter-spacing:0.05em;color:%4$s;">E-Soudan</td>
				              </tr>
				            </table>
				          </td>
				        </tr>
				        <tr>
				          <td style="padding:32px;background:%8$s;border:1px solid %9$s;border-radius:4px;">
				            <div style="margin:0 0 16px;">%10$s</div>
				            <h1 style="margin:0 0 16px;font-family:%7$s;font-size:22px;font-weight:500;line-height:1.35;color:%4$s;">%11$s</h1>
				            <p style="margin:0 0 4px;font-size:14px;line-height:1.6;color:%4$s;">Xin chào %12$s,</p>
				            <p style="margin:0 0 20px;font-size:14px;line-height:1.6;color:%4$s;">%13$s</p>
				            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
				              %14$s
				            </table>
				            %15$s
				            <table role="presentation" cellpadding="0" cellspacing="0">
				              <tr>
				                <td style="background:%16$s;border-radius:4px;">
				                  <a href="%17$s" style="display:inline-block;padding:12px 28px;font-size:14px;font-weight:500;color:#FFFFFF;text-decoration:none;">%18$s</a>
				                </td>
				              </tr>
				            </table>
				          </td>
				        </tr>
				        <tr>
				          <td style="padding:16px 4px 0;font-size:12px;line-height:1.6;color:%19$s;">
				            Nút không bấm được? Sao chép liên kết này vào trình duyệt:<br>
				            <a href="%17$s" style="color:%16$s;word-break:break-all;">%17$s</a><br><br>
				            Đây là email tự động từ hệ thống E-Soudan, vui lòng không trả lời thư này.
				          </td>
				        </tr>
				      </table>
				    </td>
				  </tr>
				</table>
				</body>
				</html>
				""".formatted(esc(c.subject()), WASHI, SANS, TEXT, esc(c.preheader()), VERMILION, SERIF, SURFACE, BORDER,
				badge, esc(c.heading()), esc(c.greetingName()), esc(c.intro()), rows, quote, INDIGO, esc(c.link()),
				esc(c.buttonLabel()), TEXT_SECONDARY);
	}

	/** Bản chữ thuần cho trình đọc mail không hiện được HTML. */
	static String text(MailContent c) {
		StringBuilder sb = new StringBuilder();
		sb.append("Xin chào ").append(c.greetingName()).append(",\n\n");
		sb.append(c.intro()).append("\n\n");
		for (Row row : c.rows()) {
			sb.append(row.label()).append(": ").append(row.value()).append('\n');
		}
		if (c.quote() != null) {
			sb.append('\n');
			c.quote().lines().forEach(line -> sb.append("  > ").append(line).append('\n'));
		}
		sb.append('\n').append(c.buttonLabel()).append(": ").append(c.link()).append("\n\n");
		sb.append("-- E-Soudan (email tự động, vui lòng không trả lời)\n");
		return sb.toString();
	}

	private static String esc(String value) {
		// UTF-8: chỉ escape ký tự đặc biệt của HTML, giữ nguyên chữ có dấu (mặc định của Spring sẽ đổi chữ có dấu thành &#...;).
		return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
	}

	/** Escape rồi đổi xuống dòng thành thẻ br để đoạn trích giữ đúng hình dạng. */
	private static String multiline(String value) {
		return esc(value).replace("\r\n", "\n").replace("\n", "<br>");
	}
}
