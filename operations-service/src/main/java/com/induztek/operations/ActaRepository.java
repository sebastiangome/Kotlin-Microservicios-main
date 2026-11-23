package com.induztek.operations;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActaRepository extends JpaRepository<ActaEntity, String> {
    List<ActaEntity> findByVisitaId(String visitaId);
    boolean existsByVisitaId(String visitaId);
}
