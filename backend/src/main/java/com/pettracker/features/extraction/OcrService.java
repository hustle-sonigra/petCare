package com.pettracker.features.extraction;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OcrService {
    private final String dataPath;
    public OcrService(@Value("${app.extraction.tessdata-dir:./tessdata}") String dataPath) { this.dataPath = dataPath; }

    public String read(BufferedImage image) {
        if (!Files.isRegularFile(Path.of(dataPath, "eng.traineddata")))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR language data is unavailable. Enter the certificate details manually.");
        try {
            // Native engine instances are not shared between concurrent requests.
            Tesseract engine = new Tesseract();
            engine.setDatapath(dataPath);
            engine.setLanguage("eng");
            return engine.doOCR(image);
        } catch (TesseractException | LinkageError e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR could not process this document. Enter the details manually.", e);
        }
    }
}
