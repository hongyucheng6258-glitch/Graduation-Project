package com.campus.platform.module.drawgame.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrawGuessStrokePolicyTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsBoundedNormalizedStrokeAndDropsClientEventFields() throws Exception {
        var payload = objectMapper.readTree("""
                {"points":[{"x":0.1,"y":0.2},{"x":0.8,"y":0.9}],"color":"#3456ab","width":5,"tool":"pen","userId":999}
                """);

        var stroke = DrawGuessStrokePolicy.validate(payload);

        assertEquals("#3456ab", stroke.get("color"));
        assertEquals(2, ((java.util.List<?>) stroke.get("points")).size());
        assertFalse(stroke.containsKey("userId"));
    }

    @Test
    void rejectsOversizedOutOfBoundsOrUnsafeStroke() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> DrawGuessStrokePolicy.validate(objectMapper.readTree("""
                        {"points":[{"x":-0.1,"y":0.2}],"color":"#3456ab","width":5,"tool":"pen"}
                        """)));
        assertThrows(IllegalArgumentException.class,
                () -> DrawGuessStrokePolicy.validate(objectMapper.readTree("""
                        {"points":[{"x":0.1,"y":0.2}],"color":"red;url(x)","width":5,"tool":"pen"}
                        """)));
        assertThrows(IllegalArgumentException.class,
                () -> DrawGuessStrokePolicy.validate(objectMapper.readTree("""
                        {"points":[{"x":0.1,"y":0.2}],"color":"#3456ab","width":33,"tool":"pen"}
                        """)));
        assertThrows(IllegalArgumentException.class,
                () -> DrawGuessStrokePolicy.validate(objectMapper.readTree("""
                        {"points":[{"x":0.1,"y":0.2}],"color":"#3456ab","width":5,"tool":"script"}
                        """)));
    }
}
