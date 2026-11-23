package com.induztek.security;

import java.util.List;

/** Cuerpo JSON uniforme para todos los errores HTTP de los microservicios. */
public record ApiError(
        String timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldDetail> details) {

    public record FieldDetail(String field, String message) {
    }
}
