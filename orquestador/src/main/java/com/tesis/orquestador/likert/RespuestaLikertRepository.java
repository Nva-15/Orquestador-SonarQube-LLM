package com.tesis.orquestador.likert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RespuestaLikertRepository extends JpaRepository<RespuestaLikert, Long> {

	List<RespuestaLikert> findByCuestionarioIdAndParticipante(Long cuestionarioId, String participante);

	Optional<RespuestaLikert> findByItemIdAndParticipante(Long itemId, String participante);

	List<RespuestaLikert> findByCuestionarioIdOrderByParticipanteAscItemIdAsc(Long cuestionarioId);

	List<RespuestaLikert> findByCuestionarioIdIn(Collection<Long> cuestionarioIds);
}
