package com.pettracker.features.vets;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class vetService {
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_CACHE_ENTRIES = 512;
    // Three decimal places place nearby searches in cells roughly 100 m wide.
    private static final double CACHE_GRID = 1_000d;
    // Covers the farthest point of a cell from its rounded center (~79 m).
    private static final double CELL_PADDING_METRES = 100d;
    private final RestClient client;
    private final RestClient fallbackClient;
    private final Clock clock;
    private final ConcurrentHashMap<CacheKey, CachedResults> cache = new ConcurrentHashMap<>();

    @Autowired
    public vetService(@Value("${app.vets.overpass-url:https://overpass-api.de/api/interpreter}") String url,
            @Value("${app.vets.overpass-fallback-url:https://overpass.private.coffee/api/interpreter}") String fallbackUrl) {
        this.client = createClient(url);
        this.fallbackClient = fallbackUrl.isBlank() || fallbackUrl.equals(url) ? null : createClient(fallbackUrl);
        this.clock = Clock.systemUTC();
    }

    private static RestClient createClient(String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    vetService(RestClient client) {
        this(client, null, Clock.systemUTC());
    }

    vetService(RestClient client, Clock clock) {
        this(client, null, clock);
    }

    vetService(RestClient client, RestClient fallbackClient, Clock clock) {
        this.client = client;
        this.fallbackClient = fallbackClient;
        this.clock = clock;
    }

    public List<NearbyVet> getNearbyVets(double lat, double lon, double radiusKm) {
        if (!validCoordinates(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid latitude or longitude");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be greater than 0 and at most 50");
        }
        CacheKey key = new CacheKey(Math.round(lat * CACHE_GRID), Math.round(lon * CACHE_GRID), radiusKm);
        // The query is centered on the cell, not the first caller, so every point
        // in that cell can reuse the same result set without missing edge clinics.
        double queryLat = key.latitudeGrid() / CACHE_GRID;
        double queryLon = key.longitudeGrid() / CACHE_GRID;
        CachedResults cached = cache.get(key);
        if (cached == null || !isFresh(cached)) {
            if (cached == null && cache.size() >= MAX_CACHE_ENTRIES) {
                cache.entrySet().removeIf(entry -> !isFresh(entry.getValue()));
                if (cache.size() >= MAX_CACHE_ENTRIES) {
                    return forCaller(fetch(queryLat, queryLon, radiusKm), lat, lon, radiusKm);
                }
            }
            cached = cache.compute(key, (ignored, current) ->
                    current != null && isFresh(current)
                            ? current
                            : new CachedResults(fetch(queryLat, queryLon, radiusKm), clock.instant()));
        }
        return forCaller(cached.vets(), lat, lon, radiusKm);
    }

    private boolean isFresh(CachedResults entry) {
        return clock.instant().isBefore(entry.storedAt().plus(CACHE_TTL));
    }

    private List<NearbyVet> fetch(double queryLat, double queryLon, double radiusKm) {
        String query = String.format(Locale.ROOT,
                "[out:json][timeout:25];nwr[\"amenity\"=\"veterinary\"](around:%.3f,%.8f,%.8f);out center;",
                radiusKm * 1000 + CELL_PADDING_METRES, queryLat, queryLon);
        var body = new LinkedMultiValueMap<String, String>();
        body.add("data", query);
        OverpassResponse response;
        try {
            response = request(client, body);
        } catch (RestClientException | ResponseStatusException primaryFailure) {
            if (fallbackClient == null) {
                if (primaryFailure instanceof ResponseStatusException statusFailure) throw statusFailure;
                throw unavailable(primaryFailure);
            }
            try {
                response = request(fallbackClient, body);
            } catch (RestClientException | ResponseStatusException fallbackFailure) {
                var error = unavailable(fallbackFailure);
                error.addSuppressed(primaryFailure);
                throw error;
            }
        }
        return response.elements().stream().filter(Objects::nonNull)
                .map(element -> toNearbyVet(element, queryLat, queryLon))
                .filter(Objects::nonNull)
                .toList();
    }

    private OverpassResponse request(RestClient provider, LinkedMultiValueMap<String, String> body) {
        OverpassResponse response = provider.post().contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body).retrieve().body(OverpassResponse.class);
        // Overpass can report an incomplete query in a successful HTTP response.
        if (response == null || response.elements() == null ||
                (response.remark() != null && !response.remark().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vet search provider returned an incomplete response");
        }
        return response;
    }

    private static ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Veterinary clinic search is temporarily unavailable; please try again later", cause);
    }

    private List<NearbyVet> forCaller(List<NearbyVet> candidates, double lat, double lon, double radiusKm) {
        return candidates.stream()
                .map(vet -> new NearbyVet(vet.id(), vet.name(), vet.latitude(), vet.longitude(),
                        distanceKm(lat, lon, vet.latitude(), vet.longitude()), vet.address(), vet.mapUrl()))
                .filter(vet -> vet.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyVet::distanceKm)
                        .thenComparing(NearbyVet::name).thenComparing(NearbyVet::id))
                .toList();
    }

    private NearbyVet toNearbyVet(Element element, double lat, double lon) {
        Double vetLat = element.lat() != null ? element.lat() : element.center() == null ? null : element.center().lat();
        Double vetLon = element.lon() != null ? element.lon() : element.center() == null ? null : element.center().lon();
        if (vetLat == null || vetLon == null || !validCoordinates(vetLat, vetLon)
                || element.id() == null || !List.of("node", "way", "relation").contains(element.type() == null ? "" : element.type())) {
            return null;
        }
        Map<String, String> tags = element.tags() == null ? Map.of() : element.tags();
        String name = tags.get("name");
        if (name == null || name.isBlank()) name = tags.getOrDefault("name:en", "Veterinary clinic");
        String street = join(" ", tags.get("addr:housenumber"), tags.get("addr:street"));
        String address = tags.get("addr:full");
        if (address == null || address.isBlank()) {
            address = join(", ", street, tags.get("addr:suburb"), tags.get("addr:city"), tags.get("addr:postcode"));
        }
        String id = element.type() + "/" + element.id();
        return new NearbyVet(id, name, vetLat, vetLon, distanceKm(lat, lon, vetLat, vetLon),
                address, "https://www.openstreetmap.org/" + id);
    }

    private static String join(String delimiter, String... parts) {
        return Stream.of(parts).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(delimiter));
    }

    private static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }

    // Haversine distance, not road/travel distance. Keep full precision for sorting.
    private static double distanceKm(double lat, double lon, double vetLat, double vetLon) {
        double sinLat = Math.sin(Math.toRadians(vetLat - lat) / 2);
        double sinLon = Math.sin(Math.toRadians(vetLon - lon) / 2);
        double a = sinLat * sinLat + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(vetLat)) * sinLon * sinLon;
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
    }

    public record NearbyVet(String id, String name, double latitude, double longitude,
                            double distanceKm, String address, String mapUrl) {}

    private record CacheKey(long latitudeGrid, long longitudeGrid, double radiusKm) {}
    private record CachedResults(List<NearbyVet> vets, Instant storedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverpassResponse(List<Element> elements, String remark) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(String type, Long id, Double lat, Double lon, Center center, Map<String, String> tags) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Center(Double lat, Double lon) {}
}
