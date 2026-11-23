package com.induztek.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.induztek.security.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/** Verifica códigos HTTP y cuerpo de error de los endpoints públicos de autenticación. */
class IdentityControllerTest {
    private IdentityService identity;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        identity = mock(IdentityService.class);
        mvc = MockMvcBuilders.standaloneSetup(new IdentityController(identity))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void loginWithMalformedEmailReturnsValidationErrorWithoutCallingService() throws Exception {
        mvc.perform(json("/api/auth/login", "{\"email\":\"correo-malo\",\"password\":\"A-strong-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
        org.mockito.Mockito.verifyNoInteractions(identity);
    }

    @Test
    void loginWithBlankPasswordReturnsValidationError() throws Exception {
        mvc.perform(json("/api/auth/login", "{\"email\":\"tech@example.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("password"));
    }

    @Test
    void loginWithMalformedJsonReturnsMalformedRequest() throws Exception {
        mvc.perform(json("/api/auth/login", "{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginWithWrongCredentialsReturns401WithStableErrorBody() throws Exception {
        when(identity.login(any(IdentityService.LoginRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        mvc.perform(json("/api/auth/login", "{\"email\":\"wrong@example.com\",\"password\":\"A-strong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void pendingAccountLoginReturns403() throws Exception {
        when(identity.login(any(IdentityService.LoginRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is awaiting administrator approval or disabled"));

        mvc.perform(json("/api/auth/login", "{\"email\":\"tech@example.com\",\"password\":\"A-strong-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void registerWithoutTermsOrWithShortPasswordReturnsValidationError() throws Exception {
        mvc.perform(json("/api/auth/register",
                        "{\"email\":\"tech@example.com\",\"password\":\"short\",\"name\":\"Tech\",\"termsAccepted\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        when(identity.register(any(IdentityService.RegisterRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists"));

        mvc.perform(json("/api/auth/register",
                        "{\"email\":\"tech@example.com\",\"password\":\"A-strong-password\",\"name\":\"Tech\",\"termsAccepted\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void registerSuccessReturns201() throws Exception {
        when(identity.register(any(IdentityService.RegisterRequest.class))).thenReturn(new IdentityService.UserResponse(
                "u1", "tech@example.com", "Tech", "CLIENT", "P-04", null, true, null, false, null, null, null));

        mvc.perform(json("/api/auth/register",
                        "{\"email\":\"tech@example.com\",\"password\":\"A-strong-password\",\"name\":\"Tech\",\"termsAccepted\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("tech@example.com"));
    }

    @Test
    void unexpectedFailuresReturn500WithoutLeakingInternalDetails() throws Exception {
        when(identity.login(any(IdentityService.LoginRequest.class))).thenThrow(new IllegalStateException("db password is hunter2"));

        mvc.perform(json("/api/auth/login", "{\"email\":\"tech@example.com\",\"password\":\"A-strong-password\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }
}
