package com.tesis.orquestador.etiquetado;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/** Pantalla 3: etiquetado humano ciego. No muestra ni consulta nada del LLM. */
@Controller
public class EtiquetadoController {

	private static final String COOKIE = "evaluador";

	private final EtiquetadoService servicio;
	private final AnalisisService analisis;
	private final HallazgoRepository hallazgos;

	public EtiquetadoController(EtiquetadoService servicio, AnalisisService analisis, HallazgoRepository hallazgos) {
		this.servicio = servicio;
		this.analisis = analisis;
		this.hallazgos = hallazgos;
	}

	@GetMapping("/etiquetar")
	String inicio(@CookieValue(name = COOKIE, required = false) String cookie, Model model) {
		String evaluador = decodificar(cookie);
		List<Analisis> completados = analisis.todos().stream()
				.filter(a -> a.getEstado() == EstadoAnalisis.COMPLETADO)
				.filter(a -> a.getTotalHallazgos() != null && a.getTotalHallazgos() > 0)
				.toList();
		Map<Long, Integer> hechas = evaluador == null ? Map.of()
				: completados.stream().collect(Collectors.toMap(Analisis::getId,
						a -> servicio.etiquetasDe(a.getId(), evaluador).size()));
		model.addAttribute("evaluador", evaluador);
		model.addAttribute("analisis", completados);
		model.addAttribute("hechas", hechas);
		return "etiquetar/inicio";
	}

	@PostMapping("/etiquetar/evaluador")
	String elegirEvaluador(@RequestParam String codigo, HttpServletResponse respuesta, RedirectAttributes redirect) {
		String c = codigo.strip();
		if (!c.matches(EtiquetadoService.PATRON_EVALUADOR)) {
			redirect.addFlashAttribute("error", "Código no válido: usa letras, números, espacios, '.', '_' o '-' (máx. 40)");
			return "redirect:/etiquetar";
		}
		var cookie = new Cookie(COOKIE, URLEncoder.encode(c, StandardCharsets.UTF_8));
		cookie.setPath("/");
		cookie.setMaxAge(365 * 24 * 3600);
		cookie.setHttpOnly(true);
		respuesta.addCookie(cookie);
		return "redirect:/etiquetar";
	}

	@GetMapping("/etiquetar/{analisisId}")
	String continuar(@PathVariable Long analisisId, @CookieValue(name = COOKIE, required = false) String cookie,
			RedirectAttributes redirect) {
		String evaluador = decodificar(cookie);
		if (evaluador == null) {
			return "redirect:/etiquetar";
		}
		return servicio.siguientePendiente(analisisId, evaluador, null)
				.map(h -> "redirect:/etiquetar/" + analisisId + "/" + h.getId())
				.orElseGet(() -> {
					List<Hallazgo> orden = servicio.ordenPara(analisisId, evaluador);
					redirect.addFlashAttribute("aviso", "Ya etiquetaste todos los hallazgos de este análisis. "
							+ "Puedes revisar o cambiar cualquiera.");
					return orden.isEmpty() ? "redirect:/etiquetar"
							: "redirect:/etiquetar/" + analisisId + "/" + orden.getFirst().getId();
				});
	}

	@GetMapping("/etiquetar/{analisisId}/{hallazgoId}")
	String hallazgo(@PathVariable Long analisisId, @PathVariable Long hallazgoId,
			@CookieValue(name = COOKIE, required = false) String cookie, Model model) {
		String evaluador = decodificar(cookie);
		if (evaluador == null) {
			return "redirect:/etiquetar";
		}
		Hallazgo h = hallazgos.findById(hallazgoId)
				.filter(x -> x.getAnalisisId().equals(analisisId))
				.orElseThrow(() -> new IllegalArgumentException("No existe el hallazgo " + hallazgoId));
		List<Hallazgo> orden = servicio.ordenPara(analisisId, evaluador);
		Map<Long, Etiqueta> hechas = servicio.etiquetasDe(analisisId, evaluador);

		model.addAttribute("evaluador", evaluador);
		model.addAttribute("a", analisis.obtener(analisisId));
		model.addAttribute("h", h);
		model.addAttribute("orden", orden);
		model.addAttribute("hechas", hechas);
		model.addAttribute("posicion", orden.stream().map(Hallazgo::getId).toList().indexOf(hallazgoId) + 1);
		model.addAttribute("total", orden.size());
		model.addAttribute("etiqueta", hechas.get(hallazgoId));
		model.addAttribute("condiciones", Condicion.values());
		model.addAttribute("archivo", servicio.archivoCompleto(h));
		return "etiquetar/hallazgo";
	}

	@PostMapping("/etiquetar/{analisisId}/{hallazgoId}")
	String guardar(@PathVariable Long analisisId, @PathVariable Long hallazgoId,
			@RequestParam(required = false) Condicion condicion, @RequestParam(required = false) String observaciones,
			@CookieValue(name = COOKIE, required = false) String cookie, RedirectAttributes redirect) {
		String evaluador = decodificar(cookie);
		if (evaluador == null) {
			return "redirect:/etiquetar";
		}
		if (condicion == null) {
			redirect.addFlashAttribute("error", "Elige si es un problema real o un falso positivo");
			return "redirect:/etiquetar/" + analisisId + "/" + hallazgoId;
		}
		Hallazgo h = hallazgos.findById(hallazgoId).orElseThrow();
		servicio.guardar(h, evaluador, condicion, observaciones);
		return servicio.siguientePendiente(analisisId, evaluador, hallazgoId)
				.map(s -> "redirect:/etiquetar/" + analisisId + "/" + s.getId())
				.orElseGet(() -> {
					redirect.addFlashAttribute("aviso", "¡Listo! Etiquetaste todos los hallazgos de este análisis.");
					return "redirect:/etiquetar";
				});
	}

	@GetMapping("/etiquetar/{analisisId}/ficha.csv")
	ResponseEntity<String> ficha(@PathVariable Long analisisId) {
		String nombre = "ficha-registro-" + analisis.obtener(analisisId).getProjectKey() + ".csv";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(servicio.fichaCsv(analisisId));
	}

	private static String decodificar(String cookie) {
		if (cookie == null || cookie.isBlank()) {
			return null;
		}
		String e = URLDecoder.decode(cookie, StandardCharsets.UTF_8);
		return e.matches(EtiquetadoService.PATRON_EVALUADOR) ? e : null;
	}
}
