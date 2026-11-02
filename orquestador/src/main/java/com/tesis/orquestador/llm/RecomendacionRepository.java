package com.tesis.orquestador.llm;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecomendacionRepository extends JpaRepository<Recomendacion, Long> {

	List<Recomendacion> findByHallazgoIdInOrderByFechaDesc(Collection<Long> hallazgoIds);

	Optional<Recomendacion> findFirstByHallazgoIdAndVeredictoNotNullOrderByFechaDesc(Long hallazgoId);

	Optional<Recomendacion> findFirstByHallazgoIdOrderByFechaDesc(Long hallazgoId);

	/** Para reanudar un lote: ¿ya hay respuesta válida con este modelo y esta versión de prompt? */
	boolean existsByHallazgoIdAndModeloAndVersionPromptAndVeredictoNotNull(Long hallazgoId, String modelo,
			String versionPrompt);
}
