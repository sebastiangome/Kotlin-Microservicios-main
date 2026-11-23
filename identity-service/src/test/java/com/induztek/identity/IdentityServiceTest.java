package com.induztek.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.server.ResponseStatusException;

class IdentityServiceTest {
    private static final String PASSWORD = "A-strong-password";

    private UserRepository users;
    private PasswordResetRepository resets;
    private RoleClient roles;
    private NotificationClient notifications;
    private JwtEncoder jwtEncoder;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        resets = mock(PasswordResetRepository.class);
        roles = mock(RoleClient.class);
        notifications = mock(NotificationClient.class);
        jwtEncoder = mock(JwtEncoder.class);
        encoder = new BCryptPasswordEncoder();
    }

    private IdentityService service(boolean requireApproval) {
        return new IdentityService(users, resets, encoder, jwtEncoder, roles, notifications, 480, 10, requireApproval);
    }

    private UserEntity user(String role, boolean active) {
        return new UserEntity("user-1", "tech@example.com", encoder.encode(PASSWORD), "Tech",
                role, null, Instant.now(), active);
    }

    private static int statusOf(Executable action) {
        return assertThrows(ResponseStatusException.class, action).getStatusCode().value();
    }

    private static String reasonOf(Executable action) {
        return assertThrows(ResponseStatusException.class, action).getReason();
    }

    private static IdentityService.RegisterRequest registerRequest(String email, String role, boolean terms) {
        return new IdentityService.RegisterRequest(email, PASSWORD, "Tech", null, role, terms);
    }

    // ── registro ──────────────────────────────────────────────────────────

    @Test
    void registrationRequiresTermsBeforeCreatingAnAccount() {
        assertEquals(400, statusOf(() -> service(true).register(registerRequest("tech@example.com", null, false))));
        verifyNoInteractions(users, roles);
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("tech@example.com")).thenReturn(true);

        assertEquals(409, statusOf(() -> service(true).register(registerRequest("TECH@example.com", null, true))));
        verify(users, never()).save(any(UserEntity.class));
    }

    @Test
    void registrationCreatesInactiveAccountWithHashedPasswordWhenApprovalIsRequired() {
        when(roles.findByCode("CLIENT")).thenReturn(new RoleClient.RoleResponse("1", "CLIENT", "Client", ""));
        when(users.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        IdentityService.UserResponse response = service(true).register(registerRequest("New@Example.com", null, true));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(saved.capture());
        assertEquals("new@example.com", response.email());
        assertEquals("CLIENT", response.roleCode());
        assertFalse(response.active());
        assertFalse(PASSWORD.equals(saved.getValue().getPasswordHash()));
        assertTrue(encoder.matches(PASSWORD, saved.getValue().getPasswordHash()));
    }

    @Test
    void registrationActivatesAccountImmediatelyWhenApprovalIsNotRequired() {
        when(roles.findByCode("CLIENT")).thenReturn(new RoleClient.RoleResponse("1", "CLIENT", "Client", ""));
        when(users.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertTrue(service(false).register(registerRequest("new@example.com", null, true)).active());
    }

    @Test
    void registrationFallsBackToDefaultRoleWhenRequestedRoleDoesNotExist() {
        when(roles.findByCode("GHOST")).thenReturn(null);
        when(roles.findByCode("FIELD_TECHNICIAN"))
                .thenReturn(new RoleClient.RoleResponse("2", "FIELD_TECHNICIAN", "Technician", ""));
        when(users.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("FIELD_TECHNICIAN",
                service(true).register(registerRequest("new@example.com", "GHOST", true)).roleCode());
    }

    @Test
    void registrationCannotSelfAssignAdminRole() {
        assertEquals(403, statusOf(() -> service(false).register(registerRequest("evil@example.com", "ADMIN", true))));
        verify(users, never()).save(any(UserEntity.class));
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    void loginWithUnknownEmailAndValidPasswordIsUnauthorized() {
        when(users.findByEmailIgnoreCase("wrong@example.com")).thenReturn(Optional.empty());

        Executable login = () -> service(true).login(new IdentityService.LoginRequest("wrong@example.com", PASSWORD));

        assertEquals(401, statusOf(login));
        assertEquals("Invalid email or password", reasonOf(login));
    }

    @Test
    void loginWithWrongPasswordGivesSameErrorAsUnknownEmail() {
        when(users.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(user("FIELD_TECHNICIAN", true)));

        Executable login = () -> service(true).login(new IdentityService.LoginRequest("tech@example.com", "bad-password-1"));

        assertEquals(401, statusOf(login));
        assertEquals("Invalid email or password", reasonOf(login));
    }

    @Test
    void loginRejectsPendingAccountsWithForbidden() {
        when(users.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(user("FIELD_TECHNICIAN", false)));

        assertEquals(403, statusOf(() -> service(true).login(new IdentityService.LoginRequest("TECH@example.com", PASSWORD))));
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void loginReturnsTokenAndProfileForActiveUser() {
        when(users.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(user("TEST_ENGINEER", true)));
        Instant now = Instant.now();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(new Jwt("signed-token", now, now.plusSeconds(60), Map.of("alg", "HS256"), Map.of("sub", "user-1")));

        IdentityService.LoginResponse response =
                service(true).login(new IdentityService.LoginRequest("TECH@example.com", PASSWORD));

        assertEquals("signed-token", response.token());
        assertEquals("P-02", response.perfilCodigo());
        assertEquals("tech@example.com", response.email());
        assertTrue(response.activo());
    }

    @Test
    void loginDoesNotFailWithServerErrorForUnknownRoleCode() {
        when(users.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(user("LEGACY_ROLE", true)));
        Instant now = Instant.now();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(new Jwt("t", now, now.plusSeconds(60), Map.of("alg", "HS256"), Map.of("sub", "user-1")));

        assertEquals("P-01", service(true).login(new IdentityService.LoginRequest("tech@example.com", PASSWORD)).perfilCodigo());
    }

    // ── administración de cuentas ─────────────────────────────────────────

    @Test
    void setRoleRejectsRoleThatDoesNotExist() {
        when(users.findById("user-1")).thenReturn(Optional.of(user("CLIENT", true)));
        when(roles.findByCode("GHOST")).thenReturn(null);

        assertEquals(404, statusOf(() -> service(true).setRole("user-1", "GHOST")));
        verify(users, never()).save(any(UserEntity.class));
    }

    @Test
    void setRoleUpdatesUserWhenRoleExists() {
        when(users.findById("user-1")).thenReturn(Optional.of(user("CLIENT", true)));
        when(roles.findByCode("ADMIN")).thenReturn(new RoleClient.RoleResponse("3", "ADMIN", "Admin", ""));
        when(users.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("ADMIN", service(true).setRole("user-1", "ADMIN").roleCode());
    }

    @Test
    void updateProfileRejectsEmailAlreadyUsedByAnotherAccount() {
        when(users.findById("user-1")).thenReturn(Optional.of(user("CLIENT", true)));
        when(users.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        IdentityService.ProfileUpdateRequest request =
                new IdentityService.ProfileUpdateRequest(null, "Taken@Example.com", null, null, null, null);

        assertEquals(409, statusOf(() -> service(true).updateProfile("user-1", request)));
        verify(users, never()).save(any(UserEntity.class));
    }

    @Test
    void deleteUserFailsWithNotFoundWhenAccountDoesNotExist() {
        when(users.findById("missing")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> service(true).deleteUser("missing")));
    }

    // ── recuperación de contraseña ────────────────────────────────────────

    @Test
    void passwordResetRequestForUnknownAccountSendsNothing() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service(true).requestPasswordReset("ghost@example.com");

        verifyNoInteractions(notifications, resets);
    }

    @Test
    void passwordResetRequestMarksCodeUsedWhenEmailDeliveryFails() {
        when(users.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(user("CLIENT", true)));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Email delivery failed"))
                .when(notifications).sendPasswordReset(any(String.class), any(String.class));
        ArgumentCaptor<PasswordResetEntity> saved = ArgumentCaptor.forClass(PasswordResetEntity.class);

        assertEquals(502, statusOf(() -> service(true).requestPasswordReset("tech@example.com")));

        verify(resets, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertTrue(saved.getValue().isUsed());
    }

    @Test
    void verifyResetCodeAcceptsCorrectCodeAndRejectsWrongOrExpiredOnes() {
        PasswordResetEntity valid = new PasswordResetEntity("r1", "tech@example.com", encoder.encode("123456"), Instant.now().plusSeconds(300));
        when(resets.findFirstByEmailIgnoreCaseAndUsedFalseOrderByExpiresAtDesc("tech@example.com")).thenReturn(Optional.of(valid));
        IdentityService service = service(true);

        service.verifyPasswordResetCode("tech@example.com", "123456");
        assertEquals(400, statusOf(() -> service.verifyPasswordResetCode("tech@example.com", "000000")));

        PasswordResetEntity expired = new PasswordResetEntity("r2", "tech@example.com", encoder.encode("123456"), Instant.now().minusSeconds(5));
        when(resets.findFirstByEmailIgnoreCaseAndUsedFalseOrderByExpiresAtDesc("tech@example.com")).thenReturn(Optional.of(expired));
        assertEquals(400, statusOf(() -> service.verifyPasswordResetCode("tech@example.com", "123456")));
    }

    @Test
    void verifyResetCodeFailsWhenNoActiveCodeExists() {
        when(resets.findFirstByEmailIgnoreCaseAndUsedFalseOrderByExpiresAtDesc("tech@example.com")).thenReturn(Optional.empty());

        assertEquals(400, statusOf(() -> service(true).verifyPasswordResetCode("tech@example.com", "123456")));
    }

    @Test
    void resetCodeIsInvalidatedAfterTooManyWrongAttempts() {
        PasswordResetEntity reset = new PasswordResetEntity("r1", "tech@example.com", encoder.encode("123456"), Instant.now().plusSeconds(300));
        when(resets.findFirstByEmailIgnoreCaseAndUsedFalseOrderByExpiresAtDesc("tech@example.com")).thenReturn(Optional.of(reset));
        IdentityService service = service(true);

        for (int i = 0; i < IdentityService.MAX_RESET_ATTEMPTS; i++) {
            assertEquals(400, statusOf(() -> service.verifyPasswordResetCode("tech@example.com", "000000")));
        }

        assertTrue(reset.isUsed());
        assertEquals(IdentityService.MAX_RESET_ATTEMPTS, reset.getAttempts());
        // Ni siquiera el código correcto sirve después de agotar los intentos.
        assertEquals(400, statusOf(() -> service.verifyPasswordResetCode("tech@example.com", "123456")));
        assertEquals(400, statusOf(() -> service.resetPassword(
                new IdentityService.ResetPasswordRequest("tech@example.com", "123456", "Another-strong-password"))));
        verify(users, never()).save(any(UserEntity.class));
    }

    @Test
    void loginIsBlockedTemporarilyAfterRepeatedFailures() {
        when(users.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(user("CLIENT", true)));
        IdentityService service = service(true);

        for (int i = 0; i < 5; i++) {
            assertEquals(401, statusOf(() -> service.login(new IdentityService.LoginRequest("tech@example.com", "wrong-password"))));
        }

        // Incluso con la contraseña correcta, el bloqueo responde 429.
        assertEquals(429, statusOf(() -> service.login(new IdentityService.LoginRequest("tech@example.com", PASSWORD))));
        // El bloqueo es por email: otra cuenta no se ve afectada.
        when(users.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.empty());
        assertEquals(401, statusOf(() -> service.login(new IdentityService.LoginRequest("other@example.com", "wrong-password"))));
    }
}
