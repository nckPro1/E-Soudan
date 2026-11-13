package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;

/** Lịch cá nhân: mọi vai trò dùng được, mỗi người chỉ thấy và sửa ghi chú của mình. */
class CalendarApiIntegrationTest extends IntegrationTestBase {

	private String annToken;
	private String bobToken;
	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		createUser("ann@example.com", SystemRole.USER);
		createUser("bob@example.com", SystemRole.MANAGER);
		adminToken = accessTokenOf("admin@example.com");
		annToken = accessTokenOf("ann@example.com");
		bobToken = accessTokenOf("bob@example.com");
	}

	private ResultActions create(String token, String date, String title, String color) throws Exception {
		return mockMvc.perform(post("/api/v1/calendar/notes").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\":\"%s\",\"title\":\"%s\",\"content\":\"nội dung\",\"color\":%s}".formatted(date, title,
						color == null ? "null" : "\"" + color + "\"")));
	}

	private long createId(String token, String date, String title) throws Exception {
		String body = create(token, date, title, "RED").andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return ((Number) JsonPath.read(body, "$.data.id")).longValue();
	}

	@Test
	void should_createAndListNotesInRange_when_anyRoleIncludingAdmin() throws Exception {
		create(annToken, "2026-11-13", "Nộp báo cáo", "RED").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.color").value("RED"));
		create(adminToken, "2026-11-20", "Họp", null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.color").value("INDIGO"));

		mockMvc.perform(get("/api/v1/calendar/notes?from=2026-11-01&to=2026-11-30")
				.header("Authorization", bearer(annToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].title").value("Nộp báo cáo"));
	}

	@Test
	void should_notShowOrChangeOthersNotes_when_differentUser() throws Exception {
		long id = createId(annToken, "2026-11-13", "Riêng tư");

		mockMvc.perform(get("/api/v1/calendar/notes?from=2026-11-01&to=2026-11-30")
				.header("Authorization", bearer(bobToken)))
				.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(put("/api/v1/calendar/notes/" + id).header("Authorization", bearer(bobToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\":\"2026-11-13\",\"title\":\"Sửa trộm\"}")).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/calendar/notes/" + id).header("Authorization", bearer(bobToken)))
				.andExpect(status().isNotFound());
	}

	@Test
	void should_updateAndDelete_when_owner() throws Exception {
		long id = createId(annToken, "2026-11-13", "Cũ");

		mockMvc.perform(put("/api/v1/calendar/notes/" + id).header("Authorization", bearer(annToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\":\"2026-11-14\",\"title\":\"Mới\",\"color\":\"GREEN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("Mới"))
				.andExpect(jsonPath("$.data.date").value("2026-11-14"));
		mockMvc.perform(delete("/api/v1/calendar/notes/" + id).header("Authorization", bearer(annToken)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/calendar/notes?from=2026-11-01&to=2026-11-30")
				.header("Authorization", bearer(annToken)))
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void should_reject_when_titleBlankColorUnknownOrRangeInvalid() throws Exception {
		create(annToken, "2026-11-13", "   ", "RED").andExpect(status().isUnprocessableEntity());
		create(annToken, "2026-11-13", "Ok", "PINK").andExpect(status().isUnprocessableEntity());
		mockMvc.perform(get("/api/v1/calendar/notes?from=2026-11-30&to=2026-11-01")
				.header("Authorization", bearer(annToken))).andExpect(status().isUnprocessableEntity());
		mockMvc.perform(get("/api/v1/calendar/notes?from=2026-01-01&to=2026-12-31")
				.header("Authorization", bearer(annToken))).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void should_limitNotesPerDay() throws Exception {
		for (int i = 0; i < 20; i++) {
			create(annToken, "2026-11-13", "n" + i, "BLUE").andExpect(status().isCreated());
		}
		create(annToken, "2026-11-13", "quá nhiều", "BLUE").andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("CALENDAR_TOO_MANY_NOTES"));
	}

	@Test
	void should_requireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/calendar/notes?from=2026-11-01&to=2026-11-30")).andExpect(status().isUnauthorized());
	}
}
