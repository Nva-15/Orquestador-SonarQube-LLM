package com.tesis.orquestador.analisis;

import java.util.Arrays;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tesis.orquestador.OrquestadorProperties;

@Controller
public class AnalisisController {

	private final AnalisisService servicio;
	private final OrquestadorProperties props;

	public AnalisisController(AnalisisService servicio, OrquestadorProperties props) {
		this.servicio = servicio;
		this.props = props;
	}

	@GetMapping("/")
	String inicio() {
		return "redirect:/analisis";
	}

	@GetMapping("/analisis")
	String nuevo(Model model) {
		model.addAttribute("analisis", servicio.todos());
		model.addAttribute("tokenDefinido", props.sonar().tieneToken());
		model.addAttribute("perfil", props.sonar().perfil());
		return "analisis/nuevo";
	}

	@PostMapping("/analisis")
	String iniciar(@RequestParam String origen, @RequestParam(required = false) String clave,
			RedirectAttributes redirect) {
		try {
			return "redirect:/analisis/" + servicio.iniciar(origen, clave).getId();
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			redirect.addFlashAttribute("origen", origen);
			return "redirect:/analisis";
		}
	}

	@GetMapping("/analisis/{id}")
	String detalle(@PathVariable Long id, Model model) {
		model.addAttribute("a", servicio.obtener(id));
		model.addAttribute("etapas", Arrays.stream(EstadoAnalisis.values())
				.filter(e -> e != EstadoAnalisis.PENDIENTE && e != EstadoAnalisis.ERROR)
				.toList());
		model.addAttribute("sonarUrl", props.sonar().url());
		return "analisis/detalle";
	}
}
