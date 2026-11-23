package com.induztek.catalog;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.UUID;

@Configuration
public class CatalogSeed {
    @Bean
    ApplicationRunner seedCatalog(EquipoRepository equipos, InstrumentoRepository instrumentos) {
        return args -> {
            // Planta Lampa
            seedEquipo(equipos, "TR-500KVA-01", "TAG-TR-01", "Transformador de Poder", "Planta Lampa", "Subestación Principal Norte", "WEG", "TP-500", "SN-2024-991", "500 kVA", "2020", "23 kV / 380 V", "1", "Dyn11", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "GEN-300KVA-01", "TAG-GEN-01", "Generador Diésel", "Planta Lampa", "Sala de Generación Auxiliar", "Caterpillar", "C9-300", "SN-2023-104", "300 kVA", "2023", "380 V", "N/A", "Estrella", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "INT-380V-01", "TAG-INT-01", "Interruptor de Baja Tensión", "Planta Lampa", "Tablero General TGD-01", "Schneider Electric", "Masterpact NW16", "SN-2022-550", "380 V / 1600 A", "2022", "380 V", "N/A", "N/A", "REGISTRADO_TERRENO");

            // Minera Escondida
            seedEquipo(equipos, "INT-23KV-02", "TAG-INT-02", "Interruptor de Poder", "Minera Escondida", "Subestación Escondida Alta", "ABB", "HD4", "SN-2024-992", "23 kV", "2024", "23 kV", "N/A", "N/A", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "CEL-23KV-01", "TAG-CEL-01", "Celda de Media Tensión", "Minera Escondida", "Sala Eléctrica Chancador", "Siemens", "NXAIR 24kV", "SN-2023-781", "23 kV / 1250 A", "2023", "23 kV", "N/A", "N/A", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "TR-2MVA-02", "TAG-TR-02", "Transformador Seco Resina", "Minera Escondida", "Subestación Molienda 2", "Schneider Electric", "Trihal 2000", "SN-2021-332", "2 MVA", "2021", "23 kV / 400 V", "2", "Dyn11", "REGISTRADO_TERRENO");

            // Codelco Ventanas
            seedEquipo(equipos, "CAB-MT-03", "TAG-CAB-03", "Cable de Poder", "Codelco Ventanas", "Subestación Ventanas 66kV", "Prysmian", "XLPE 15kV", "SN-2024-993", "15 kV", "2024", "15 kV", "N/A", "N/A", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "TRAFO-66KV-01", "TAG-TR-66", "Transformador 66/13.8kV", "Codelco Ventanas", "Patio de Alta Tensión", "ABB", "ONAF 15MVA", "SN-2020-881", "15 MVA", "2020", "66 kV / 13.8 kV", "3", "YNd11", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "REL-SEL751-01", "TAG-REL-01", "Relé Protecciones Feeder", "Codelco Ventanas", "Sala de Control Fundición", "SEL", "SEL-751A", "SN-2023-409", "13.8 kV", "2023", "110 Vca", "N/A", "N/A", "REGISTRADO_TERRENO");

            // Minera Los Pelambres
            seedEquipo(equipos, "REL-51-04", "TAG-REL-04", "Relé de Protección", "Minera Los Pelambres", "Subestación Cordillera", "Siemens", "SIPROTEC 5", "SN-2024-994", "380 V", "2024", "110 Vcc", "N/A", "N/A", "REGISTRADO_TERRENO");
            seedEquipo(equipos, "MOT-2.5MW-01", "TAG-MOT-01", "Motor Sincrónico Molino", "Minera Los Pelambres", "Edificio Molienda SAG", "GE Power", "MS-2500", "SN-2019-112", "2.5 MW / 6.6 kV", "2019", "6.6 kV", "N/A", "Estrella", "REGISTRADO_TERRENO");

            // Seed Instrumentos
            seedInstrumento(instrumentos, "INS-MEG-01", "Megóhmetro 5kV", "Megger", "MIT515", "SN-MEG-8810", "2026-12-31", true);
            seedInstrumento(instrumentos, "INS-TTR-02", "Medidor Relación Transformación (TTR)", "Megger", "TTR300", "SN-TTR-4421", "2026-11-30", true);
            seedInstrumento(instrumentos, "INS-MICRO-03", "Micróhmetro de Alta Precisión", "Omicron", "CPC 100", "SN-OMI-9012", "2027-01-15", true);
            seedInstrumento(instrumentos, "INS-HIPOT-04", "Probador Rigidez Dieléctrica Hipot", "Megger", "VLF-28kV", "SN-HIP-1102", "2026-10-20", true);
            seedInstrumento(instrumentos, "INS-MULTI-05", "Multímetro Digital TRMS", "Fluke", "Fluke 87V", "SN-FLK-5541", "2026-12-01", true);
        };
    }

    private void seedEquipo(EquipoRepository repo, String codigo, String tag, String tipo, String cliente, String ubicacion, String marca, String modelo, String serie, String potenciaTension, String anio, String tensiones, String tap, String conexion, String ultimoEstado) {
        if (repo.findByCodigo(codigo).isEmpty()) {
            EquipoEntity eq = new EquipoEntity(
                UUID.randomUUID().toString(),
                codigo,
                tag,
                tipo,
                cliente,
                ubicacion,
                marca,
                modelo,
                serie,
                potenciaTension,
                anio,
                tensiones,
                tap,
                conexion,
                ultimoEstado
            );
            repo.save(eq);
        }
    }

    private void seedInstrumento(InstrumentoRepository repo, String id, String tipo, String marca, String modelo, String numeroSerie, String vencimientoCalibracion, boolean activo) {
        if (!repo.existsById(id)) {
            repo.save(new InstrumentoEntity(id, tipo, marca, modelo, numeroSerie, vencimientoCalibracion, activo));
        }
    }
}
