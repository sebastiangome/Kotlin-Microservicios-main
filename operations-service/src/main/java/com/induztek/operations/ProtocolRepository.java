package com.induztek.operations;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtocolRepository extends JpaRepository<ProtocolEntity, String> {
    List<ProtocolEntity> findByVisitaId(String visitaId);
    List<ProtocolEntity> findByVisitaIdIn(List<String> visitaIds);
    List<ProtocolEntity> findByEquipoCodigo(String equipoCodigo);
    boolean existsByCodigoProtocolo(String codigoProtocolo);
    boolean existsByVisitaIdAndEquipoCodigo(String visitaId, String equipoCodigo);
}
