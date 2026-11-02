package com.tesis.orquestador.likert;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

	Optional<Turno> findByCuestionarioIdAndParticipante(Long cuestionarioId, String participante);

	int countByCuestionarioId(Long cuestionarioId);
}
