package com.tesis.orquestador.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.tesis.orquestador.OrquestadorProperties;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Interpreta con el LLM los hallazgos de un análisis, de uno en uno. Es reanudable: salta los que ya tienen
 * respuesta válida con el mismo modelo y versión de prompt, y se detiene si se agota el límite de uso del plan.
 */
@Service
public class InterpretacionService {

	private static final Logger LOG = LoggerFactory.getLogger(InterpretacionService.class);
	private static final int INTENTOS = 2;

	private final HallazgoRepository hallazgos;
	private final RecomendacionRepository recomendaciones;
	private final ProveedorLlm proveedor;
	private final PlantillaPrompt plantilla;
	private final ObjectMapper json;
	private final ExecutorService ejecutor;
	private final List<String> modelos;
	private final Map<Long, Progreso> progresos = new ConcurrentHashMap<>();

	public InterpretacionService(HallazgoRepository hallazgos, RecomendacionRepository recomendaciones,
			ProveedorLlm proveedor, PlantillaPrompt plantilla, ObjectMapper json,
			@Qualifier("ejecutorLlm") ExecutorService ejecutor, OrquestadorProperties props) {
		this.hallazgos = hallazgos;
		this.recomendaciones = recomendaciones;
		this.proveedor = proveedor;
		this.plantilla = plantilla;
		this.json = json;
		this.ejecutor = ejecutor;
		this.modelos = props.llm().modelos();
	}

	public List<String> modelos() {
		return modelos;
	}

	public String versionPrompt() {
		return plantilla.version();
	}

	public Progreso progreso(Long analisisId) {
		return progresos.get(analisisId);
	}

	/**
	 * Encola la interpretación de los hallazgos pendientes (o de todos si {@code repetir}: nueva corrida que se
	 * guarda junto a las anteriores). Devuelve cuántos se procesarán.
	 */
	public int interpretar(Long analisisId, String modelo, boolean repetir) {
		if (!modelos.contains(modelo)) {
			throw new IllegalArgumentException("Modelo no permitido: " + modelo);
		}
		Progreso actual = progresos.get(analisisId);
		if (actual != null && actual.isEnCurso()) {
			return 0;
		}
		List<Hallazgo> pendientes = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(analisisId).stream()
				.filter(h -> repetir || !recomendaciones.existsByHallazgoIdAndModeloAndVersionPromptAndVeredictoNotNull(
						h.getId(), modelo, plantilla.version()))
				.toList();
		var progreso = new Progreso(modelo, pendientes.size());
		progresos.put(analisisId, progreso);
		ejecutor.submit(() -> procesar(pendientes, modelo, progreso));
		return pendientes.size();
	}

	private void procesar(List<Hallazgo> pendientes, String modelo, Progreso progreso) {
		try {
			for (Hallazgo h : pendientes) {
				progreso.actual = h.getRegla() + " en " + h.getArchivo();
				if (!interpretarUno(h, modelo, progreso)) {
					return;
				}
			}
			progreso.mensaje = progreso.errores == 0 ? "Completado"
					: "Completado con " + progreso.errores + " errores (vuelve a pulsar para reintentarlos)";
		} finally {
			progreso.actual = null;
			progreso.enCurso = false;
		}
	}

	/** Devuelve false si hay que detener el lote (límite de uso del plan). */
	private boolean interpretarUno(Hallazgo h, String modelo, Progreso progreso) {
		String promptUsuario = plantilla.usuario(h);
		var peticion = new ProveedorLlm.PeticionLlm(modelo, plantilla.sistema(), promptUsuario, plantilla.esquema());
		for (int intento = 1; intento <= INTENTOS; intento++) {
			var r = new Recomendacion(h.getId(), proveedor.nombre(), modelo, plantilla.version(), promptUsuario);
			try {
				var respuesta = proveedor.generar(peticion);
				r.completar(SalidaLlm.validar(respuesta.json(), json), respuesta);
				recomendaciones.save(r);
				progreso.hechos++;
				return true;
			} catch (LlmException e) {
				r.fallar(e.getMessage());
				recomendaciones.save(r);
				if (e.esLimiteDeUso()) {
					progreso.mensaje = "Detenido: se alcanzó el límite de uso del plan. Reanuda más tarde; "
							+ "los hallazgos ya interpretados no se repiten.";
					return false;
				}
				LOG.warn("Hallazgo {} intento {}: {}", h.getId(), intento, e.getMessage());
			} catch (RuntimeException e) {
				r.fallar("Respuesta inválida: " + e.getMessage());
				recomendaciones.save(r);
				LOG.warn("Hallazgo {} intento {}: respuesta inválida", h.getId(), intento, e);
			}
		}
		progreso.errores++;
		return true;
	}

	/** Estado de un lote, consultado por la pantalla mientras se procesa. */
	public static final class Progreso {

		private final String modelo;
		private final int total;
		private volatile int hechos;
		private volatile int errores;
		private volatile boolean enCurso = true;
		private volatile String actual;
		private volatile String mensaje;

		Progreso(String modelo, int total) {
			this.modelo = modelo;
			this.total = total;
		}

		public String getModelo() {
			return modelo;
		}

		public int getTotal() {
			return total;
		}

		public int getHechos() {
			return hechos;
		}

		public int getErrores() {
			return errores;
		}

		public boolean isEnCurso() {
			return enCurso;
		}

		public String getActual() {
			return actual;
		}

		public String getMensaje() {
			return mensaje;
		}

		public int getPorcentaje() {
			return total == 0 ? 100 : (hechos + errores) * 100 / total;
		}
	}
}
