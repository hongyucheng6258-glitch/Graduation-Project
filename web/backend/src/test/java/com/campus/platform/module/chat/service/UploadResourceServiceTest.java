package com.campus.platform.module.chat.service;


import com.campus.platform.common.BizException;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadResourceServiceTest {

    @Mock private UploadResourceMapper mapper;
    @InjectMocks private UploadResourceService service;

    @Test
    void imageAlreadyBoundToAnotherChatMessageCannotBeReused() {
        UploadResource resource = image(1L, 7L);
        resource.setBizType("chat_message");
        resource.setBizId(66L);
        when(mapper.findByUrl("https://cdn.example/a.jpg")).thenReturn(resource);

        assertThrows(BizException.class,
                () -> service.requireOwnedImage(7L, "https://cdn.example/a.jpg"));
    }

    @Test
    void unboundOwnedImageCanBeUsedForChat() {
        UploadResource resource = image(1L, 7L);
        when(mapper.findByUrl("https://cdn.example/a.jpg")).thenReturn(resource);

        assertDoesNotThrow(() -> service.requireOwnedImage(7L, "https://cdn.example/a.jpg"));
    }

    private UploadResource image(Long id, Long ownerId) {
        UploadResource resource = new UploadResource();
        resource.setId(id);
        resource.setOwnerUserId(ownerId);
        resource.setResourceType("image");
        return resource;
    }
}
