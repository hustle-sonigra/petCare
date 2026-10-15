package com.pettracker.features.vets;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vets")
public class VetController {
    private final vetService service;

    public VetController(vetService service) {
        this.service = service;
    }

    @GetMapping("/nearby")
    public List<vetService.NearbyVet> nearby(@RequestParam("lat") double lat,
            @RequestParam("lon") double lon, @RequestParam(defaultValue = "10", name = "radiusKm") double radiusKm) {
        return service.getNearbyVets(lat, lon, radiusKm);
    }
}
