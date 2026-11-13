package com.soudan.ai.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Nhật ký một lượt dùng trợ lý AI: ai, làm gì, lúc nào, đọc bao nhiêu mẩu dữ liệu. Không lưu câu hỏi hay câu trả lời.
 * Dùng để giới hạn lượt dùng mỗi giờ và để truy vết AI đã đọc dữ liệu cho ai.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 20)
	private String kind;

	@Column(nullable = false)
	private int sourceCount;

	@Column(nullable = false)
	private Instant createdAt;

	protected AiUsage() {
		// JPA
	}

	public AiUsage(Long userId, String kind, int sourceCount, Instant createdAt) {
		this.userId = userId;
		this.kind = kind;
		this.sourceCount = sourceCount;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getKind() {
		return kind;
	}

	public int getSourceCount() {
		return sourceCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
