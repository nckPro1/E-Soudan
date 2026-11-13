package com.soudan.common.ai;

import java.time.Instant;
import java.util.List;

/**
 * Nguồn dữ liệu cho truy xuất của trợ lý AI. Mỗi module có dữ liệu đáng hỏi hiện thực một nguồn, và PHẢI chỉ trả về
 * những gì người đang đăng nhập được phép xem (cùng quy tắc với giao diện thường). Nhắn riêng không bao giờ là một nguồn.
 */
public interface KnowledgeSource {

	/** Tên loại, trùng {@link KnowledgeDoc#kind()}. */
	String kind();

	/** Dữ liệu người đang đăng nhập xem được, từ mốc {@code since} trở về nay, mới nhất trước, tối đa {@code limit} mẩu. */
	List<KnowledgeDoc> visibleSince(Instant since, int limit);
}
