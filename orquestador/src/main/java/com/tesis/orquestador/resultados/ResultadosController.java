package com.tesis.orquestador.resultados;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tesis.orquestador.analisis.Analisis;
import com.tesis.orquestador.analisis.AnalisisService;
import com.tesis.orquestador.analisis.EstadoAnalisis;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;
import com.tesis.orquestador.llm.InterpretacionService;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.RecomendacionRepository;
import com.tesis.orquestador.llm.Veredicto;

/** Pantalla 2: hallazgos de Sonar (G1) junto a la interpretación de cada corrida del LLM (G2). */
@Controller
public class ResultadosController {

	private final AnalisisService analisis;
	private final HallazgoRepository hallazgos;
	private final RecomendacionRepository recomendaciones;
	private final InterpretacionService interpretacion;

	public ResultadosController(AnalisisService analisis, HallazgoRepository hallazgos,
			RecomendacionRepository recomendaciones, InterpretacionService interpretacion) {
		this.analisis = analisis;
		this.hallazgos = hallazgos;
		this.recomendaciones = recomendaciones;
		this.interpretacion = interpretacion;
	}

	@GetMapping("/resultados")
	String lista(Model model) {
		List<Analisis> completados = analisis.todos().stream()
				.filter(a -> a.getEstado() == EstadoAnalisis.COMPLETADO)
				.toList();
		model.addAttribute("analisis", completados);
		return "resultados/lista";
	}

	@GetMapping("/resultados/{id}")
	String analisis(@PathVariable Long id, @RequestParam(required = false) String corrida, Model model) {
		Analisis a = analisis.obtener(id);
		List<Hallazgo> lista = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(id);
		Map<Corrida, Map<Long, Recomendacion>> porCorrida = porCorrida(lista);
		Corrida actual = elegir(porCorrida.keySet(), corrida);
		Map<Long, Recomendacion> recs = actual != null ? porCorrida.get(actual) : Map.of();
		var progreso = interpretacion.progreso(id);

		model.addAttribute("a", a);
		model.addAttribute("hallazgos", lista);
		model.addAttribute("corridas", porCorrida.keySet());
		model.addAttribute("actual", actual);
		model.addAttribute("recs", recs);
		model.addAttribute("progreso", progreso);
		model.addAttribute("enCurso", progreso != null && progreso.isEnCurso());
		model.addAttribute("modelos", interpretacion.modelos());
		model.addAttribute("porTipo", contar(lista, Hallazgo::getTipo));
		model.addAttribute("porVeredicto", contarVeredictos(lista.size(), recs));
		model.addAttribute("uso", Uso.de(recs.values()));
		if (porCorrida.size() > 1) {
			Map<Long, Boolean> coinciden = coincidencias(lista, porCorrida.values());
			model.addAttribute("coinciden", coinciden);
			model.addAttribute("enAcuerdo", coinciden.values().stream().filter(Boolean::booleanValue).count());
			model.addAttribute("comparables", coinciden.size());
		}
		return "resultados/analisis";
	}

	@PostMapping("/resultados/{id}/interpretar")
	String interpretar(@PathVariable Long id, @RequestParam String modelo,
			@RequestParam(defaultValue = "false") boolean repetir, RedirectAttributes redirect) {
		int n = interpretacion.interpretar(id, modelo, repetir);
		redirect.addFlashAttribute("aviso", n == 0
				? "No hay hallazgos pendientes para " + modelo + " (o ya hay un lote en curso). "
						+ "Marca «Repetir corrida» para volver a interpretarlos."
				: "Interpretando " + n + " hallazgos con " + modelo + "…");
		redirect.addAttribute("corrida", modelo + "_" + interpretacion.versionPrompt());
		return "redirect:/resultados/" + id;
	}

	@PostMapping("/resultados/{id}/extraer")
	String extraer(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			redirect.addFlashAttribute("aviso", "Hallazgos extraídos: " + analisis.reextraer(id));
		} catch (RuntimeException e) {
			redirect.addFlashAttribute("error", "No se pudieron extraer los hallazgos: " + e.getMessage());
		}
		return "redirect:/resultados/" + id;
	}

	@GetMapping("/hallazgos/{id}")
	String hallazgo(@PathVariable Long id, @RequestParam(required = false) String corrida, Model model) {
		Hallazgo h = hallazgos.findById(id).orElseThrow(() -> new IllegalArgumentException("No existe " + id));
		List<Long> ids = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(h.getAnalisisId()).stream()
				.map(Hallazgo::getId)
				.toList();
		int pos = ids.indexOf(id);
		List<Recomendacion> historial = recomendaciones.findByHallazgoIdInOrderByFechaDesc(List.of(id));
		Map<Corrida, Recomendacion> ultimas = new TreeMap<>();
		historial.stream().filter(Recomendacion::isExitosa).forEach(r -> ultimas.putIfAbsent(Corrida.de(r), r));
		Corrida actual = elegir(ultimas.keySet(), corrida);

		model.addAttribute("h", h);
		model.addAttribute("a", analisis.obtener(h.getAnalisisId()));
		model.addAttribute("corridas", ultimas);
		model.addAttribute("actual", actual);
		model.addAttribute("rec", actual != null ? ultimas.get(actual) : null);
		model.addAttribute("historial", historial);
		model.addAttribute("posicion", pos + 1);
		model.addAttribute("total", ids.size());
		model.addAttribute("anterior", pos > 0 ? ids.get(pos - 1) : null);
		model.addAttribute("siguiente", pos < ids.size() - 1 ? ids.get(pos + 1) : null);
		return "resultados/hallazgo";
	}

	/** Para cada corrida, la respuesta válida más reciente de cada hallazgo. */
	private Map<Corrida, Map<Long, Recomendacion>> porCorrida(List<Hallazgo> lista) {
		if (lista.isEmpty()) {
			return new TreeMap<>();
		}
		return Corrida.ultimasPorCorrida(
				recomendaciones.findByHallazgoIdInOrderByFechaDesc(lista.stream().map(Hallazgo::getId).toList()));
	}

	private static Corrida elegir(Collection<Corrida> corridas, String clave) {
		return corridas.stream()
				.filter(c -> c.getClave().equals(clave))
				.findFirst()
				.orElse(corridas.stream().findFirst().orElse(null));
	}

	/** Hallazgos con respuesta en todas las corridas → true si todas dan el mismo veredicto. */
	private static Map<Long, Boolean> coincidencias(List<Hallazgo> lista, Collection<Map<Long, Recomendacion>> corridas) {
		Map<Long, Boolean> coinciden = new HashMap<>();
		for (Hallazgo h : lista) {
			List<Veredicto> veredictos = corridas.stream()
					.map(m -> m.get(h.getId()))
					.map(r -> r == null ? null : r.getVeredicto())
					.toList();
			if (!veredictos.contains(null)) {
				coinciden.put(h.getId(), veredictos.stream().distinct().count() == 1);
			}
		}
		return coinciden;
	}

	private static Map<String, Long> contar(List<Hallazgo> lista, Function<Hallazgo, String> clave) {
		return lista.stream().collect(Collectors.groupingBy(h -> Objects.requireNonNullElse(clave.apply(h), "—"),
				LinkedHashMap::new, Collectors.counting()));
	}

	private static Map<String, Long> contarVeredictos(int total, Map<Long, Recomendacion> recs) {
		Map<String, Long> conteo = new LinkedHashMap<>();
		for (Veredicto v : Veredicto.values()) {
			conteo.put(v.name(), recs.values().stream().filter(r -> r.getVeredicto() == v).count());
		}
		conteo.put("PENDIENTE", (long) (total - recs.size()));
		return conteo;
	}

	/** Consumo de una corrida G2 (para la tabla de costos de la tesis). */
	public record Uso(long tokensEntrada, long tokensSalida, double costoReferenciaUsd, long latenciaMediaMs,
			String modelos) {

		static Uso de(Collection<Recomendacion> recs) {
			long entrada = recs.stream().mapToLong(r -> Objects.requireNonNullElse(r.getTokensEntrada(), 0)).sum();
			long salida = recs.stream().mapToLong(r -> Objects.requireNonNullElse(r.getTokensSalida(), 0)).sum();
			double costo = recs.stream().mapToDouble(r -> Objects.requireNonNullElse(r.getCostoReferenciaUsd(), 0.0))
					.sum();
			long latencia = (long) recs.stream().mapToLong(r -> Objects.requireNonNullElse(r.getLatenciaMs(), 0L))
					.average().orElse(0);
			String modelos = recs.stream().map(r -> Objects.requireNonNullElse(r.getModeloReal(), r.getModelo()))
					.distinct().sorted().collect(Collectors.joining(", "));
			return new Uso(entrada, salida, costo, latencia, modelos);
		}
	}
}
