package com.soudan.ai.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.soudan.common.ai.KnowledgeDoc;

/**
 * Truy xuất theo từ khoá (BM25 đơn giản) trên các mẩu dữ liệu ĐÃ được lọc quyền bởi module sở hữu. Bỏ dấu tiếng Việt khi
 * so khớp để "bao cao" khớp "báo cáo"; chữ Nhật được tách theo cặp ký tự. Giao diện tách riêng để về sau thay bằng
 * truy xuất vector (embedding) mà không đụng phần còn lại.
 */
@Component
class Retriever {

	private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Set<String> STOP = Set.of("la", "va", "cua", "co", "khong", "toi", "cho", "nhung", "duoc", "trong",
			"nay", "do", "the", "nao", "gi", "da", "dang", "se", "voi", "mot", "cac", "nhu", "hay", "khi", "ve", "tu",
			"den", "bao", "nhieu", "tuan", "hom", "ai", "a", "an", "of", "and", "or", "to", "in", "is", "are", "what",
			"how", "did", "my", "me", "on", "for", "this", "that", "with", "was", "were");

	private final Clock clock;

	Retriever(Clock clock) {
		this.clock = clock;
	}

	record Scored(KnowledgeDoc doc, double score) {
	}

	/** Các mẩu liên quan nhất tới câu hỏi, tối đa {@code k}; ít hơn 4 khớp thì bù bằng các mẩu mới nhất. */
	List<KnowledgeDoc> top(String query, List<KnowledgeDoc> candidates, int k, int windowDays) {
		if (candidates.isEmpty() || k <= 0) {
			return List.of();
		}
		Set<String> terms = new HashSet<>(tokens(query));
		List<Map<String, Integer>> tf = new ArrayList<>();
		Map<String, Integer> df = new HashMap<>();
		for (KnowledgeDoc d : candidates) {
			Map<String, Integer> counts = new HashMap<>();
			tokens(d.title()).forEach(t -> counts.merge(t, 3, Integer::sum)); // trùng tiêu đề đáng giá hơn
			tokens(d.text()).forEach(t -> counts.merge(t, 1, Integer::sum));
			tf.add(counts);
			for (String t : counts.keySet()) {
				if (terms.contains(t)) {
					df.merge(t, 1, Integer::sum);
				}
			}
		}
		int n = candidates.size();
		Instant now = clock.instant();
		List<Scored> scored = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double score = 0;
			for (String t : terms) {
				Integer f = tf.get(i).get(t);
				if (f != null) {
					score += Math.log(1 + (double) n / (1 + df.getOrDefault(t, 0))) * (1 + Math.log(f));
				}
			}
			if (score > 0) {
				double ageDays = Math.max(0, Duration.between(candidates.get(i).at(), now).toHours() / 24.0);
				double recency = Math.max(0, 1 - ageDays / Math.max(windowDays, 1));
				scored.add(new Scored(candidates.get(i), score * (1 + 0.3 * recency)));
			}
		}
		scored.sort(Comparator.comparingDouble(Scored::score).reversed());
		List<KnowledgeDoc> result = new ArrayList<>();
		Set<KnowledgeDoc> chosen = new HashSet<>();
		for (Scored s : scored) {
			if (result.size() >= k) {
				break;
			}
			result.add(s.doc());
			chosen.add(s.doc());
		}
		// Câu hỏi chung chung ("tuần này nhóm làm gì") ít khớp từ khoá: bù bằng các mẩu mới nhất
		int minimum = Math.min(k, 4);
		List<KnowledgeDoc> recent = candidates.stream().sorted(Comparator.comparing(KnowledgeDoc::at).reversed()).toList();
		for (KnowledgeDoc d : recent) {
			if (result.size() >= minimum) {
				break;
			}
			if (chosen.add(d)) {
				result.add(d);
			}
		}
		return result;
	}

	/** Chữ thường, bỏ dấu, tách từ; chữ CJK tách theo cặp ký tự. Bỏ từ quá ngắn và từ dừng. */
	static List<String> tokens(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String folded = MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("")
				.replace('đ', 'd');
		List<String> out = new ArrayList<>();
		for (String word : NON_WORD.split(folded)) {
			if (word.isEmpty()) {
				continue;
			}
			if (hasCjk(word)) {
				for (int i = 0; i + 1 < word.length(); i++) {
					out.add(word.substring(i, i + 2));
				}
				if (word.length() == 1) {
					out.add(word);
				}
			} else if (word.length() >= 2 && !STOP.contains(word)) {
				out.add(word);
			}
		}
		return out;
	}

	private static boolean hasCjk(String s) {
		return s.codePoints().anyMatch(cp -> {
			Character.UnicodeScript script = Character.UnicodeScript.of(cp);
			return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
					|| script == Character.UnicodeScript.KATAKANA;
		});
	}
}
