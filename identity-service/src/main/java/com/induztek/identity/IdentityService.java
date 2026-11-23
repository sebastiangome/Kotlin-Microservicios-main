package com.induztek.identity;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {
    private static final String DEFAULT_ROLE = "FIELD_TECHNICIAN";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_LOGIN_FAILURES = 5;
    static final int MAX_RESET_ATTEMPTS = 5;
    private final UserRepository users;
    private final PasswordResetRepository resetCodes;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final RoleClient roleClient;
    private final NotificationClient notifications;
    private final Duration tokenLifetime;
    private final Duration resetCodeLifetime;
    private final boolean requireApproval;
    private final SecureRandom random = new SecureRandom();
    private final LoginAttemptTracker loginAttempts = new LoginAttemptTracker(MAX_LOGIN_FAILURES, Duration.ofMinutes(15));

    public IdentityService(
            UserRepository users,
            PasswordResetRepository resetCodes,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            RoleClient roleClient,
            NotificationClient notifications,
            @Value("${identity.jwt.expiration-minutes:480}") long tokenMinutes,
            @Value("${identity.password-reset.code-minutes:10}") long resetCodeMinutes,
            @Value("${identity.registration.require-approval:true}") boolean requireApproval) {
        this.users = users;
        this.resetCodes = resetCodes;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.roleClient = roleClient;
        this.notifications = notifications;
        this.tokenLifetime = Duration.ofMinutes(tokenMinutes);
        this.resetCodeLifetime = Duration.ofMinutes(resetCodeMinutes);
        this.requireApproval = requireApproval;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!request.termsAccepted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terms must be accepted");
        }
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        String targetRoleCode = request.roleCode() != null && !request.roleCode().isBlank()
                ? request.roleCode()
                : "CLIENT";
        if (ADMIN_ROLE.equalsIgnoreCase(targetRoleCode.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This role cannot be self-assigned");
        }
        RoleClient.RoleResponse role = roleClient.findByCode(targetRoleCode);
        if (role == null) {
            role = roleClient.findByCode(DEFAULT_ROLE);
        }
        UserEntity user = users.save(new UserEntity(
                UUID.randomUUID().toString(),
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                role != null ? role.code() : DEFAULT_ROLE,
                normalizeNullable(request.company()),
                Instant.now(),
                !requireApproval));
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(String id) {
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        users.delete(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String loginKey = normalizeEmail(request.email());
        if (loginAttempts.isLocked(loginKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again later");
        }
        UserEntity user = users.findByEmailIgnoreCase(loginKey).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttempts.recordFailure(loginKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        loginAttempts.recordSuccess(loginKey);
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is awaiting administrator approval or disabled");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("induztek")
                .issuedAt(now)
                .expiresAt(now.plus(tokenLifetime))
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRoleCode())
                .claim("company", user.getCompany() != null ? user.getCompany() : "")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        String pic = user.getFotoPerfil() != null ? user.getFotoPerfil() : user.getPhotoUrl();
        return new LoginResponse(user.getId(), token, user.getName(), user.getEmail(), profileCode(user.getRoleCode()),
                user.getCompany(), user.getRoleCode(), user.isActive(), pic, pic);
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        UserEntity user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        PasswordResetEntity reset = new PasswordResetEntity(
                UUID.randomUUID().toString(), email, passwordEncoder.encode(code), Instant.now().plus(resetCodeLifetime));
        resetCodes.save(reset);
        try {
            notifications.sendPasswordReset(email, code);
        } catch (RuntimeException exception) {
            reset.setUsed(true);
            resetCodes.save(reset);
            throw exception;
        }
    }

    // noRollbackFor: el contador de intentos fallidos debe guardarse aunque se lance la excepción 400.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verifyPasswordResetCode(String rawEmail, String rawCode) {
        requireValidResetCode(normalizeEmail(rawEmail), rawCode);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        PasswordResetEntity reset = requireValidResetCode(email, request.code());
        UserEntity user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        reset.setUsed(true);
        users.save(user);
        resetCodes.save(reset);
        List<PasswordResetEntity> otherCodes = resetCodes.findByEmailIgnoreCaseAndUsedFalseOrderByExpiresAtDesc(email);
        otherCodes.forEach(code -> code.setUsed(true));
        resetCodes.saveAll(otherCodes);
    }

    private PasswordResetEntity requireValidResetCode(String email, String rawCode) {
        PasswordResetEntity reset = resetCodes.findFirstByEmailIgnoreCaseAndUsedFalseOrderByExpiresAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired recovery code"));
        if (reset.getExpiresAt().isBefore(Instant.now()) || reset.getAttempts() >= MAX_RESET_ATTEMPTS) {
            reset.setUsed(true);
            resetCodes.save(reset);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired recovery code");
        }
        if (!passwordEncoder.matches(rawCode, reset.getCodeHash())) {
            reset.registerFailedAttempt();
            if (reset.getAttempts() >= MAX_RESET_ATTEMPTS) {
                reset.setUsed(true);
            }
            resetCodes.save(reset);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired recovery code");
        }
        return reset;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    public UserResponse setRole(String userId, String roleCode) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        RoleClient.RoleResponse role = roleClient.findByCode(roleCode);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        user.setRoleCode(role.code());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse updateProfile(String userId, ProfileUpdateRequest request) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = normalizeEmail(request.email());
            if (!newEmail.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(newEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
            }
            user.setEmail(newEmail);
        }
        if (request.company() != null) {
            user.setCompany(normalizeNullable(request.company()));
        }
        String pic = request.fotoPerfil() != null ? request.fotoPerfil() : request.photoUrl();
        if (pic != null) {
            user.setFotoPerfil(pic);
            user.setPhotoUrl(pic);
        }
        if (request.biography() != null) {
            user.setBiography(request.biography().trim());
        }
        return UserResponse.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse setActive(String id, boolean active) {
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        user.setActive(active);
        return UserResponse.from(users.save(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String profileCode(String roleCode) {
        if (roleCode == null) {
            return "P-01";
        }
        return switch (roleCode) {
            case "TEST_ENGINEER" -> "P-02";
            case "ADMIN" -> "P-03";
            case "CLIENT" -> "P-04";
            default -> "P-01";
        };
    }

    public record RegisterRequest(
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 10, max = 72) String password,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
            @jakarta.validation.constraints.Size(max = 120) String company,
            @jakarta.validation.constraints.Size(max = 50) String roleCode,
            @jakarta.validation.constraints.AssertTrue boolean termsAccepted) {
    }

    public record LoginRequest(
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email,
            @jakarta.validation.constraints.NotBlank String password) {
    }

    public record ResetPasswordRequest(
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{6}") String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 10, max = 72) String newPassword) {
    }

    public record VerifyResetCodeRequest(
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{6}") String code) {
    }

    public record RecoveryRequest(@jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email) {
    }

    public record ProfileUpdateRequest(
            @jakarta.validation.constraints.Size(max = 120) String name,
            @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.Size(max = 120) String company,
            @jakarta.validation.constraints.Size(max = 2048) String fotoPerfil,
            @jakarta.validation.constraints.Size(max = 2048) String photoUrl,
            @jakarta.validation.constraints.Size(max = 500) String biography) {
    }

    public record LoginResponse(
            String id, String token, String usuario, String email, String perfilCodigo,
            String empresaCliente, String roleCode, boolean activo, String fotoPerfil, String photoUrl) {
    }

    public record UserResponse(
            String id, String email, String name, String roleCode, String perfilCodigo, String company,
            boolean termsAccepted, Instant termsAcceptedAt, boolean active, String fotoPerfil, String photoUrl, String biography) {
        static UserResponse from(UserEntity user) {
            String pic = user.getFotoPerfil() != null ? user.getFotoPerfil() : user.getPhotoUrl();
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRoleCode(),
                profileCode(user.getRoleCode()),
                user.getCompany(), user.isTermsAccepted(), user.getTermsAcceptedAt(), user.isActive(),
                pic, pic, user.getBiography());
        }
    }
}
