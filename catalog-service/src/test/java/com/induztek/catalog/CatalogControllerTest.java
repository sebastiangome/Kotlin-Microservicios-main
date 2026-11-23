package com.induztek.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class CatalogControllerTest {
    private EquipoRepository equipos;
    private InstrumentoRepository instrumentos;
    private CatalogController controller;

    private final EquipoEntity equipoA = equipo("id-a", "EQ-A", "Cliente A");
    private final EquipoEntity equipoB = equipo("id-b", "EQ-B", "Cliente B");

    @BeforeEach
    void setUp() {
        equipos = mock(EquipoRepository.class);
        instrumentos = mock(InstrumentoRepository.class);
        controller = new CatalogController(equipos, instrumentos);
        when(equipos.findAll()).thenReturn(List.of(equipoA, equipoB));
    }

    private static EquipoEntity equipo(String id, String codigo, String cliente) {
        return new EquipoEntity(id, codigo, "TAG-" + codigo, "Transformador", cliente, "Planta",
                "Marca", "Modelo", "SN-" + codigo, "500 kVA", "2020", "23 kV", "3", "Dyn11", "Registrado");
    }

    private static CatalogController.EquipoRequest request(String codigo) {
        return new CatalogController.EquipoRequest(codigo, "TAG", "Transformador", "Cliente A", "Planta",
                "Marca", "Modelo", "SN", "500 kVA", "2020", "23 kV", "3", "Dyn11", "Registrado");
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

    // ── equipos ───────────────────────────────────────────────────────────

    @Test
    void clientOnlySeesEquipmentOfItsOwnCompany() {
        List<CatalogController.EquipoResponse> result = controller.listEquipment(null, jwt("CLIENT", "Cliente A"));

        assertEquals(1, result.size());
        assertEquals("EQ-A", result.get(0).codigo());
    }

    @Test
    void clientWithoutCompanyClaimSeesNoEquipmentInsteadOfEverything() {
        assertTrue(controller.listEquipment(null, jwt("CLIENT", null)).isEmpty());
        assertTrue(controller.listEquipment("Cliente B", jwt("CLIENT", "  ")).isEmpty());
    }

    @Test
    void staffCanFilterEquipmentByClient() {
        assertEquals(1, controller.listEquipment("Cliente B", jwt("ADMIN", null)).size());
        assertEquals(2, controller.listEquipment(null, jwt("ADMIN", null)).size());
    }

    @Test
    void getEquipmentReturns404WhenCodeDoesNotExist() {
        when(equipos.findByCodigo("NOPE")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.getEquipment("NOPE", jwt("ADMIN", null))));
    }

    @Test
    void clientCannotReadEquipmentOfAnotherCompany() {
        when(equipos.findByCodigo("EQ-B")).thenReturn(Optional.of(equipoB));

        assertEquals(404, statusOf(() -> controller.getEquipment("EQ-B", jwt("CLIENT", "Cliente A"))));
    }

    @Test
    void createEquipmentRejectsDuplicateCode() {
        when(equipos.findByCodigo("EQ-A")).thenReturn(Optional.of(equipoA));

        assertEquals(409, statusOf(() -> controller.createEquipment(request("EQ-A"))));
        verify(equipos, never()).save(any(EquipoEntity.class));
    }

    @Test
    void createEquipmentSavesNewEquipment() {
        when(equipos.findByCodigo("EQ-NEW")).thenReturn(Optional.empty());
        when(equipos.save(any(EquipoEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("EQ-NEW", controller.createEquipment(request("EQ-NEW")).codigo());
    }

    @Test
    void updateEquipmentReturns404WhenMissing() {
        when(equipos.findByCodigo("NOPE")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.updateEquipment("NOPE", request("NOPE"))));
    }

    @Test
    void updateEquipmentRejectsChangingCodeToOneAlreadyInUse() {
        when(equipos.findByCodigo("EQ-A")).thenReturn(Optional.of(equipoA));
        when(equipos.findByCodigo("EQ-B")).thenReturn(Optional.of(equipoB));

        assertEquals(409, statusOf(() -> controller.updateEquipment("EQ-A", request("EQ-B"))));
        verify(equipos, never()).save(any(EquipoEntity.class));
    }

    @Test
    void duplicateCheckDetectsSameSerialInSameLocation() {
        CatalogController.DuplicateResponse found = controller.duplicateCheck("Planta", "", "SN-EQ-A", "", jwt("ADMIN", null));
        CatalogController.DuplicateResponse other = controller.duplicateCheck("Otra planta", "", "SN-EQ-A", "", jwt("ADMIN", null));

        assertTrue(found.duplicate());
        assertEquals("EQ-A", found.equipment().codigo());
        assertFalse(other.duplicate());
    }

    // ── instrumentos ──────────────────────────────────────────────────────

    @Test
    void getInstrumentReturns404WhenMissing() {
        when(instrumentos.findById("i-1")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.getInstrument("i-1", jwt("ADMIN", null))));
    }

    @Test
    void clientCannotListOrReadInstruments() {
        assertEquals(403, statusOf(() -> controller.listInstruments(false, jwt("CLIENT", "Cliente A"))));
        assertEquals(403, statusOf(() -> controller.getInstrument("i-1", jwt("CLIENT", "Cliente A"))));
        verify(instrumentos, never()).findAll();
        verify(instrumentos, never()).findById(any());
    }

    @Test
    void duplicateCheckOnlySeesOwnCompanyEquipmentForClients() {
        CatalogController.DuplicateResponse otherCompany =
                controller.duplicateCheck("Planta", "", "SN-EQ-B", "", jwt("CLIENT", "Cliente A"));
        CatalogController.DuplicateResponse ownCompany =
                controller.duplicateCheck("Planta", "", "SN-EQ-A", "", jwt("CLIENT", "Cliente A"));
        CatalogController.DuplicateResponse noCompany =
                controller.duplicateCheck("Planta", "", "SN-EQ-A", "", jwt("CLIENT", null));

        assertFalse(otherCompany.duplicate());
        assertTrue(ownCompany.duplicate());
        assertFalse(noCompany.duplicate());
    }

    @Test
    void createInstrumentRejectsExistingId() {
        when(instrumentos.existsById("i-1")).thenReturn(true);

        assertEquals(409, statusOf(() -> controller.createInstrument(
                new CatalogController.InstrumentoRequest("i-1", "Megger", null, null, null, null, true))));
        verify(instrumentos, never()).save(any(InstrumentoEntity.class));
    }

    @Test
    void updateCalibrationReturns404WhenInstrumentIsMissing() {
        when(instrumentos.findById("i-9")).thenReturn(Optional.empty());

        assertEquals(404, statusOf(() -> controller.updateCalibration("i-9", new CatalogController.CalibrationRequest("2027-01-01"))));
    }

    @Test
    void createInstrumentDefaultsToActiveWhenFlagIsOmitted() {
        when(instrumentos.existsById(any(String.class))).thenReturn(false);
        when(instrumentos.save(any(InstrumentoEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertTrue(controller.createInstrument(
                new CatalogController.InstrumentoRequest(null, "Megger", null, null, null, null, null)).activo());
    }
}
