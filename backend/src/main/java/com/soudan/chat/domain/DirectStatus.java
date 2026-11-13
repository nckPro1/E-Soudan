package com.soudan.chat.domain;

/** Trạng thái của một cuộc nhắn riêng: chờ đối phương chấp nhận, đã chấp nhận (hai bên nhắn được), hoặc bị từ chối. */
public enum DirectStatus {
	PENDING,
	ACCEPTED,
	DECLINED
}
