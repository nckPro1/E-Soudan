package com.soudan.common.file;

import static org.assertj.core.api.Assertions.assertThat;

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
}
