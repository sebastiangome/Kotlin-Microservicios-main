package com.induztek.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class CatalogClientTest {
    private static CatalogClient clientReturning(HttpStatus status, String json) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(status).header("Content-Type", "application/json").body(json).build()));
        return new CatalogClient(builder, "http://catalog-service");
    }

    @Test
    void missingEquipmentIsRejectedWithNotFoundBeforeOperationIsSaved() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> clientReturning(HttpStatus.OK, "{\"exists\":false}").requireEquipment("MISSING"));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void existingEquipmentIsAccepted() {
        assertDoesNotThrow(() -> clientReturning(HttpStatus.OK, "{\"exists\":true}").requireEquipment("EQ-1"));
    }

    @Test
    void catalogServiceFailureIsReportedAsServiceUnavailable() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{}").requireEquipment("EQ-1"));

        assertEquals(503, error.getStatusCode().value());
    }
}
