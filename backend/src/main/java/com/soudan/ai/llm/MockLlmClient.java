package com.soudan.ai.llm;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bản giả lập để chạy thử giao diện khi chưa có khoá API ({@code app.ai.provider=mock}). Không gọi ra ngoài; trả lời có
 * trích dẫn các tài liệu được đưa vào để kiểm tra được luồng truy xuất và phân quyền. Không dùng ở môi trường thật.
 */
public class MockLlmClient implements LlmClient {

	private static final Pattern DOC = Pattern.compile("<document id=\"(\\d+)\" [^>]*title=\"([^\"]*)\"");

	@Override
	public String complete(String system, List<Turn> turns, int maxTokens) {
		String last = turns.isEmpty() ? "" : turns.get(turns.size() - 1).content();
		Matcher m = DOC.matcher(last);
		StringBuilder out = new StringBuilder("[Chế độ giả lập, chưa gọi mô hình thật]\n");
		int count = 0;
		while (m.find() && count < 5) {
			out.append("- ").append(m.group(2)).append(" [").append(m.group(1)).append("]\n");
			count++;
		}
		if (count == 0) {
			out.append("Không có tài liệu liên quan trong ngữ cảnh.");
		}
		return out.toString().strip();
	}
}
