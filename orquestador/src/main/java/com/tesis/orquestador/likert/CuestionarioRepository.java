package com.tesis.orquestador.likert;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CuestionarioRepository extends JpaRepository<Cuestionario, Long> {

	List<Cuestionario> findAllByOrderByCreadoDesc();

	List<Cuestionario> findByAnalisisIdIn(Collection<Long> analisisIds);
}
