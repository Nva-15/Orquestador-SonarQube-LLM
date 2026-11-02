package com.tesis.orquestador.etiquetado;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EtiquetaRepository extends JpaRepository<Etiqueta, Long> {

	Optional<Etiqueta> findByHallazgoIdAndEvaluador(Long hallazgoId, String evaluador);

	List<Etiqueta> findByAnalisisIdAndEvaluador(Long analisisId, String evaluador);

	List<Etiqueta> findByAnalisisIdOrderByEvaluadorAscHallazgoIdAsc(Long analisisId);
}
