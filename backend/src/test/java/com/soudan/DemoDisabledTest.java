package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Mặc định (và ở production) tài khoản demo TẮT: endpoint công khai không lộ email hay mật khẩu. */
class DemoDisabledTest extends IntegrationTestBase {

	@Test
	void should_notExposeAnythingWhenDemoDisabled() throws Exception {
		mockMvc.perform(get("/api/v1/demo/account")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.email").doesNotExist())
				.andExpect(jsonPath("$.data.password").doesNotExist());
	}
}
