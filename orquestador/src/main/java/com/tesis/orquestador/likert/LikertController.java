package com.tesis.orquestador.likert;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tesis.orquestador.analisis.Analisis;
import com.tesis.orquestador.analisis.AnalisisService;
import com.tesis.orquestador.analisis.EstadoAnalisis;
import com.tesis.orquestador.etiquetado.EtiquetadoService;
import com.tesis.orquestador.hallazgo.HallazgoRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/** Pantalla 4: cuestionario Likert ciego (utilidad percibida). */
@Controller
public class LikertController {

	private static final String COOKIE = "participante";

	private final LikertService servicio;
	private final AnalisisService analisis;
	private final HallazgoRepository hallazgos;

	public LikertController(LikertService servicio, AnalisisService analisis, HallazgoRepository hallazgos) {
		this.servicio = servicio;
		this.analisis = analisis;
		this.hallazgos = hallazgos;
	}

	@GetMapping("/likert")
	String inicio(@CookieValue(name = COOKIE, required = false) String cookie, Model model) {
		String codigo = decodificar(cookie);
		var participante = codigo == null ? null : servicio.participante(codigo).orElse(null);
		List<Cuestionario> cuestionarios = servicio.todos();
		Map<Long, Analisis> analisisPorId = new LinkedHashMap<>();
		analisis.todos().forEach(a -> analisisPorId.put(a.getId(), a));
		Map<Long, Integer> totales = new LinkedHashMap<>();
		Map<Long, Integer> hechas = new LinkedHashMap<>();
		for (Cuestionario c : cuestionarios) {
			totales.put(c.getId(), servicio.items(c.getId()).size());
			hechas.put(c.getId(), participante == null ? 0 : servicio.respuestasDe(c.getId(), codigo).size());
		}
		model.addAttribute("participante", participante);
		model.addAttribute("codigo", codigo);
		model.addAttribute("experiencias", LikertService.EXPERIENCIA);
		model.addAttribute("cuestionarios", cuestionarios);
		model.addAttribute("analisisPorId", analisisPorId);
		model.addAttribute("totales", totales);
		model.addAttribute("hechas", hechas);
		model.addAttribute("opciones", opcionesDeCreacion());
		return "likert/inicio";
	}

	@PostMapping("/likert/participante")
	String participante(@RequestParam String codigo, @RequestParam(required = false) String experiencia,
			HttpServletResponse respuesta, RedirectAttributes redirect) {
		String c = codigo.strip();
		if (!c.matches(EtiquetadoService.PATRON_EVALUADOR)) {
			redirect.addFlashAttribute("error", "Código no válido: usa letras, números, espacios, '.', '_' o '-' (máx. 40)");
			return "redirect:/likert";
		}
		try {
			servicio.registrarParticipante(c, experiencia);
		} catch (IllegalArgumentException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/likert";
		}
		var cookie = new Cookie(COOKIE, URLEncoder.encode(c, StandardCharsets.UTF_8));
		cookie.setPath("/");
		cookie.setMaxAge(365 * 24 * 3600);
		cookie.setHttpOnly(true);
		respuesta.addCookie(cookie);
		return "redirect:/likert";
	}

	@PostMapping("/likert/cuestionarios")
	String crear(@RequestParam String opcion, @RequestParam(defaultValue = "10") int tamano,
			RedirectAttributes redirect) {
		try {
			String[] partes = opcion.split("\\|", 2);
			Cuestionario c = servicio.crear(Long.valueOf(partes[0]), partes[1], tamano);
			redirect.addFlashAttribute("aviso", "Cuestionario #" + c.getId() + " creado con "
					+ servicio.items(c.getId()).size() + " hallazgos.");
		} catch (RuntimeException e) {
			redirect.addFlashAttribute("error", "No se pudo crear el cuestionario: " + e.getMessage());
		}
		return "redirect:/likert";
	}

	@GetMapping("/likert/{id}")
	String continuar(@PathVariable Long id, @CookieValue(name = COOKIE, required = false) String cookie,
			RedirectAttributes redirect) {
		String codigo = registrado(cookie);
		if (codigo == null) {
			return "redirect:/likert";
		}
		Cuestionario c = servicio.obtener(id);
		return servicio.siguientePendiente(c, codigo)
				.map(a -> "redirect:/likert/" + id + "/" + a.item().getId())
				.orElseGet(() -> {
					redirect.addFlashAttribute("aviso", "Ya respondiste todo el cuestionario #" + id + ". ¡Gracias!");
					return "redirect:/likert";
				});
	}

	@GetMapping("/likert/{id}/{itemId}")
	String item(@PathVariable Long id, @PathVariable Long itemId,
			@CookieValue(name = COOKIE, required = false) String cookie, Model model) {
		String codigo = registrado(cookie);
		if (codigo == null) {
			return "redirect:/likert";
		}
		Cuestionario c = servicio.obtener(id);
		List<LikertService.Asignado> asignacion = servicio.asignacion(c, codigo);
		int pos = 0;
		LikertService.Asignado actual = null;
		for (int i = 0; i < asignacion.size(); i++) {
			if (asignacion.get(i).item().getId().equals(itemId)) {
				pos = i + 1;
				actual = asignacion.get(i);
			}
		}
		if (actual == null) {
			throw new IllegalArgumentException("El ítem no pertenece al cuestionario");
		}
		var hechas = servicio.respuestasDe(id, codigo);
		model.addAttribute("c", c);
		model.addAttribute("codigo", codigo);
		model.addAttribute("asignado", actual);
		model.addAttribute("h", hallazgos.findById(actual.item().getHallazgoId()).orElseThrow());
		model.addAttribute("respuesta", hechas.get(itemId));
		model.addAttribute("posicion", pos);
		model.addAttribute("total", asignacion.size());
		model.addAttribute("hechas", hechas.size());
		model.addAttribute("anterior", pos > 1 ? asignacion.get(pos - 2).item().getId() : null);
		model.addAttribute("preguntas", PREGUNTAS);
		return "likert/item";
	}

	@PostMapping("/likert/{id}/{itemId}")
	String responder(@PathVariable Long id, @PathVariable Long itemId,
			@RequestParam(required = false) Integer claridad, @RequestParam(required = false) Integer pertinencia,
			@RequestParam(required = false) Integer accionabilidad, @RequestParam(required = false) String comentario,
			@CookieValue(name = COOKIE, required = false) String cookie, RedirectAttributes redirect) {
		String codigo = registrado(cookie);
		if (codigo == null) {
			return "redirect:/likert";
		}
		if (claridad == null || pertinencia == null || accionabilidad == null) {
			redirect.addFlashAttribute("error", "Responde las tres afirmaciones");
			return "redirect:/likert/" + id + "/" + itemId;
		}
		servicio.guardar(servicio.obtener(id), itemId, codigo, claridad, pertinencia, accionabilidad, comentario);
		return "redirect:/likert/" + id;
	}

	@GetMapping("/likert/{id}/respuestas.csv")
	ResponseEntity<String> csv(@PathVariable Long id) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("likert-cuestionario-" + id + ".csv").build().toString())
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(servicio.respuestasCsv(id));
	}

	/** Afirmaciones del cuestionario (dimensiones de utilidad percibida de la tesis). */
	public record Pregunta(String campo, String dimension, String afirmacion) {
	}

	static final List<Pregunta> PREGUNTAS = List.of(
			new Pregunta("claridad", "Claridad", "La recomendación es clara y fácil de entender."),
			new Pregunta("pertinencia", "Pertinencia", "La recomendación es pertinente para este código concreto."),
			new Pregunta("accionabilidad", "Accionabilidad",
					"Con esta recomendación sabría qué hacer para resolver (o descartar) el hallazgo."));

	/** Opciones "análisis · corrida" para crear un cuestionario. */
	private List<String[]> opcionesDeCreacion() {
		List<String[]> opciones = new ArrayList<>();
		for (Analisis a : analisis.todos()) {
			if (a.getEstado() != EstadoAnalisis.COMPLETADO) {
				continue;
			}
			servicio.corridas(a.getId()).forEach(cor -> opciones.add(new String[] {
					a.getId() + "|" + cor.getClave(), a.getProjectKey() + " · " + cor.getEtiqueta() }));
		}
		return opciones;
	}

	private String registrado(String cookie) {
		String codigo = decodificar(cookie);
		return codigo != null && servicio.participante(codigo).isPresent() ? codigo : null;
	}

	private static String decodificar(String cookie) {
		if (cookie == null || cookie.isBlank()) {
			return null;
		}
		String c = URLDecoder.decode(cookie, StandardCharsets.UTF_8);
		return c.matches(EtiquetadoService.PATRON_EVALUADOR) ? c : null;
	}
}
