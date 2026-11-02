package com.tesis.orquestador.hallazgo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface HallazgoRepository extends JpaRepository<Hallazgo, Long> {

	List<Hallazgo> findByAnalisisIdOrderByArchivoAscLineaInicioAsc(Long analisisId);

	long countByAnalisisId(Long analisisId);

	@Transactional
	void deleteByAnalisisId(Long analisisId);
}
