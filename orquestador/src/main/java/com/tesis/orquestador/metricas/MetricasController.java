package com.tesis.orquestador.metricas;

import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Pantalla 5: métricas G1 vs G2 y exportación a Excel. */
@Controller
public class MetricasController {

	private final MetricasService servicio;
	private final ExportadorExcel excel;

	public MetricasController(MetricasService servicio, ExportadorExcel excel) {
		this.servicio = servicio;
		this.excel = excel;
	}

	@GetMapping("/metricas")
	String metricas(@RequestParam(required = false) Long analisis, Model model) {
		var informe = servicio.informe(analisis);
		model.addAttribute("disponibles", servicio.analisisConHallazgos());
		model.addAttribute("seleccionado", analisis);
		model.addAttribute("inf", informe);
		return "metricas/metricas";
	}

	@GetMapping("/metricas/excel")
	ResponseEntity<byte[]> exportar(@RequestParam(required = false) Long analisis) {
		var informe = servicio.informe(analisis);
		String alcance = analisis == null || informe.analisis().isEmpty() ? "todos"
				: informe.analisis().getFirst().getProjectKey();
		String nombre = "metricas-" + alcance + "-" + LocalDate.now() + ".xlsx";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(excel.exportar(informe));
	}
}
