package com.campus.platform.utils;

import com.campus.platform.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataUriUtilsTest {

    private final DataUriUtils dataUriUtils = new DataUriUtils();

    @Test
    void encodesContentAndMimeTypeAsDataUri() {
        byte[] content = "png-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "demo.png", "image/png", content);

        String dataUri = dataUriUtils.toDataUri(file);

        assertTrue(dataUri.startsWith("data:image/png;base64,"));
        byte[] decoded = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
        assertArrayEquals(content, decoded);
    }

    @Test
    void usesBinaryMimeTypeWhenContentTypeIsMissing() {
        MockMultipartFile file = new MockMultipartFile("file", "demo.bin", null, new byte[]{1, 2, 3});

        String dataUri = dataUriUtils.toDataUri(file);

        assertEquals("data:application/octet-stream;base64,AQID", dataUri);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.bin", "application/octet-stream", new byte[0]);

        assertThrows(BizException.class, () -> dataUriUtils.toDataUri(file));
    }
}
