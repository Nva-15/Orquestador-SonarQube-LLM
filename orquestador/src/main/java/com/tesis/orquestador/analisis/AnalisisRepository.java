package com.tesis.orquestador.analisis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalisisRepository extends JpaRepository<Analisis, Long> {

	List<Analisis> findAllByOrderByCreadoDesc();

	List<Analisis> findByEstadoNotIn(List<EstadoAnalisis> estados);
}
