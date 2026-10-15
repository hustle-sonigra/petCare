package com.pettracker.features.vets;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VetServiceTest {
    private MockRestServiceServer server;
    private vetService service;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://example.test/overpass");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new vetService(builder.build());
    }

    @Test
    void endpointReturnsNearestFirstWithDistancesAndSupportsWayCenters() throws Exception {
        server.expect(requestTo("https://example.test/overpass")).andRespond(withSuccess("""
                {"version":0.6,"elements":[
                  {"type":"node","id":1,"lat":0,"lon":0.02,"tags":{"name":"A Far"}},
                  {"type":"way","id":2,"center":{"lat":0,"lon":0.01},
                   "tags":{"name":"Z Near","addr:housenumber":"12","addr:street":"Main Street","addr:city":"Town"}},
                  {"type":"node","id":3,"lat":0,"lon":1},
                  {"type":"way","id":4},
                  {"type":"node","id":5,"lat":0,"lon":0}
                ]}
                """, MediaType.APPLICATION_JSON));
        MockMvcBuilders.standaloneSetup(new VetController(service)).build()
                .perform(get("/api/vets/nearby").param("lat", "0").param("lon", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$[0].name").value("Veterinary clinic"))
                .andExpect(jsonPath("$[1].id").value("way/2"))
                .andExpect(jsonPath("$[1].distanceKm").value(org.hamcrest.Matchers.closeTo(1.11195, 0.00001)))
                .andExpect(jsonPath("$[1].address").value("12 Main Street, Town"))
                .andExpect(jsonPath("$[1].mapUrl").value("https://www.openstreetmap.org/way/2"))
                .andExpect(jsonPath("$[2].id").value("node/1"));
        server.verify();
    }

    @Test
    void rejectsInvalidInputsWithoutCallingProvider() {
        for (double[] input : new double[][] {{91, 0, 10}, {0, 181, 10}, {Double.NaN, 0, 10},
                {0, Double.POSITIVE_INFINITY, 10}, {0, 0, 0}, {0, 0, 51}, {0, 0, Double.NaN}}) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> service.getNearbyVets(input[0], input[1], input[2]));
            assertEquals(400, error.getStatusCode().value());
        }
        server.verify();
    }

    @Test
    void returnsEmptyListWhenNoVetsFound() {
        server.expect(anything()).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(service.getNearbyVets(0, 0, 10).isEmpty());
        assertTrue(service.getNearbyVets(0, 0.0002, 10).isEmpty());
        server.verify(); // A valid empty result is cached for the area.
    }

    @Test
    void providerFailureReturnsServiceUnavailable() {
        server.expect(anything()).andRespond(withServerError());
        server.expect(anything()).andRespond(withServerError());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.getNearbyVets(0, 0, 10)).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.getNearbyVets(0, 0, 10)).getStatusCode().value());
        server.verify(); // Failed provider responses are never cached.
    }

    @Test
    void primaryFailureUsesFallbackAndCachesItsResults() {
        var fallbackBuilder = RestClient.builder().baseUrl("https://fallback.test/overpass");
        var fallbackServer = MockRestServiceServer.bindTo(fallbackBuilder).build();
        var primaryBuilder = RestClient.builder().baseUrl("https://example.test/overpass");
        var primaryServer = MockRestServiceServer.bindTo(primaryBuilder).build();
        var serviceWithFallback = new vetService(primaryBuilder.build(), fallbackBuilder.build(), Clock.systemUTC());
        primaryServer.expect(requestTo("https://example.test/overpass")).andRespond(withServerError());
        fallbackServer.expect(requestTo("https://fallback.test/overpass")).andRespond(withSuccess("""
                {"elements":[{"type":"node","id":12,"lat":0,"lon":0.01,"tags":{"name":"Fallback Vet"}}]}
                """, MediaType.APPLICATION_JSON));

        assertEquals("Fallback Vet", serviceWithFallback.getNearbyVets(0, 0, 10).getFirst().name());
        assertEquals("Fallback Vet", serviceWithFallback.getNearbyVets(0, 0.0002, 10).getFirst().name());
        primaryServer.verify();
        fallbackServer.verify();
    }

    @Test
    void healthyPrimaryDoesNotCallFallback() {
        var fallbackBuilder = RestClient.builder().baseUrl("https://fallback.test/overpass");
        var fallbackServer = MockRestServiceServer.bindTo(fallbackBuilder).build();
        var primaryBuilder = RestClient.builder().baseUrl("https://example.test/overpass");
        var primaryServer = MockRestServiceServer.bindTo(primaryBuilder).build();
        var serviceWithFallback = new vetService(primaryBuilder.build(), fallbackBuilder.build(), Clock.systemUTC());
        primaryServer.expect(anything()).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(serviceWithFallback.getNearbyVets(0, 0, 10).isEmpty());
        primaryServer.verify();
        fallbackServer.verify();
    }

    @Test
    void bothProvidersFailWithServiceUnavailable() {
        var fallbackBuilder = RestClient.builder().baseUrl("https://fallback.test/overpass");
        var fallbackServer = MockRestServiceServer.bindTo(fallbackBuilder).build();
        var primaryBuilder = RestClient.builder().baseUrl("https://example.test/overpass");
        var primaryServer = MockRestServiceServer.bindTo(primaryBuilder).build();
        var serviceWithFallback = new vetService(primaryBuilder.build(), fallbackBuilder.build(), Clock.systemUTC());
        primaryServer.expect(anything()).andRespond(withServerError());
        fallbackServer.expect(anything()).andRespond(withServerError());

        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> serviceWithFallback.getNearbyVets(0, 0, 10)).getStatusCode().value());
        primaryServer.verify();
        fallbackServer.verify();
    }

    @Test
    void incompletePrimaryResponseUsesFallback() {
        var fallbackBuilder = RestClient.builder().baseUrl("https://fallback.test/overpass");
        var fallbackServer = MockRestServiceServer.bindTo(fallbackBuilder).build();
        var primaryBuilder = RestClient.builder().baseUrl("https://example.test/overpass");
        var primaryServer = MockRestServiceServer.bindTo(primaryBuilder).build();
        var serviceWithFallback = new vetService(primaryBuilder.build(), fallbackBuilder.build(), Clock.systemUTC());
        primaryServer.expect(anything()).andRespond(withSuccess(
                "{\"elements\":[],\"remark\":\"runtime error: Query timed out\"}", MediaType.APPLICATION_JSON));
        fallbackServer.expect(anything()).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(serviceWithFallback.getNearbyVets(0, 0, 10).isEmpty());
        primaryServer.verify();
        fallbackServer.verify();
    }

    @Test
    void incompleteProviderResultsAreNotReturnedAsSuccessfulSearch() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"elements\":[],\"remark\":\"runtime error: Query timed out\"}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> service.getNearbyVets(0, 0, 10)).getStatusCode().value());
    }

    @Test
    void nearbyCallersReuseOneSearchAndGetTheirOwnDistancesAndOrder() {
        server.expect(requestTo("https://example.test/overpass")).andRespond(withSuccess("""
                {"elements":[
                  {"type":"node","id":1,"lat":0,"lon":-0.003,"tags":{"name":"West Vet"}},
                  {"type":"node","id":2,"lat":0,"lon":0.003,"tags":{"name":"East Vet"}}
                ]}
                """, MediaType.APPLICATION_JSON));

        var west = service.getNearbyVets(0, -0.0004, 1);
        var east = service.getNearbyVets(0, 0.0004, 1);

        assertEquals("West Vet", west.getFirst().name());
        assertEquals("East Vet", east.getFirst().name());
        assertTrue(east.getFirst().distanceKm() < west.getLast().distanceKm());
        server.verify(); // One Overpass call for both positions in the same cell.
    }

    @Test
    void cachedCandidatesAreFilteredForEachCallerAtTheRadiusEdge() {
        server.expect(requestTo("https://example.test/overpass")).andRespond(withSuccess("""
                {"elements":[{"type":"node","id":7,"lat":0,"lon":0.0092,"tags":{"name":"Edge Vet"}}]}
                """, MediaType.APPLICATION_JSON));

        assertTrue(service.getNearbyVets(0, -0.0004, 1).isEmpty());
        assertEquals("Edge Vet", service.getNearbyVets(0, 0.0004, 1).getFirst().name());
        server.verify();
    }

    @Test
    void expiredEntryFetchesAgainAfterSixHours() {
        var builder = RestClient.builder().baseUrl("https://example.test/overpass");
        var timedServer = MockRestServiceServer.bindTo(builder).build();
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var timedService = new vetService(builder.build(), clock);
        timedServer.expect(anything()).andRespond(withSuccess("""
                {"elements":[{"type":"node","id":1,"lat":0,"lon":0,"tags":{"name":"Original"}}]}
                """, MediaType.APPLICATION_JSON));
        timedServer.expect(anything()).andRespond(withSuccess("""
                {"elements":[{"type":"node","id":1,"lat":0,"lon":0,"tags":{"name":"Updated"}}]}
                """, MediaType.APPLICATION_JSON));

        assertEquals("Original", timedService.getNearbyVets(0, 0, 1).getFirst().name());
        clock.advance(Duration.ofHours(6).minusNanos(1));
        assertEquals("Original", timedService.getNearbyVets(0, 0, 1).getFirst().name());
        clock.advance(Duration.ofNanos(1));
        assertEquals("Updated", timedService.getNearbyVets(0, 0, 1).getFirst().name());
        timedServer.verify();
    }

    @Test
    void anotherAreaOrRadiusUsesAnotherCacheEntry() {
        server.expect(anything()).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));

        service.getNearbyVets(0, 0, 1);
        service.getNearbyVets(0.002, 0, 1);
        service.getNearbyVets(0, 0, 2);
        server.verify();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }
        private void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
