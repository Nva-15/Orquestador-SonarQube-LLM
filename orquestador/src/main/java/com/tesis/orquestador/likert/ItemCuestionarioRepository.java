package com.tesis.orquestador.likert;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCuestionarioRepository extends JpaRepository<ItemCuestionario, Long> {

	List<ItemCuestionario> findByCuestionarioIdOrderByIdAsc(Long cuestionarioId);
}
