package com.tesis.orquestador.analisis;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.tesis.orquestador.OrquestadorProperties;
import com.tesis.orquestador.escaneo.EscanerDocker;
import com.tesis.orquestador.escaneo.EspacioTrabajo;
import com.tesis.orquestador.escaneo.TipoBuild;
import com.tesis.orquestador.hallazgo.ExtractorHallazgos;
import com.tesis.orquestador.sonar.PerfilReglasService;
import com.tesis.orquestador.sonar.SonarClient;

/** Orquesta un análisis G1: copia de trabajo → perfil de reglas → compilación → scanner → Sonar CE. */
@Service
public class AnalisisService {

	private static final Logger LOG = LoggerFactory.getLogger(AnalisisService.class);
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final Duration ESPERA_CE = Duration.ofMinutes(15);

	private final AnalisisRepository repo;
	private final ExecutorService ejecutor;
	private final EspacioTrabajo espacio;
	private final EscanerDocker escaner;
	private final SonarClient sonar;
	private final PerfilReglasService perfil;
	private final ExtractorHallazgos extractor;
	private final OrquestadorProperties props;

	public AnalisisService(AnalisisRepository repo, @Qualifier("ejecutorAnalisis") ExecutorService ejecutor,
			EspacioTrabajo espacio,
			EscanerDocker escaner, SonarClient sonar, PerfilReglasService perfil, ExtractorHallazgos extractor,
			OrquestadorProperties props) {
		this.repo = repo;
		this.ejecutor = ejecutor;
		this.espacio = espacio;
		this.escaner = escaner;
		this.sonar = sonar;
		this.perfil = perfil;
		this.extractor = extractor;
		this.props = props;
	}

	/** Valida la entrada, registra el análisis y lo encola. */
	public Analisis iniciar(String origen, String clave) {
		String o = origen == null ? "" : origen.trim().replaceAll("^\"|\"$", "");
		if (o.isEmpty()) {
			throw new IllegalArgumentException("Indica la ruta del repositorio o su URL git");
		}
		if (!props.sonar().tieneToken()) {
			throw new IllegalStateException(
					"Falta la variable de entorno SONAR_TOKEN: defínela y reinicia el orquestador");
		}
		String k = clave == null || clave.isBlank() ? EspacioTrabajo.claveDesde(o) : clave.trim();
		if (!k.matches("[a-zA-Z0-9._-]*[a-zA-Z._-][a-zA-Z0-9._-]*")) {
			throw new IllegalArgumentException("Clave de proyecto no válida (letras, números, '.', '_' o '-'): " + k);
		}
		Analisis a = repo.save(new Analisis(o, k));
		ejecutor.submit(() -> ejecutar(a.getId()));
		return a;
	}

	/** Vuelve a leer de Sonar los hallazgos de un análisis completado (p. ej. uno anterior a la extracción). */
	public int reextraer(Long id) {
		Analisis a = obtener(id);
		if (a.getEstado() != EstadoAnalisis.COMPLETADO) {
			throw new IllegalStateException("El análisis no está completado");
		}
		var log = new Bitacora(a);
		a.setTotalHallazgos(extractor.extraer(a.getId(), a.getProjectKey(), log));
		repo.save(a);
		return a.getTotalHallazgos();
	}

	public List<Analisis> todos() {
		return repo.findAllByOrderByCreadoDesc();
	}

	public Analisis obtener(Long id) {
		return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("No existe el análisis " + id));
	}

	/** Un reinicio de la app corta los análisis en curso: se marcan como error para no dejarlos colgados. */
	@EventListener(ApplicationReadyEvent.class)
	void cerrarInterrumpidos() {
		for (Analisis a : repo.findByEstadoNotIn(List.of(EstadoAnalisis.COMPLETADO, EstadoAnalisis.ERROR))) {
			a.registrar("Interrumpido por reinicio del orquestador");
			a.finalizar(EstadoAnalisis.ERROR);
			repo.save(a);
		}
	}

	void ejecutar(Long id) {
		Analisis a = obtener(id);
		var log = new Bitacora(a);
		try {
			paso(a, EstadoAnalisis.PREPARANDO);
			Path copia = espacio.preparar(a.getOrigen(), a.getProjectKey(), log);

			perfil.asegurarPerfil(log);
			if (!sonar.existeProyecto(a.getProjectKey())) {
				log.accept("Creando proyecto '" + a.getProjectKey() + "' en Sonar");
				sonar.crearProyecto(a.getProjectKey(), a.getProjectKey());
			}
			sonar.asignarPerfil(a.getProjectKey(), perfil.nombrePerfil());
			a.setPerfilReglas(perfil.nombrePerfil());
			log.accept("Perfil asignado: " + perfil.nombrePerfil());

			TipoBuild tipo = TipoBuild.detectar(copia);
			a.setTipoBuild(tipo.etiqueta());
			paso(a, EstadoAnalisis.COMPILANDO);
			boolean compilado = escaner.compilar(copia, tipo, log);

			paso(a, EstadoAnalisis.ESCANEANDO);
			a.setCeTaskId(escaner.escanear(copia, a.getProjectKey(), tipo, compilado, log));

			paso(a, EstadoAnalisis.PROCESANDO_EN_SONAR);
			esperarCe(a, log);
			a.setTotalHallazgos(extractor.extraer(a.getId(), a.getProjectKey(), log));
			log.accept("Análisis completado: " + a.getTotalHallazgos() + " hallazgos");
			a.finalizar(EstadoAnalisis.COMPLETADO);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.accept("ERROR: análisis interrumpido");
			a.finalizar(EstadoAnalisis.ERROR);
		} catch (Exception e) {
			LOG.warn("Análisis {} fallido", id, e);
			log.accept("ERROR: " + e.getMessage());
			a.finalizar(EstadoAnalisis.ERROR);
		}
		repo.save(a);
	}

	private void esperarCe(Analisis a, Bitacora log) throws InterruptedException {
		Instant limite = Instant.now().plus(ESPERA_CE);
		while (Instant.now().isBefore(limite)) {
			var tarea = sonar.tareaCe(a.getCeTaskId());
			switch (tarea.estado()) {
				case "SUCCESS" -> {
					return;
				}
				case "FAILED", "CANCELED" -> throw new IllegalStateException(
						"Sonar no pudo procesar el análisis (" + tarea.estado() + "): " + tarea.error());
				default -> Thread.sleep(3_000);
			}
		}
		throw new IllegalStateException("Sonar no terminó de procesar en " + ESPERA_CE.toMinutes() + " min");
	}

	private void paso(Analisis a, EstadoAnalisis estado) {
		a.setEstado(estado);
		repo.save(a);
	}

	/** Añade líneas al log del análisis y lo guarda como mucho una vez por segundo (la pantalla lo consulta). */
	private final class Bitacora implements java.util.function.Consumer<String> {

		private final Analisis analisis;
		private Instant ultimoGuardado = Instant.EPOCH;

		Bitacora(Analisis analisis) {
			this.analisis = analisis;
		}

		@Override
		public synchronized void accept(String linea) {
			analisis.registrar("[" + LocalTime.now().format(HORA) + "] " + linea);
			if (Duration.between(ultimoGuardado, Instant.now()).toMillis() >= 1_000) {
				repo.save(analisis);
				ultimoGuardado = Instant.now();
			}
		}
	}
}
