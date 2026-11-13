package com.soudan.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** File hoặc ảnh đính kèm một tin nhắn. {@code storageKey} là khoá ngẫu nhiên của FileStorage, không phải tên file. */
@Entity
@Table(name = "chat_attachment")
public class ChatAttachment {

	/** Tối đa bao nhiêu file đính kèm cho một tin nhắn. */
	public static final int MAX_PER_MESSAGE = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long messageId;

	@Column(nullable = false)
	private String fileName;

	@Column(nullable = false)
	private String contentType;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private String storageKey;

	protected ChatAttachment() {
		// JPA
	}

	public ChatAttachment(Long messageId, String fileName, String contentType, long sizeBytes, String storageKey) {
		this.messageId = messageId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
	}

	public boolean isImage() {
		return contentType.startsWith("image/");
	}

	public Long getId() {
		return id;
	}

	public Long getMessageId() {
		return messageId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}
}
