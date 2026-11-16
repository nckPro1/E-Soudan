/**
 * Module meeting: tổ chức cuộc họp video (học trực tuyến) qua Jitsi as a Service của 8x8. Backend không lưu cuộc họp, chỉ ký
 * token JaaS: người bấm "Cuộc họp mới" là chủ trì (moderator, duyệt người vào phòng chờ), ai có link cũng vào được bằng token
 * khách và phải chờ chủ trì cho vào. Không phụ thuộc module nghiệp vụ nào, chỉ dùng {@code common}.
 */
package com.soudan.meeting;
