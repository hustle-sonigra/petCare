package com.pettracker.features.extraction;

import java.util.UUID;
import com.pettracker.features.auth.AuthTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtractionControllerTest {
    @Test void requiresAuthenticationAndOnlyReturnsSuggestions() throws Exception {
        var documents = mock(DocumentExtractionService.class);
        var controller = new ExtractionController(documents, new InterpretationService());
        var request = new MockHttpServletRequest();
        var file = new MockMultipartFile("file", "record.pdf", "application/pdf", "%PDF-test".getBytes());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller.extract(file, request)).getStatusCode().value());
        verifyNoInteractions(documents);
        request.setAttribute(AuthTokenFilter.OWNER_ID, UUID.randomUUID());
        when(documents.extract(any())).thenReturn("Clinic: Test Vet\nRabies\nAdministered: 2026-10-25");
        assertEquals("Test Vet", controller.extract(file, request).clinicName());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.extract(new MockMultipartFile("file", new byte[0]), request)).getStatusCode().value());
    }
}
