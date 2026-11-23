package com.induztek.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class OperationsControllerTest {
    private VisitRepository visits;
    private ProtocolRepository protocols;
    private TestRepository tests;
    private ActaRepository actas;
    private CatalogClient catalog;
    private OperationsController controller;

    @BeforeEach
    void setUp() {
        visits = mock(VisitRepository.class);
        protocols = mock(ProtocolRepository.class);
        tests = mock(TestRepository.class);
        actas = mock(ActaRepository.class);
        catalog = mock(CatalogClient.class);
        controller = new OperationsController(visits, protocols, tests, actas, catalog, new ObjectMapper());
    }

    private static Jwt jwt(String role, String company) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").claim("role", role);
        if (company != null) {
            builder.claim("company", company);
        }
        return builder.build();
    }

    private static int statusOf(Executable action) {
        return assertThrows(ResponseStatusException.class, action).getStatusCode().value();
    }

    private static OperationsController.ProtocolRequest protocolRequest(String visitaId, String equipoCodigo) {
        return new OperationsController.ProtocolRequest(null, "P-1", visitaId, null, equipoCodigo,
                null, null, null, null, null, null, null, null, false,
                null, null, false, null, null, null, null, null, null, null);
    }

    @Test
    void getVisitReturns404WhenMissing() {
        when(visits.findById("v-1")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.getVisit("v-1", jwt("ADMIN", null))));
    }

    @Test
    void clientCannotReadVisitOfAnotherCompany() {
        VisitEntity visit = mock(VisitEntity.class);
        when(visit.getCliente()).thenReturn("Cliente B");
        when(visits.findById("v-1")).thenReturn(Optional.of(visit));

        assertEquals(404, statusOf(() -> controller.getVisit("v-1", jwt("CLIENT", "Cliente A"))));
    }

    @Test
    void clientCanReadVisitOfItsOwnCompanyCaseInsensitively() {
        VisitEntity visit = mock(VisitEntity.class);
        when(visit.getId()).thenReturn("v-1");
        when(visit.getCliente()).thenReturn("CLIENTE A");
        when(visits.findById("v-1")).thenReturn(Optional.of(visit));

        assertEquals("v-1", controller.getVisit("v-1", jwt("CLIENT", "cliente a")).id());
    }

    @Test
    void closeVisitReturns404WhenMissing() {
        when(visits.findById("v-1")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.closeVisit("v-1")));
    }

    @Test
    void createProtocolIsRejectedWhenEquipmentDoesNotExistInCatalog() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: EQ-X"))
                .when(catalog).requireEquipment("EQ-X");

        assertEquals(404, statusOf(() -> controller.createProtocol(protocolRequest("v-1", "EQ-X"))));
        verify(protocols, never()).save(any(ProtocolEntity.class));
    }

    @Test
    void createProtocolRejectsSecondProtocolForSameEquipmentAndVisit() {
        when(visits.existsById("v-1")).thenReturn(true);
        when(protocols.existsByVisitaIdAndEquipoCodigo("v-1", "EQ-1")).thenReturn(true);

        assertEquals(409, statusOf(() -> controller.createProtocol(protocolRequest("v-1", "EQ-1"))));
        verify(protocols, never()).save(any(ProtocolEntity.class));
    }

    @Test
    void createProtocolRequiresAnExistingVisit() {
        when(visits.existsById("v-x")).thenReturn(false);

        assertEquals(404, statusOf(() -> controller.createProtocol(protocolRequest("v-x", "EQ-1"))));
        verify(protocols, never()).save(any(ProtocolEntity.class));
    }

    @Test
    void createProtocolRejectsDuplicatedProtocolCode() {
        when(visits.existsById("v-1")).thenReturn(true);
        when(protocols.existsByCodigoProtocolo("P-1")).thenReturn(true);

        assertEquals(409, statusOf(() -> controller.createProtocol(protocolRequest("v-1", "EQ-1"))));
        verify(protocols, never()).save(any(ProtocolEntity.class));
    }

    @Test
    void createProtocolCannotOverwriteAnExistingId() {
        when(visits.existsById("v-1")).thenReturn(true);
        when(protocols.existsById("p-9")).thenReturn(true);
        OperationsController.ProtocolRequest request = new OperationsController.ProtocolRequest(
                "p-9", "P-9", "v-1", null, "EQ-1", null, null, null, null, null, null, null, null, false,
                null, null, false, null, null, null, null, null, null, null);

        assertEquals(409, statusOf(() -> controller.createProtocol(request)));
        verify(protocols, never()).save(any(ProtocolEntity.class));
    }

    @Test
    void createProtocolIgnoresEstadoAndFormalizationSentByTheClient() {
        when(visits.existsById("v-1")).thenReturn(true);
        when(protocols.save(any(ProtocolEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OperationsController.ProtocolRequest request = new OperationsController.ProtocolRequest(
                null, "P-7", "v-1", null, "EQ-1", null, null, "FORMALIZADO", null, null, null, null, null, false,
                null, null, false, "hacker", "2026-01-01 00:00", null, null, null, null, null);

        OperationsController.ProtocolResponse response = controller.createProtocol(request);

        assertEquals("REGISTRADO_TERRENO", response.estado());
        assertEquals(null, response.formalizadoPor());
        assertEquals(null, response.fechaFormalizacion());
    }

    @Test
    void formalizeProtocolRejectsAnAlreadyFormalizedProtocol() {
        ProtocolEntity protocol = mock(ProtocolEntity.class);
        when(protocol.getEstado()).thenReturn("FORMALIZADO");
        when(protocols.findById("p-1")).thenReturn(Optional.of(protocol));

        assertEquals(409, statusOf(() -> controller.formalizeProtocol("p-1", new OperationsController.FormalizeRequest("admin"))));
        verify(protocols, never()).save(any(ProtocolEntity.class));
    }

    @Test
    void createTestRequiresAnExistingProtocolWhenOneIsGiven() {
        when(protocols.existsById("p-x")).thenReturn(false);
        OperationsController.TestRequest request = new OperationsController.TestRequest(
                null, "p-x", null, "EQ-1", null, null, null, null, null, null, null, null, null, null,
                false, null, null, null, null, null, false);

        assertEquals(404, statusOf(() -> controller.createTest(request)));
        verify(tests, never()).save(any(TestEntity.class));
    }

    @Test
    void createActRequiresVisitAndRejectsASecondActForTheSameVisit() {
        OperationsController.ActaRequest request = new OperationsController.ActaRequest(
                null, "v-1", null, null, null, null, null, null, false, null, null);

        when(visits.existsById("v-1")).thenReturn(false);
        assertEquals(404, statusOf(() -> controller.createAct(request)));

        when(visits.existsById("v-1")).thenReturn(true);
        when(actas.existsByVisitaId("v-1")).thenReturn(true);
        assertEquals(409, statusOf(() -> controller.createAct(request)));
        verify(actas, never()).save(any(ActaEntity.class));
    }

    @Test
    void createProtocolPropagatesCatalogOutageAs503() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable"))
                .when(catalog).requireEquipment("EQ-1");

        assertEquals(503, statusOf(() -> controller.createProtocol(protocolRequest("v-1", "EQ-1"))));
    }

    @Test
    void getProtocolReturns404WhenMissing() {
        when(protocols.findById("p-1")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.getProtocol("p-1", jwt("ADMIN", null))));
    }

    @Test
    void formalizeProtocolReturns404WhenMissing() {
        when(protocols.findById("p-1")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.formalizeProtocol("p-1", new OperationsController.FormalizeRequest("admin"))));
    }

    @Test
    void getTestReturns404WhenMissing() {
        when(tests.findById("t-1")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.getTest("t-1", jwt("ADMIN", null))));
    }

    @Test
    void getActReturns404WhenMissingOrOwnedByAnotherCompany() {
        when(actas.findById("a-0")).thenReturn(Optional.empty());
        ActaEntity act = mock(ActaEntity.class);
        when(act.getCliente()).thenReturn("Cliente B");
        when(actas.findById("a-1")).thenReturn(Optional.of(act));

        assertEquals(404, statusOf(() -> controller.getAct("a-0", jwt("ADMIN", null))));
        assertEquals(404, statusOf(() -> controller.getAct("a-1", jwt("CLIENT", "Cliente A"))));
    }
}
