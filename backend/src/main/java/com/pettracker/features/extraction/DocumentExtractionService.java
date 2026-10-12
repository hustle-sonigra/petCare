package com.pettracker.features.extraction;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.pettracker.features.attachments.CertificateFileType;

@Service
public class DocumentExtractionService {
    private static final long MAX_PIXELS = 20_000_000;
    private final OcrService ocr;
    public DocumentExtractionService(OcrService ocr) { this.ocr = ocr; }

    public String extract(byte[] bytes) {
        String type = CertificateFileType.detect(bytes);
        try {
            if (!type.equals("application/pdf")) return imageText(bytes);
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                if (pdf.isEncrypted()) throw bad("Password-protected PDFs are not supported.");
                if (pdf.getNumberOfPages() == 0 || pdf.getNumberOfPages() > 10) throw bad("Choose a certificate with 1–10 pages.");
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                PDFRenderer renderer = new PDFRenderer(pdf);
                StringBuilder text = new StringBuilder();
                for (int page = 0; page < pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page + 1);
                    stripper.setEndPage(page + 1);
                    String digital = stripper.getText(pdf);
                    // Some scans contain only a digital heading. OCR the complete rendered page.
                    boolean useful = digital.codePoints().filter(Character::isLetterOrDigit).count() >= 40
                            && digital.indexOf('\uFFFD') < 0;
                    if (useful) text.append(digital);
                    if (!useful || hasImageLayer(pdf.getPage(page).getResources(), 0)) {
                        var box = pdf.getPage(page).getCropBox();
                        checkPixels((long) Math.ceil(box.getWidth() * 300 / 72), (long) Math.ceil(box.getHeight() * 300 / 72));
                        BufferedImage image = renderer.renderImageWithDPI(page, 300);
                        try { text.append('\n').append(ocr.read(image)); } finally { image.flush(); }
                    }
                    text.append('\n');
                    if (text.length() > 200_000) throw bad("Certificate contains too much text.");
                }
                return text.toString();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The certificate could not be read. Choose another file or enter details manually.", e);
        }
    }

    private boolean hasImageLayer(PDResources resources, int depth) throws IOException {
        if (resources == null || depth > 5) return false;
        for (var name : resources.getXObjectNames()) {
            var object = resources.getXObject(name);
            // Skip tiny decorative images; substantial raster layers may contain medical fields.
            if (object instanceof PDImageXObject image && (long) image.getWidth() * image.getHeight() >= 100_000) return true;
            if (object instanceof PDFormXObject form && hasImageLayer(form.getResources(), depth + 1)) return true;
        }
        return false;
    }

    private String imageText(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("The image is not readable.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                checkPixels(reader.getWidth(0), reader.getHeight(0));
                BufferedImage image = reader.read(0);
                try { return ocr.read(image); } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }
    private void checkPixels(long width, long height) {
        if (width <= 0 || height <= 0 || width > MAX_PIXELS || height > MAX_PIXELS || width * height > MAX_PIXELS)
            throw bad("Page dimensions are too large to process.");
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
