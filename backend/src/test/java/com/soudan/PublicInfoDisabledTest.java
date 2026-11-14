package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Mặc định máy chạy cả ngày: không có giờ hoạt động nào để hiển thị. */
class PublicInfoDisabledTest extends IntegrationTestBase {

	@Test
	void should_returnNoOperatingHours_byDefault() throws Exception {
		mockMvc.perform(get("/api/v1/public/info")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.operatingHours").doesNotExist());
	}
}
