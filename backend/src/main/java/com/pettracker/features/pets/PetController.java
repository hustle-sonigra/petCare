package com.pettracker.features.pets;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import com.pettracker.features.auth.AuthTokenFilter;

import com.pettracker.features.pets.domain.Pet;
import com.pettracker.features.pets.domain.PetSpecies;
import com.pettracker.features.pets.persistence.PetRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/pets") 
public class PetController {
    private final PetRepository pets;
    public PetController(PetRepository pets) { this.pets = pets; }

    @GetMapping
    public List<PetResponse> list(HttpServletRequest request) { return pets.findAllByOwnerIdAndArchivedAtIsNullOrderByNameAsc(owner(request)).stream().map(PetResponse::from).toList(); }

    @GetMapping("/{petId}")
    public PetResponse get(@PathVariable UUID petId, HttpServletRequest request) { return PetResponse.from(find(petId, owner(request))); }

    @PostMapping
    public PetResponse create(@Valid @RequestBody CreatePet request, HttpServletRequest httpRequest) {
        Pet pet = pets.save(new Pet(owner(httpRequest), request.name().trim(), request.species(), request.breed(), request.birthDate()));
        return PetResponse.from(pet);
    }

    public Pet find(UUID petId, UUID ownerId) { return pets.findByIdAndOwnerIdAndArchivedAtIsNull(petId, ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found")); }
    private UUID owner(HttpServletRequest request) { Object id = request.getAttribute(AuthTokenFilter.OWNER_ID); if (id instanceof UUID ownerId) return ownerId; throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"); }

    public record CreatePet(@NotBlank @Size(max = 100) String name, @NotNull PetSpecies species, @Size(max = 100) String breed, @JsonAlias("birth_date") LocalDate birthDate) {}

    public record PetResponse(UUID id, String name, String species, String breed, java.time.LocalDate birthDate) {
        static PetResponse from(Pet pet) { return new PetResponse(pet.getId(), pet.getName(), pet.getSpecies().name(), pet.getBreed(), pet.getBirthDate()); }
    }
}
