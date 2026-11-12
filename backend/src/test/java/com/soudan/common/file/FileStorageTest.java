package com.soudan.common.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

class FileStorageTest {

	private static final byte[] PDF = { '%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0, 0, 0, 0, 0 };
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0 };
	private static final byte[] ZIP = { 'P', 'K', 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private static final byte[] EXE = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0 };

	@TempDir
	Path dir;

	private LocalFileStorage storage;
	private UploadPolicy policy;

	@BeforeEach
	void setUp() throws IOException {
		StorageProperties props = new StorageProperties(dir.toString(), DataSize.ofMegabytes(10));
		storage = new LocalFileStorage(props);
		policy = new UploadPolicy(props);
	}

	// ---- LocalFileStorage ----

	@Test
	void should_readBackSameBytes_when_saved() throws IOException {
		String key = storage.save(new ByteArrayInputStream(PDF));

		try (var in = storage.load(key)) {
			assertThat(in.readAllBytes()).isEqualTo(PDF);
		}
		assertThat(Files.list(dir).toList()).hasSize(1);
	}

	@Test
	void should_useRandomKeyNotUserName() throws IOException {
		String a = storage.save(new ByteArrayInputStream(PDF));
		String b = storage.save(new ByteArrayInputStream(PDF));

		assertThat(a).isNotEqualTo(b).matches("[0-9a-f-]{36}");
	}

	@Test
	void should_rejectTraversalKeys() {
		for (String key : new String[] { "../secret", "..\\secret", "/etc/passwd", "a/b", "", "x" }) {
			assertThatThrownBy(() -> storage.load(key)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void should_failWithNotFound_when_keyUnknown() {
		assertThatThrownBy(() -> storage.load("00000000-0000-0000-0000-000000000000"))
				.isInstanceOf(FileNotFoundException.class);
	}

	@Test
	void should_deleteFile_andNotFailWhenAlreadyGone() throws IOException {
		String key = storage.save(new ByteArrayInputStream(PDF));

		storage.delete(key);
		storage.delete(key);

		assertThat(Files.list(dir).toList()).isEmpty();
	}

	// ---- UploadPolicy ----

	private ErrorCode codeOf(String name, long size, byte[] head) {
		try {
			policy.validate(name, size, head);
		} catch (BusinessException e) {
			return e.getCode();
		}
		return null;
	}

	@Test
	void should_accept_when_extensionMatchesContent() {
		assertThat(codeOf("report.pdf", 100, PDF)).isNull();
		assertThat(codeOf("photo.PNG", 100, PNG)).isNull();
		assertThat(codeOf("sheet.xlsx", 100, ZIP)).isNull();
		assertThat(codeOf("doc.docx", 100, ZIP)).isNull();
		assertThat(codeOf("all.zip", 100, ZIP)).isNull();
	}

	@Test
	void should_reject_when_extensionNotAllowed() {
		assertThat(codeOf("run.exe", 100, EXE)).isEqualTo(ErrorCode.INVALID_FILE);
		assertThat(codeOf("noext", 100, PDF)).isEqualTo(ErrorCode.INVALID_FILE);
		assertThat(codeOf("page.html", 100, PDF)).isEqualTo(ErrorCode.INVALID_FILE);
	}

	@Test
	void should_reject_when_contentDoesNotMatchExtension() {
		assertThat(codeOf("fake.pdf", 100, EXE)).isEqualTo(ErrorCode.INVALID_FILE);
		assertThat(codeOf("fake.png", 100, PDF)).isEqualTo(ErrorCode.INVALID_FILE);
		assertThat(codeOf("fake.docx", 100, PDF)).isEqualTo(ErrorCode.INVALID_FILE);
	}

	@Test
	void should_reject_when_emptyOrTooLarge() {
		assertThat(codeOf("a.pdf", 0, PDF)).isEqualTo(ErrorCode.INVALID_FILE);
		assertThat(codeOf("a.pdf", 10L * 1024 * 1024, PDF)).isNull();
		assertThat(codeOf("a.pdf", 10L * 1024 * 1024 + 1, PDF)).isEqualTo(ErrorCode.FILE_TOO_LARGE);
	}

	@Test
	void should_stripPathAndControlCharsFromName() {
		assertThat(policy.safeName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
		assertThat(policy.safeName("C:\\Users\\x\\báo cáo.pdf")).isEqualTo("báo cáo.pdf");
		assertThat(policy.safeName("a\r\nb\".pdf")).isEqualTo("ab.pdf");
		assertThat(policy.safeName("   ")).isEqualTo("file");
		assertThat(policy.safeName(null)).isEqualTo("file");
	}

	@Test
	void should_deriveContentTypeFromExtension() {
		assertThat(policy.contentTypeOf("a.pdf")).isEqualTo("application/pdf");
		assertThat(policy.contentTypeOf("a.exe")).isEqualTo("application/octet-stream");
	}
}
