package com.pettracker.features.extraction;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentExtractionServiceTest {
    private final OcrService ocr = mock(OcrService.class);
    private final DocumentExtractionService service = new DocumentExtractionService(ocr);

    @Test void readsDigitalTextWithoutOcr() throws Exception {
        String result = service.extract(pdf(true, false));
        assertTrue(result.contains("Happy Paws Clinic"));
        verifyNoInteractions(ocr);
    }
    @Test void routesBlankAndMixedPagesToOcr() throws Exception {
        when(ocr.read(any())).thenReturn("Rabies\nAdministered: 2026-10-05");
        assertTrue(service.extract(pdf(false, false)).contains("Rabies"));
        String mixed = service.extract(pdf(true, true));
        assertTrue(mixed.contains("Happy Paws Clinic"));
        assertTrue(mixed.contains("Rabies"));
        verify(ocr, times(2)).read(any());
    }
    @Test void routesImagesBySignatureAndRejectsInvalidContent() throws Exception {
        when(ocr.read(any())).thenReturn("Clinic: Test");
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "png", output);
        assertEquals("Clinic: Test", service.extract(output.toByteArray()));
        assertEquals(415, assertThrows(ResponseStatusException.class, () -> service.extract("fake.pdf".getBytes())).getStatusCode().value());
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.extract("%PDF-broken".getBytes())).getStatusCode().value());
    }
    @Test void rejectsOversizedPageCounts() throws Exception {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < 11; i++) pdf.addPage(new PDPage());
            pdf.save(output);
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.extract(output.toByteArray())).getStatusCode().value());
            verifyNoInteractions(ocr);
        }
    }
    private byte[] pdf(boolean digital, boolean raster) throws Exception {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var stream = new PDPageContentStream(pdf, page)) {
                if (digital) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText("Happy Paws Clinic vaccination certificate medical record");
                    stream.endText();
                }
                if (raster) stream.drawImage(LosslessFactory.createFromImage(pdf, new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB)), 50, 100, 200, 200);
            }
            pdf.save(output); return output.toByteArray();
        }
    }
}
