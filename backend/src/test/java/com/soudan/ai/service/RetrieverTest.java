package com.soudan.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.soudan.common.ai.KnowledgeDoc;

class RetrieverTest {

	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
	private final Retriever retriever = new Retriever(Clock.fixed(NOW, ZoneOffset.UTC));

	private static KnowledgeDoc doc(long id, String title, String text, Instant at) {
		return new KnowledgeDoc("REPORT", id, title, text, at, null, "/x");
	}

	@Test
	void should_matchWithoutDiacritics_when_queryHasNoAccents() {
		KnowledgeDoc payment = doc(1, "Báo cáo", "Hoàn thành module thanh toán", NOW);
		KnowledgeDoc other = doc(2, "Báo cáo", "Sửa lỗi giao diện đăng nhập", NOW);

		List<KnowledgeDoc> top = retriever.top("module thanh toan", List.of(other, payment), 1, 60);

		assertThat(top).containsExactly(payment);
	}

	@Test
	void should_rankTitleMatchAndRecent_higher() {
		KnowledgeDoc old = doc(1, "Ghi chú", "thanh toán", NOW.minusSeconds(60L * 86400));
		KnowledgeDoc titled = doc(2, "Thanh toán VNPay", "chi tiết", NOW);

		assertThat(retriever.top("thanh toán", List.of(old, titled), 2, 60)).first().isEqualTo(titled);
	}

	@Test
	void should_topUpWithRecent_when_queryMatchesLittle() {
		List<KnowledgeDoc> docs = List.of(doc(1, "a", "alpha", NOW.minusSeconds(300)), doc(2, "b", "beta", NOW),
				doc(3, "c", "gamma", NOW.minusSeconds(100)), doc(4, "d", "delta", NOW.minusSeconds(200)),
				doc(5, "e", "epsilon", NOW.minusSeconds(900)));

		List<KnowledgeDoc> top = retriever.top("tuần này nhóm làm gì", docs, 8, 60);

		assertThat(top).hasSize(4).first().extracting(KnowledgeDoc::id).isEqualTo(2L);
	}

	@Test
	void should_splitJapaneseIntoBigrams() {
		assertThat(Retriever.tokens("日報を提出")).contains("日報", "提出");
	}

	@Test
	void should_returnEmpty_when_noCandidates() {
		assertThat(retriever.top("x", List.of(), 5, 60)).isEmpty();
	}
}
