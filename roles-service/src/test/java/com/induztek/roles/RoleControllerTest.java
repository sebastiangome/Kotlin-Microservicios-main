package com.induztek.roles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RoleControllerTest {
    private RoleRepository roles;
    private RoleController controller;

    @BeforeEach
    void setUp() {
        roles = mock(RoleRepository.class);
        controller = new RoleController(roles);
    }

    @Test
    void listReturnsAllRoles() {
        when(roles.findAll()).thenReturn(List.of(new RoleEntity("1", "ADMIN", "Admin", "d")));

        assertEquals("ADMIN", controller.list().get(0).code());
    }

    @Test
    void getReturns404WhenRoleDoesNotExist() {
        when(roles.findByCode("GHOST")).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.get("GHOST")).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.getInternal("GHOST")).getStatusCode().value());
    }

    @Test
    void createRejectsDuplicateRoleCode() {
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(new RoleEntity("1", "ADMIN", "Admin", "d")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create(new RoleController.RoleRequest("2", "ADMIN", "Admin 2", null)));

        assertEquals(409, error.getStatusCode().value());
        verify(roles, never()).save(any(RoleEntity.class));
    }

    @Test
    void createStoresNewRole() {
        when(roles.findByCode("AUDITOR")).thenReturn(Optional.empty());
        when(roles.save(any(RoleEntity.class))).thenAnswer(call -> call.getArgument(0));

        RoleController.RoleResponse created = controller.create(new RoleController.RoleRequest("9", "AUDITOR", "Auditor", "ro"));

        assertEquals("AUDITOR", created.code());
        assertEquals("Auditor", created.name());
    }
}
