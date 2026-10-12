package com.pettracker.features.extraction;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class OcrServiceTest {
    @TempDir Path empty;
    @Test void reportsMissingLanguageDataWithoutCallingNativeEngine() {
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> new OcrService(empty.toString()).read(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB))).getStatusCode().value());
    }
    @Test void readsPrintedTextWithConfiguredNativeEngine() throws Exception {
        // Use Tess4J's bundled English model so a fresh checkout can run this test.
        try (var model = getClass().getResourceAsStream("/tessdata/eng.traineddata")) {
            assertNotNull(model);
            Files.copy(model, empty.resolve("eng.traineddata"));
        }
        var image = new BufferedImage(1400, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1400, 400);
        graphics.setColor(Color.BLACK); graphics.setFont(new Font("SansSerif", Font.PLAIN, 48));
        graphics.drawString("Happy Paws Clinic", 40, 90);
        graphics.drawString("Rabies", 40, 160);
        graphics.drawString("Administered: 2026-10-25", 40, 230);
        graphics.dispose();
        String text = new OcrService(empty.toString()).read(image);
        assertTrue(text.contains("Happy Paws Clinic"), text);
        assertEquals("RABIES", new InterpretationService().interpret(text).vaccineName());
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.drawImage(LosslessFactory.createFromImage(pdf, image), 30, 300, 560, 160);
            }
            pdf.save(bytes);
            String scannedText = new DocumentExtractionService(new OcrService(empty.toString())).extract(bytes.toByteArray());
            var fields = new InterpretationService().interpret(scannedText);
            assertEquals("Happy Paws Clinic", fields.clinicName(), scannedText);
            assertEquals(java.time.LocalDate.of(2026, 10, 25), fields.administeredOn(), scannedText);
        } finally { image.flush(); }
    }
}
