package com.pettracker.features.extraction;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import jakarta.servlet.http.HttpServletRequest;
import com.pettracker.features.auth.AuthTokenFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/certificates/extract")
public class ExtractionController {
    private final DocumentExtractionService documents;
    private final InterpretationService interpretation;
    private final Semaphore capacity = new Semaphore(2);
    public ExtractionController(DocumentExtractionService documents, InterpretationService interpretation) {
        this.documents = documents; this.interpretation = interpretation;
    }

    /** No filesystem or database writes: extraction is a preview before confirmation. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VaccinationExtractionDto extract(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        if (!(request.getAttribute(AuthTokenFilter.OWNER_ID) instanceof UUID))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty certificate up to 5 MB.");
        if (!capacity.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Extraction is busy. Please retry shortly.");
        try { return interpretation.interpret(documents.extract(file.getBytes())); }
        finally { capacity.release(); }
    }
}
