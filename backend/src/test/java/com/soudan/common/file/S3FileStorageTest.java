package com.soudan.common.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3FileStorage với S3Client giả: kiểm tra tên đối tượng, giới hạn dung lượng, ánh xạ lỗi và chống khoá giả. */
class S3FileStorageTest {

	private S3Client s3;
	private S3FileStorage storage;

	@BeforeEach
	void setUp() {
		s3 = mock(S3Client.class);
		storage = new S3FileStorage(s3, new StorageProperties("", DataSize.ofBytes(10), "s3", "demo-bucket", "ap-southeast-1"));
	}

	@Test
	void should_putUnderUploadsPrefixWithRandomUuidKey() throws IOException {
		String key = storage.save(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

		assertThat(UUID.fromString(key)).isNotNull(); // khoá trả về là UUID trần, không có tiền tố
		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
		verify(s3).putObject(request.capture(), body.capture());
		assertThat(request.getValue().bucket()).isEqualTo("demo-bucket");
		assertThat(request.getValue().key()).isEqualTo("uploads/" + key);
		assertThat(body.getValue().optionalContentLength()).hasValue(5L);
	}

	@Test
	void should_refuseContentOverTheLimit_withoutCallingS3() {
		assertThatThrownBy(() -> storage.save(new ByteArrayInputStream(new byte[11]))).isInstanceOf(IOException.class);

		verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}

	@Test
	void should_wrapSdkFailureOnSave() {
		when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(AwsServiceException.builder().message("boom").build());

		assertThatThrownBy(() -> storage.save(new ByteArrayInputStream(new byte[1]))).isInstanceOf(IOException.class);
	}

	@Test
	void should_loadContentByKey() throws IOException {
		String key = UUID.randomUUID().toString();
		when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
				AbortableInputStream.create(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)))));

		try (InputStream in = storage.load(key)) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("data");
		}
		ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
		verify(s3).getObject(request.capture());
		assertThat(request.getValue().key()).isEqualTo("uploads/" + key);
	}

	@Test
	void should_reportMissingObjectAsFileNotFound() {
		when(s3.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("none").build());

		assertThatThrownBy(() -> storage.load(UUID.randomUUID().toString())).isInstanceOf(FileNotFoundException.class);
	}

	@Test
	void should_deleteByPrefixedKey() throws IOException {
		String key = UUID.randomUUID().toString();

		storage.delete(key);

		ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3).deleteObject(request.capture());
		assertThat(request.getValue().key()).isEqualTo("uploads/" + key);
	}

	@Test
	void should_rejectKeysThatAreNotUuids() {
		for (String bad : new String[] { "../secret", "uploads/other", "", "not-a-uuid" }) {
			assertThatThrownBy(() -> storage.load(bad)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> storage.delete(bad)).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> storage.load(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
