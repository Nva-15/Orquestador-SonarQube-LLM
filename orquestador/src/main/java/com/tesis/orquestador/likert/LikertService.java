package com.tesis.orquestador.likert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.RecomendacionRepository;
import com.tesis.orquestador.resultados.Corrida;

/**
 * Cuestionario de utilidad percibida (OE4), ciego y contrabalanceado: cada participante valora cada hallazgo una
 * sola vez, la mitad en la condición G1 (recomendación de SonarQube) y la otra mitad en G2 (recomendación del
 * LLM). La muestra se divide una vez en dos mitades fijas (A y B); los participantes alternan qué mitad ven en G1,
 * así que con un número par de participantes cada hallazgo recibe tantas valoraciones G1 como G2.
 */
@Service
public class LikertService {

	public static final List<String> EXPERIENCIA = List.of("Menos de 1 año", "1 a 3 años", "3 a 5 años",
			"Más de 5 años");

	private final CuestionarioRepository cuestionarios;
	private final ItemCuestionarioRepository items;
	private final RespuestaLikertRepository respuestas;
	private final ParticipanteRepository participantes;
	private final TurnoRepository turnos;
	private final HallazgoRepository hallazgos;
	private final RecomendacionRepository recomendaciones;

	public LikertService(CuestionarioRepository cuestionarios, ItemCuestionarioRepository items,
			RespuestaLikertRepository respuestas, ParticipanteRepository participantes, TurnoRepository turnos,
			HallazgoRepository hallazgos, RecomendacionRepository recomendaciones) {
		this.cuestionarios = cuestionarios;
		this.items = items;
		this.respuestas = respuestas;
		this.participantes = participantes;
		this.turnos = turnos;
		this.hallazgos = hallazgos;
		this.recomendaciones = recomendaciones;
	}

	/** Corridas G2 disponibles para un análisis: las que tienen al menos una respuesta válida. */
	public List<Corrida> corridas(Long analisisId) {
		return List.copyOf(porCorrida(analisisId).keySet());
	}

	/** Crea un cuestionario con una muestra aleatoria de hallazgos que tienen respuesta en la corrida. */
	@Transactional
	public Cuestionario crear(Long analisisId, String claveCorrida, int tamano) {
		if (tamano < 2) {
			throw new IllegalArgumentException("La muestra necesita al menos 2 hallazgos");
		}
		var porCorrida = porCorrida(analisisId);
		var entrada = porCorrida.entrySet().stream()
				.filter(e -> e.getKey().getClave().equals(claveCorrida))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Esa corrida no tiene respuestas en este análisis"));
		Map<Long, Recomendacion> recs = entrada.getValue();
		long semilla = new Random().nextLong();
		List<Hallazgo> candidatos = new ArrayList<>(hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(analisisId)
				.stream().filter(h -> recs.containsKey(h.getId())).toList());
		Collections.shuffle(candidatos, new Random(semilla));
		List<Hallazgo> muestra = candidatos.subList(0, Math.min(tamano, candidatos.size()));

		Cuestionario c = cuestionarios.save(new Cuestionario(analisisId, entrada.getKey().modelo(),
				entrada.getKey().versionPrompt(), semilla));
		for (Hallazgo h : muestra) {
			Recomendacion r = recs.get(h.getId());
			items.save(new ItemCuestionario(c.getId(), h.getId(), r.getId(), textoG1(h), textoG2(r),
					r.getCodigoCorregido()));
		}
		return c;
	}

	static String textoG1(Hallazgo h) {
		String nombre = h.getReglaNombre() == null ? "" : h.getReglaNombre().strip();
		String descripcion = h.getReglaDescripcion() == null ? "" : h.getReglaDescripcion().strip();
		return (nombre + "\n\n" + descripcion).strip();
	}

	static String textoG2(Recomendacion r) {
		return (r.getExplicacion().strip() + "\n\n" + r.getRecomendacion().strip()).strip();
	}

	public List<Cuestionario> todos() {
		return cuestionarios.findAllByOrderByCreadoDesc();
	}

	public Cuestionario obtener(Long id) {
		return cuestionarios.findById(id).orElseThrow(() -> new IllegalArgumentException("No existe el cuestionario " + id));
	}

	public List<ItemCuestionario> items(Long cuestionarioId) {
		return items.findByCuestionarioIdOrderByIdAsc(cuestionarioId);
	}

	/**
	 * Orden y condición de cada ítem para un participante (reproducible). Mitades fijas A/B del cuestionario;
	 * los turnos pares ven A en G1 y B en G2, los impares al revés. El orden de presentación es aleatorio y propio
	 * de cada participante.
	 */
	@Transactional
	public List<Asignado> asignacion(Cuestionario c, String participante) {
		List<ItemCuestionario> lista = new ArrayList<>(items(c.getId()));
		Collections.shuffle(lista, new Random(c.getSemilla()));
		int mitadA = (lista.size() + 1) / 2;
		boolean turnoPar = turno(c.getId(), participante) % 2 == 0;
		List<Asignado> asignados = new ArrayList<>();
		for (int i = 0; i < lista.size(); i++) {
			boolean enA = i < mitadA;
			asignados.add(new Asignado(lista.get(i), enA == turnoPar ? "G1" : "G2"));
		}
		Collections.shuffle(asignados, new Random(c.getSemilla() ^ (31L * participante.hashCode())));
		return asignados;
	}

	private int turno(Long cuestionarioId, String participante) {
		return turnos.findByCuestionarioIdAndParticipante(cuestionarioId, participante)
				.orElseGet(() -> turnos.save(new Turno(cuestionarioId, participante,
						turnos.countByCuestionarioId(cuestionarioId))))
				.getNumero();
	}

	public Map<Long, RespuestaLikert> respuestasDe(Long cuestionarioId, String participante) {
		return respuestas.findByCuestionarioIdAndParticipante(cuestionarioId, participante).stream()
				.collect(Collectors.toMap(RespuestaLikert::getItemId, Function.identity()));
	}

	public Optional<Asignado> siguientePendiente(Cuestionario c, String participante) {
		Map<Long, RespuestaLikert> hechas = respuestasDe(c.getId(), participante);
		return asignacion(c, participante).stream().filter(a -> !hechas.containsKey(a.item().getId())).findFirst();
	}

	public void guardar(Cuestionario c, Long itemId, String participante, int claridad, int pertinencia,
			int accionabilidad, String comentario) {
		Asignado a = asignacion(c, participante).stream()
				.filter(x -> x.item().getId().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("El ítem no pertenece al cuestionario"));
		RespuestaLikert r = respuestas.findByItemIdAndParticipante(itemId, participante)
				.orElseGet(() -> new RespuestaLikert(a.item(), participante, a.condicion()));
		r.valorar(claridad, pertinencia, accionabilidad, comentario);
		respuestas.save(r);
	}

	public Optional<Participante> participante(String codigo) {
		return participantes.findById(codigo);
	}

	public void registrarParticipante(String codigo, String experiencia) {
		if (!EXPERIENCIA.contains(experiencia)) {
			throw new IllegalArgumentException("Elige tu experiencia con Java");
		}
		Participante p = participantes.findById(codigo).orElseGet(() -> new Participante(codigo, experiencia));
		p.setExperiencia(experiencia);
		participantes.save(p);
	}

	public String respuestasCsv(Long cuestionarioId) {
		Cuestionario c = obtener(cuestionarioId);
		Map<Long, Hallazgo> porId = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(c.getAnalisisId())
				.stream().collect(Collectors.toMap(Hallazgo::getId, Function.identity()));
		Map<String, String> experiencia = participantes.findAll().stream()
				.collect(Collectors.toMap(Participante::getCodigo, p -> p.getExperiencia() == null ? "" : p.getExperiencia()));
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("cuestionario;corrida_g2;participante;experiencia;hallazgo;regla;condicion;claridad;pertinencia;")
				.append("accionabilidad;media;comentario;fecha\n");
		for (RespuestaLikert r : respuestas.findByCuestionarioIdOrderByParticipanteAscItemIdAsc(cuestionarioId)) {
			Hallazgo h = porId.get(r.getHallazgoId());
			csv.append(String.join(";", List.of(String.valueOf(c.getId()), c.getEtiquetaCorrida(),
					celda(r.getParticipante()), celda(experiencia.get(r.getParticipante())),
					String.valueOf(r.getHallazgoId()), h == null ? "" : h.getRegla(), r.getCondicion(),
					String.valueOf(r.getClaridad()), String.valueOf(r.getPertinencia()),
					String.valueOf(r.getAccionabilidad()), String.format("%.2f", r.getMedia()).replace('.', ','),
					celda(r.getComentario()), r.getFecha() == null ? "" : r.getFecha().toString())))
					.append('\n');
		}
		return csv.toString();
	}

	private Map<Corrida, Map<Long, Recomendacion>> porCorrida(Long analisisId) {
		List<Long> ids = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(analisisId).stream()
				.map(Hallazgo::getId).toList();
		return ids.isEmpty() ? Map.of()
				: Corrida.ultimasPorCorrida(recomendaciones.findByHallazgoIdInOrderByFechaDesc(ids));
	}

	private static String celda(String valor) {
		if (valor == null) {
			return "";
		}
		String v = valor.replace("\r", " ").replace("\n", " ");
		return v.contains(";") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
	}

	/** Ítem tal como lo ve un participante: con la condición que le tocó. */
	public record Asignado(ItemCuestionario item, String condicion) {

		/** Texto de la recomendación que se muestra, sin indicar su origen. */
		public String getTexto() {
			return "G1".equals(condicion) ? item.getTextoG1() : item.getTextoG2();
		}

		public String getCodigo() {
			return "G1".equals(condicion) ? null : item.getCodigoG2();
		}
	}
}
