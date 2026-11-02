package com.tesis.orquestador.metricas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tesis.orquestador.analisis.Analisis;
import com.tesis.orquestador.analisis.AnalisisService;
import com.tesis.orquestador.analisis.EstadoAnalisis;
import com.tesis.orquestador.etiquetado.Condicion;
import com.tesis.orquestador.etiquetado.Etiqueta;
import com.tesis.orquestador.etiquetado.EtiquetaRepository;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;
import com.tesis.orquestador.likert.Cuestionario;
import com.tesis.orquestador.likert.CuestionarioRepository;
import com.tesis.orquestador.likert.RespuestaLikert;
import com.tesis.orquestador.likert.RespuestaLikertRepository;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.RecomendacionRepository;
import com.tesis.orquestador.resultados.Corrida;

/** Reúne hallazgos, etiquetas humanas y respuestas del LLM y calcula las métricas de la pantalla 5. */
@Service
public class MetricasService {

	private final AnalisisService analisis;
	private final HallazgoRepository hallazgos;
	private final EtiquetaRepository etiquetas;
	private final RecomendacionRepository recomendaciones;
	private final CuestionarioRepository cuestionarios;
	private final RespuestaLikertRepository respuestasLikert;

	public MetricasService(AnalisisService analisis, HallazgoRepository hallazgos, EtiquetaRepository etiquetas,
			RecomendacionRepository recomendaciones, CuestionarioRepository cuestionarios,
			RespuestaLikertRepository respuestasLikert) {
		this.analisis = analisis;
		this.hallazgos = hallazgos;
		this.etiquetas = etiquetas;
		this.recomendaciones = recomendaciones;
		this.cuestionarios = cuestionarios;
		this.respuestasLikert = respuestasLikert;
	}

	public List<Analisis> analisisConHallazgos() {
		return analisis.todos().stream()
				.filter(a -> a.getEstado() == EstadoAnalisis.COMPLETADO)
				.filter(a -> a.getTotalHallazgos() != null && a.getTotalHallazgos() > 0)
				.toList();
	}

	/** Informe de los análisis indicados (todos si {@code analisisId} es null). */
	public Informe informe(Long analisisId) {
		List<Analisis> seleccion = analisisConHallazgos().stream()
				.filter(a -> analisisId == null || a.getId().equals(analisisId))
				.toList();
		Datos total = new Datos(List.of());
		List<Repositorio> porRepositorio = new ArrayList<>();
		for (Analisis a : seleccion) {
			Datos d = cargar(a);
			total = total.mas(d);
			porRepositorio.add(new Repositorio(a, CalculoMetricas.etiquetado(d.hallazgos(), d.etiquetas()),
					comparaciones(d)));
		}
		List<Cuestionario> cs = cuestionarios.findByAnalisisIdIn(seleccion.stream().map(Analisis::getId).toList());
		Map<Long, String> corridaDe = cs.stream()
				.collect(Collectors.toMap(Cuestionario::getId, Cuestionario::getEtiquetaCorrida));
		List<RespuestaLikert> respuestas = cs.isEmpty() ? List.of()
				: respuestasLikert.findByCuestionarioIdIn(corridaDe.keySet());
		List<CalculoMetricas.Likert> likert = respuestas.stream()
				.collect(Collectors.groupingBy(r -> corridaDe.get(r.getCuestionarioId()), java.util.TreeMap::new,
						Collectors.toList()))
				.entrySet().stream()
				.map(e -> CalculoMetricas.likert(e.getKey(), e.getValue()))
				.toList();
		return new Informe(seleccion, CalculoMetricas.etiquetado(total.hallazgos(), total.etiquetas()),
				comparaciones(total), usos(total), porRepositorio, total, likert, respuestas, corridaDe);
	}

	private Datos cargar(Analisis a) {
		List<Hallazgo> hs = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(a.getId());
		List<Recomendacion> recs = hs.isEmpty() ? List.of()
				: recomendaciones.findByHallazgoIdInOrderByFechaDesc(hs.stream().map(Hallazgo::getId).toList());
		Map<Long, List<Etiqueta>> ets = etiquetas.findByAnalisisIdOrderByEvaluadorAscHallazgoIdAsc(a.getId()).stream()
				.filter(e -> e.getCondicion() != null)
				.collect(Collectors.groupingBy(Etiqueta::getHallazgoId));
		return new Datos(List.of(new Parte(a, hs, ets, recs)));
	}

	private static List<CalculoMetricas.Comparacion> comparaciones(Datos d) {
		Map<Long, Condicion> verdad = CalculoMetricas.verdad(d.etiquetas());
		return d.corridas().entrySet().stream()
				.map(e -> CalculoMetricas.comparar(e.getKey(), verdad, e.getValue()))
				.toList();
	}

	private static List<CalculoMetricas.Uso> usos(Datos d) {
		return d.corridas().entrySet().stream()
				.map(e -> CalculoMetricas.uso(e.getKey(), e.getValue().values()))
				.toList();
	}

	record Parte(Analisis analisis, List<Hallazgo> hallazgos, Map<Long, List<Etiqueta>> etiquetas,
			List<Recomendacion> recomendaciones) {
	}

	/** Datos de uno o varios análisis (los ids de hallazgo son únicos entre análisis). */
	public record Datos(List<Parte> partes) {

		Datos mas(Datos otro) {
			List<Parte> todas = new ArrayList<>(partes);
			todas.addAll(otro.partes);
			return new Datos(todas);
		}

		public List<Hallazgo> hallazgos() {
			return partes.stream().flatMap(p -> p.hallazgos().stream()).toList();
		}

		public Map<Long, List<Etiqueta>> etiquetas() {
			return partes.stream().flatMap(p -> p.etiquetas().entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}

		/** Ordenadas por fecha descendente dentro de cada análisis; se reordenan al unir. */
		public Map<Corrida, Map<Long, Recomendacion>> corridas() {
			List<Recomendacion> todas = partes.stream().flatMap(p -> p.recomendaciones().stream())
					.sorted((x, y) -> y.getFecha().compareTo(x.getFecha()))
					.toList();
			return Corrida.ultimasPorCorrida(todas);
		}

		public Analisis analisisDe(Hallazgo h) {
			return partes.stream().filter(p -> p.analisis().getId().equals(h.getAnalisisId()))
					.map(Parte::analisis).findFirst().orElse(null);
		}

		public List<Recomendacion> todasLasLlamadas() {
			return partes.stream().flatMap(p -> p.recomendaciones().stream()).toList();
		}
	}

	public record Repositorio(Analisis analisis, CalculoMetricas.Etiquetado etiquetado,
			List<CalculoMetricas.Comparacion> comparaciones) {
	}

	/**
	 * @param corridaDeCuestionario etiqueta de la corrida G2 de cada cuestionario Likert
	 */
	public record Informe(List<Analisis> analisis, CalculoMetricas.Etiquetado etiquetado,
			List<CalculoMetricas.Comparacion> comparaciones, List<CalculoMetricas.Uso> usos,
			List<Repositorio> porRepositorio, Datos datos, List<CalculoMetricas.Likert> likert,
			List<RespuestaLikert> respuestasLikert, Map<Long, String> corridaDeCuestionario) {
	}
}
