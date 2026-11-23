package com.induztek.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/** Falla si se agrega un endpoint sin documentar en Swagger o con códigos de respuesta repetidos. */
class ApiDocumentationTest {
    private static final List<Class<?>> CONTROLLERS = List.of(OperationsController.class, ImageController.class);

    private static boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    @Test
    void everyEndpointIsDocumentedWithItsPossibleResponses() {
        int endpoints = 0;
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                endpoints++;
                String name = controller.getSimpleName() + "." + method.getName();
                assertNotNull(method.getAnnotation(Operation.class), name + " no tiene @Operation");
                ApiResponses responses = method.getAnnotation(ApiResponses.class);
                assertNotNull(responses, name + " no tiene @ApiResponses");
                Set<String> codes = new HashSet<>();
                for (ApiResponse response : responses.value()) {
                    assertTrue(codes.add(response.responseCode()), name + " repite el código " + response.responseCode());
                }
                assertTrue(codes.contains("500"), name + " no documenta el 500");
                assertTrue(codes.stream().anyMatch(code -> code.startsWith("2")), name + " no documenta la respuesta exitosa");
            }
        }
        assertTrue(endpoints > 0);
    }
}
