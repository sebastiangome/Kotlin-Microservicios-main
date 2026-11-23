package com.induztek.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class RoleClientTest {
    private static RoleClient clientReturning(HttpStatus status, String json) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(status).header("Content-Type", "application/json").body(json).build()));
        return new RoleClient(builder, "http://roles-service");
    }

    @Test
    void returnsRoleWhenRolesServiceFindsIt() {
        RoleClient client = clientReturning(HttpStatus.OK,
                "{\"id\":\"1\",\"code\":\"CLIENT\",\"name\":\"Client\",\"description\":\"d\"}");

        assertEquals("CLIENT", client.findByCode("CLIENT").code());
    }

    @Test
    void returnsNullWhenRoleDoesNotExistInsteadOfReportingServiceDown() {
        assertNull(clientReturning(HttpStatus.NOT_FOUND, "{}").findByCode("GHOST"));
    }

    @Test
    void reportsServiceUnavailableWhenRolesServiceFails() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{}").findByCode("CLIENT"));

        assertEquals(503, error.getStatusCode().value());
    }
}
