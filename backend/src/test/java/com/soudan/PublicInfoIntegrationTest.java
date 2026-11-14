package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Giờ hoạt động công khai cho trang đăng nhập: có khi được bật, null khi máy chạy cả ngày. */
@TestPropertySource(properties = { "app.operating-hours.enabled=true", "app.operating-hours.start=08:30",
		"app.operating-hours.end=18:00" })
class PublicInfoIntegrationTest extends IntegrationTestBase {

	@Test
	void should_exposeOperatingHours_withoutLogin_whenEnabled() throws Exception {
		mockMvc.perform(get("/api/v1/public/info")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.operatingHours.start").value("08:30"))
				.andExpect(jsonPath("$.data.operatingHours.end").value("18:00"))
				.andExpect(jsonPath("$.data.operatingHours.timezone").value("Asia/Ho_Chi_Minh"))
				.andExpect(jsonPath("$.data.operatingHours.days.length()").value(5))
				.andExpect(jsonPath("$.data.operatingHours.days[0]").value("MONDAY"));
	}
}
