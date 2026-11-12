package com.soudan.common.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Lưu file trên ổ đĩa. Tên file trên đĩa là UUID ngẫu nhiên; mọi khoá nhận từ ngoài đều phải đúng dạng UUID
 * nên không thể dùng "../" để thoát khỏi thư mục gốc.
 */
@Component
public class LocalFileStorage implements FileStorage {

	private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private final Path root;

	public LocalFileStorage(StorageProperties properties) throws IOException {
		this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
		Files.createDirectories(root);
	}

	@Override
	public String save(InputStream content) throws IOException {
		String key = UUID.randomUUID().toString();
		Files.copy(content, resolve(key));
		return key;
	}

	@Override
	public InputStream load(String key) throws IOException {
		Path path = resolve(key);
		if (!Files.isRegularFile(path)) {
			throw new FileNotFoundException(key);
		}
		return Files.newInputStream(path);
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolve(key));
	}

	private Path resolve(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Khoá file không hợp lệ");
		}
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Khoá file không hợp lệ");
		}
		return path;
	}
}
