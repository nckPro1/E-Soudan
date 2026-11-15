package com.soudan.common.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class StoragePropertiesTest {

	@Test
	void should_useRealTmpDirNotLiteralPlaceholder_when_dirNotConfigured() {
		StorageProperties props = new StorageProperties("", DataSize.ofMegabytes(10));

		assertThat(props.dir()).doesNotContain("${").endsWith("soudan-uploads");
		assertThat(new StorageProperties(null, DataSize.ofMegabytes(10)).dir()).doesNotContain("${");
	}

	@Test
	void should_keepConfiguredDir() {
		assertThat(new StorageProperties("/data/uploads", DataSize.ofMegabytes(10)).dir()).isEqualTo("/data/uploads");
	}

	@Test
	void should_failFast_when_typeUnknownOrS3IsIncomplete() {
		assertThatThrownBy(() -> new StorageProperties("", DataSize.ofMegabytes(10), "ftp", "", "")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new StorageProperties("", DataSize.ofMegabytes(10), "s3", "", "ap-southeast-1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new StorageProperties("", DataSize.ofMegabytes(10), "s3", "bucket", ""))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void should_acceptCompleteS3Config() {
		StorageProperties props = new StorageProperties("", DataSize.ofMegabytes(10), "s3", "bucket", "ap-southeast-1");

		assertThat(props.type()).isEqualTo("s3");
		assertThat(props.s3Bucket()).isEqualTo("bucket");
	}
}
