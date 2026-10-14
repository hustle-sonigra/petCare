package com.pettracker.features.vaccinations;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.pettracker.features.pets.PetController;
import com.pettracker.features.pets.domain.Pet;
import com.pettracker.features.vaccinations.domain.VaccinationRecord;
import com.pettracker.features.vaccinations.persistence.VaccinationRecordRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import com.pettracker.features.auth.AuthTokenFilter;

@RestController
@RequestMapping("/api")
public class VaccinationController {
    private final VaccinationRecordRepository records;
    private final PetController pets;
    public VaccinationController(VaccinationRecordRepository records, PetController pets) { this.records = records; this.pets = pets; }

    @GetMapping("/pets/{petId}/vaccinations")
    public List<VaccinationResponse> list(@PathVariable UUID petId, HttpServletRequest request) {
        pets.find(petId, owner(request));
        return records.findAllByPetIdOrderByAdministeredOnDesc(petId).stream().map(VaccinationResponse::from).toList();
    }

    @PostMapping("/vaccinations")
    public VaccinationResponse create(@Valid @RequestBody CreateVaccination request, HttpServletRequest httpRequest) {
        Pet pet = pets.find(request.petId(), owner(httpRequest));
        VaccinationRecord record = records.save(new VaccinationRecord(pet, request.vaccineName(), request.administeredOn(), request.nextDueOn(), request.provider(), request.notes()));
        return VaccinationResponse.from(record);
    }

    private UUID owner(HttpServletRequest request) { Object id = request.getAttribute(AuthTokenFilter.OWNER_ID); if (id instanceof UUID ownerId) return ownerId; throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"); }

    @PutMapping("/vaccinations/{recordId}")
    @Transactional
    public VaccinationResponse update(@PathVariable UUID recordId,
            @Valid @RequestBody UpdateVaccination request, HttpServletRequest httpRequest) {
        UUID ownerId = owner(httpRequest);
        VaccinationRecord record = records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vaccination not found"));
        pets.find(record.getPet().getId(), ownerId);
        if (request.nextDueOn() != null && !request.nextDueOn().isAfter(request.administeredOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The next due date must be after the administration date");
        }
        record.update(request.vaccineName().trim(), request.administeredOn(), request.nextDueOn(),
                request.provider(), request.notes());
        return VaccinationResponse.from(records.save(record));
    }

    public record UpdateVaccination(
            @NotBlank @Size(max = 120) String vaccineName,
            @NotNull LocalDate administeredOn,
            LocalDate nextDueOn,
            @Size(max = 160) String provider,
            @Size(max = 2000) String notes) {}

    public record CreateVaccination(@JsonAlias("pet_id") UUID petId, @NotBlank @JsonAlias("vaccine_name") String vaccineName, @NotNull @JsonAlias("administered_on") LocalDate administeredOn, @JsonAlias("next_due_on") LocalDate nextDueOn, String provider, String notes) {}
    public record VaccinationResponse(UUID id, UUID petId, String vaccineName, LocalDate administeredOn, LocalDate nextDueOn, String provider, String notes) {
        static VaccinationResponse from(VaccinationRecord record) { return new VaccinationResponse(record.getId(), record.getPet().getId(), record.getVaccineName(), record.getAdministeredOn(), record.getNextDueOn(), record.getProvider(), record.getNotes()); }
    }
    @DeleteMapping("/vaccinations/{recordId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
@Transactional
public void delete(
        @PathVariable UUID recordId,
        HttpServletRequest request
) {
    UUID ownerId = owner(request);

    VaccinationRecord record = records.findById(recordId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Vaccination not found"
            ));

    // Verify that the signed-in owner owns this vaccination's pet.
    pets.find(record.getPet().getId(), ownerId);

    records.delete(record);
}


}
