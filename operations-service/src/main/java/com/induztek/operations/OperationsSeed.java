package com.induztek.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OperationsSeed {

    @Bean
    ApplicationRunner seedOperations(
            VisitRepository visits,
            ProtocolRepository protocols,
            TestRepository tests,
            ActaRepository actas,
            ObjectMapper mapper) {
        return args -> {
            if (visits.count() == 0) {
                // Seed Visitas
                VisitEntity v1 = createVisit(visits, mapper, "V-001", "OT-2024-001", "2026-11-20", "08:30", "Planta Lampa", "Subestación Principal Norte", "PRESENCIAL", "Carlos Técnico Terreno", List.of("Carlos Técnico Terreno", "Pedro Morales"), "22 °C", "45%");
                VisitEntity v2 = createVisit(visits, mapper, "V-002", "OT-2024-002", "2026-11-21", "09:00", "Minera Escondida", "Subestación Escondida Alta", "PRESENCIAL", "Carlos Técnico Terreno", List.of("Carlos Técnico Terreno", "Luis Fuentes"), "18 °C", "35%");
                VisitEntity v3 = createVisit(visits, mapper, "V-003", "OT-2024-003", "2026-11-22", "10:00", "Codelco Ventanas", "Subestación Ventanas 66kV", "PRESENCIAL", "Carlos Técnico Terreno", List.of("Carlos Técnico Terreno", "Andrea Silva"), "20 °C", "60%");
                v3.close();
                visits.save(v3);

                VisitEntity v4 = createVisit(visits, mapper, "V-004", "OT-2024-004", "2026-11-23", "08:00", "Minera Los Pelambres", "Subestación Cordillera", "PRESENCIAL", "Carlos Técnico Terreno", List.of("Carlos Técnico Terreno"), "15 °C", "40%");

                // Seed Protocolos
                createProtocol(protocols, mapper, "P-001", "PROT-2024-001", v1.getId(), "OT-2024-001", "TR-500KVA-01", "Transformador de Poder", "2026-11-20 09:15", "FORMALIZADO", "CONFORME", "Carlos Técnico Terreno", "Carlos Técnico Terreno", "Pruebas de aislamiento y relación de transformación transformador WEG 500kVA.", "Megóhmetro 5kV Megger MIT515", true, "22 °C", "45%", true, "Administrador Induztek", "2026-11-20 14:00", "Resistencia de aislamiento > 2000 MΩ. Equipo en condición operativa.");
                createProtocol(protocols, mapper, "P-002", "PROT-2024-002", v1.getId(), "OT-2024-001", "GEN-300KVA-01", "Generador Diésel", "2026-11-20 11:30", "REGISTRADO_TERRENO", "CONFORME", "Carlos Técnico Terreno", "Carlos Técnico Terreno", "Inspección de bobinados y prueba en vacío generador Caterpillar.", "Multímetro Digital TRMS Fluke 87V", true, "23 °C", "44%", false, null, null, "Tensión en vacío 380V simétrica en las 3 fases.");

                createProtocol(protocols, mapper, "P-003", "PROT-2024-003", v2.getId(), "OT-2024-002", "INT-23KV-02", "Interruptor de Poder", "2026-11-21 10:00", "FORMALIZADO", "OBSERVADO", "Carlos Técnico Terreno", "Carlos Técnico Terreno", "Prueba de resistencia de contactos microhmetro 100A en interruptor ABB HD4.", "Micróhmetro de Alta Precisión Omicron CPC 100", true, "18 °C", "35%", true, "Administrador Induztek", "2026-11-21 16:30", "Fase B presenta resistencia de contacto de 42 µΩ (límite 40 µΩ). Se recomienda mantenimiento.");
                createProtocol(protocols, mapper, "P-004", "PROT-2024-004", v2.getId(), "OT-2024-002", "CEL-23KV-01", "Celda de Media Tensión", "2026-11-21 12:45", "REGISTRADO_TERRENO", "CONFORME", "Carlos Técnico Terreno", "Carlos Técnico Terreno", "Inspección visual, reapriete de barras y verificación de enclavamientos.", "Multímetro Digital TRMS Fluke 87V", true, "19 °C", "36%", false, null, null, "Barras en excelente estado. Torque verificado según norma.");

                createProtocol(protocols, mapper, "P-005", "PROT-2024-005", v3.getId(), "OT-2024-003", "CAB-MT-03", "Cable de Poder", "2026-11-22 11:00", "FORMALIZADO", "CONFORME", "Carlos Técnico Terreno", "Carlos Técnico Terreno", "Ensayos VLF y rigidez dieléctrica en cable XLPE 15kV.", "Probador Rigidez Dieléctrica Hipot Megger VLF-28kV", true, "20 °C", "60%", true, "Administrador Induztek", "2026-11-22 17:00", "Sin corriente de fuga apreciable durante 15 min a 18kV VLF.");

                createProtocol(protocols, mapper, "P-006", "PROT-2024-006", v4.getId(), "OT-2024-004", "REL-51-04", "Relé de Protección", "2026-11-23 09:30", "REGISTRADO_TERRENO", "CONFORME", "Carlos Técnico Terreno", "Carlos Técnico Terreno", "Prueba de inyección secundaria y curvas de tiempo inverso Siemens SIPROTEC 5.", "Micróhmetro de Alta Precisión Omicron CPC 100", true, "15 °C", "40%", false, null, null, "Curva 51/51N verificada. Tiempos de disparo dentro de ±3%.");

                // Seed Pruebas
                createTest(tests, mapper, "PRUEBA-001", "P-001", "OT-2024-001", "TR-500KVA-01", "Medición de Resistencia de Aislamiento", "Subestación Principal Norte", "2026-11-20 09:30", "REALIZADA", null, "CONFORME", "INS-MEG-01", "Megóhmetro 5kV Megger MIT515", "SN-MEG-8810", "2026-12-31", true, "22 °C", "45%", "R1min = 2500 MΩ, DAR = 1.45, PI = 2.1.", 2.5, false);
                createTest(tests, mapper, "PRUEBA-002", "P-001", "OT-2024-001", "TR-500KVA-01", "Relación de Transformación (TTR)", "Subestación Principal Norte", "2026-11-20 10:15", "REALIZADA", null, "CONFORME", "INS-TTR-02", "Medidor Relación Transformación Megger TTR300", "SN-TTR-4421", "2026-11-30", true, "22 °C", "45%", "Desviación máxima entre fases < 0.1%.", 0.1, false);
                createTest(tests, mapper, "PRUEBA-003", "P-003", "OT-2024-002", "INT-23KV-02", "Resistencia de Contactos Microhmetro", "Subestación Escondida Alta", "2026-11-21 10:30", "REALIZADA", null, "OBSERVADO", "INS-MICRO-03", "Micróhmetro Omicron CPC 100", "SN-OMI-9012", "2027-01-15", true, "18 °C", "35%", "Fase A: 35 µΩ, Fase B: 42 µΩ, Fase C: 36 µΩ.", 15.0, false);
                createTest(tests, mapper, "PRUEBA-004", "P-005", "OT-2024-003", "CAB-MT-03", "Rigidez Dieléctrica VLF", "Subestación Ventanas 66kV", "2026-11-22 11:30", "REALIZADA", null, "CONFORME", "INS-HIPOT-04", "Probador Hipot Megger VLF-28kV", "SN-HIP-1102", "2026-10-20", true, "20 °C", "60%", "Prueba VLF 18kV durante 15 minutos sin descargas.", 1.2, false);

                // Seed Actas
                createActa(actas, "ACTA-001", v1.getId(), "OT-2024-001", "Planta Lampa", "Subestación Principal Norte", "2026-11-20", "Carlos Técnico Terreno", "Encargado Planta Lampa", false, "Protocolos de mantenimiento preventivo TR-500KVA-01 y GEN-300KVA-01.");
                createActa(actas, "ACTA-002", v2.getId(), "OT-2024-002", "Minera Escondida", "Subestación Escondida Alta", "2026-11-21", "Carlos Técnico Terreno", "Supervisor Minera Escondida", true, "Protocolos INT-23KV-02 (Observado) y CEL-23KV-01 (Conforme).");
                createActa(actas, "ACTA-003", v3.getId(), "OT-2024-003", "Codelco Ventanas", "Subestación Ventanas 66kV", "2026-11-22", "Carlos Técnico Terreno", "Gerente Codelco Ventanas", false, "Protocolo de rigidez dieléctrica CAB-MT-03.");
            }
        };
    }

    private VisitEntity createVisit(VisitRepository repo, ObjectMapper mapper, String id, String ot, String fecha, String hora, String cliente, String instalacion, String modalidad, String ing, List<String> tecnicos, String temp, String humedad) {
        String tecnicosJson = ProtocolEntity.serialize(mapper, tecnicos);
        OperationsController.VisitRequest req = new OperationsController.VisitRequest(id, ot, fecha, hora, cliente, instalacion, modalidad, ing, tecnicosJson, temp, humedad, "ENVIADO");
        return repo.save(new VisitEntity(req));
    }

    private void createProtocol(ProtocolRepository repo, ObjectMapper mapper, String id, String codigo, String visitaId, String ot, String equipoCodigo, String tipoEquipo, String fechaHora, String estado, String resultado, String tecnico, String tecnicoCreador, String desc, String instrumento, boolean calibVigente, String temp, String humedad, boolean actaEntregada, String formalizadoPor, String fechaFormalizacion, String obs) {
        OperationsController.ProtocolRequest req = new OperationsController.ProtocolRequest(
                id, codigo, visitaId, ot, equipoCodigo, tipoEquipo, fechaHora, estado, resultado,
                tecnico, tecnicoCreador, desc, instrumento, calibVigente, temp, humedad, actaEntregada,
                formalizadoPor, fechaFormalizacion, "ENVIADO", obs, List.of(), List.of(), List.of()
        );
        repo.save(new ProtocolEntity(req, mapper));
    }

    private void createTest(TestRepository repo, ObjectMapper mapper, String id, String protocoloId, String ot, String equipoCodigo, String tipoPrueba, String ubicacion, String fechaHora, String estadoPrueba, String motivo, String resultado, String instId, String inst, String instSerie, String instCalib, boolean calibVigente, String temp, String humedad, String obs, Double varHist, boolean superaVar) {
        OperationsController.TestRequest req = new OperationsController.TestRequest(
                id, protocoloId, ot, equipoCodigo, tipoPrueba, ubicacion, fechaHora, estadoPrueba,
                motivo, resultado, instId, inst, instSerie, instCalib, calibVigente, temp, humedad,
                List.of(), obs, varHist, superaVar
        );
        repo.save(new TestEntity(req, mapper));
    }

    private void createActa(ActaRepository repo, String id, String visitaId, String ot, String cliente, String instalacion, String fechaEmision, String tecnicoEntrega, String recibidoPor, boolean pendiente, String resumen) {
        OperationsController.ActaRequest req = new OperationsController.ActaRequest(
                id, visitaId, ot, cliente, instalacion, fechaEmision, tecnicoEntrega, recibidoPor, pendiente, resumen, "ENVIADO"
        );
        repo.save(new ActaEntity(req));
    }
}
