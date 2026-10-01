package com.nbamanager.util;

import com.nbamanager.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public final class ImageUploadValidator {

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    private ImageUploadValidator() {
    }

    public static String validateImage(MultipartFile file, long maxFileSize, String label, String maxSizeLabel) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, label + "大小不能超过 " + maxSizeLabel);
        }

        String extension = extensionOf(file.getOriginalFilename());
        String expectedMime = MIME_BY_EXTENSION.get(extension);
        if (expectedMime == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WEBP、GIF 格式");
        }

        String contentType = file.getContentType();
        if (contentType == null || !expectedMime.equalsIgnoreCase(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "文件类型不合法");
        }

        try {
            if (!magicBytesMatch(file, extension)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "文件内容与图片格式不符");
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "读取失败");
        }

        return extension;
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean magicBytesMatch(MultipartFile file, String extension) throws IOException {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(head);
        }

        return switch (extension) {
            case "jpg", "jpeg" -> read >= 3
                    && (head[0] & 0xFF) == 0xFF
                    && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            case "png" -> read >= 8
                    && (head[0] & 0xFF) == 0x89
                    && head[1] == 'P'
                    && head[2] == 'N'
                    && head[3] == 'G'
                    && head[4] == 0x0D
                    && head[5] == 0x0A
                    && head[6] == 0x1A
                    && head[7] == 0x0A;
            case "gif" -> read >= 6
                    && head[0] == 'G'
                    && head[1] == 'I'
                    && head[2] == 'F'
                    && head[3] == '8';
            case "webp" -> read >= 12
                    && head[0] == 'R'
                    && head[1] == 'I'
                    && head[2] == 'F'
                    && head[3] == 'F'
                    && head[8] == 'W'
                    && head[9] == 'E'
                    && head[10] == 'B'
                    && head[11] == 'P';
            default -> false;
        };
    }
}
