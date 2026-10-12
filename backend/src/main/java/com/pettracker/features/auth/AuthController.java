package com.pettracker.features.auth;

import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final OwnerRepository owners; private final PasswordHasher passwords; private final JwtService jwt;
    public AuthController(OwnerRepository owners, PasswordHasher passwords, JwtService jwt) { this.owners = owners; this.passwords = passwords; this.jwt = jwt; }
    @PostMapping("/signin")
    public AuthResponse signin(@Valid @RequestBody SigninRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (owners.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        Owner owner = owners.save(new Owner(request.name().trim(), email, passwords.hash(request.password()), request.timezone().trim()));
        return response(owner);
    }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        Owner owner = owners.findByEmailIgnoreCase(request.email().trim()).filter(o -> passwords.matches(request.password(), o.getPasswordHash())).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return response(owner);
    }
    private AuthResponse response(Owner owner) { return new AuthResponse(jwt.issue(owner), owner.getId(), owner.getName(), owner.getEmail()); }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record SigninRequest(@NotBlank @Size(max=120) String name, @NotBlank @Email String email, @NotBlank @Size(min=8, max=200) String password, @NotBlank String timezone) {}
    public record AuthResponse(String token, UUID ownerId, String name, String email) {}
}
