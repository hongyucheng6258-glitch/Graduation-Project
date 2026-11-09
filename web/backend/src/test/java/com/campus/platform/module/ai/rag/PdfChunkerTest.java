package com.campus.platform.module.ai.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfChunkerTest {
    @Test
    void findsTextBeyondOldCutoffAndKeepsPdfPageNumbers() throws Exception {
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            addPage(pdf, "general campus information ".repeat(360));
            addPage(pdf, "The makerspace access key is Lantern-Zeta-52.");
            pdf.save(bytes);
            var parts = new PdfChunker().split(bytes.toByteArray());
            assertThat(parts).allSatisfy(part -> assertThat(part.text()).hasSizeLessThanOrEqualTo(640));
            assertThat(parts.stream().filter(part -> part.page() == 1).mapToInt(part -> part.text().length()).sum())
                    .isGreaterThan(8000);
            assertThat(parts).anySatisfy(part -> {
                assertThat(part.page()).isEqualTo(2);
                assertThat(part.text()).contains("Lantern-Zeta-52");
            });
        }
    }

    private void addPage(PDDocument pdf, String text) throws Exception {
        var page = new PDPage();
        pdf.addPage(page);
        try (var writer = new PDPageContentStream(pdf, page)) {
            writer.beginText();
            writer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
            writer.setLeading(5);
            writer.newLineAtOffset(10, 775);
            for (int i = 0; i < text.length(); i += 70) {
                writer.showText(text.substring(i, Math.min(i + 70, text.length())));
                writer.newLine();
            }
            writer.endText();
        }
    }
}
