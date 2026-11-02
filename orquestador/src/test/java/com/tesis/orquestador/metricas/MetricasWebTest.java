package com.tesis.orquestador.metricas;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.tesis.orquestador.analisis.Analisis;
import com.tesis.orquestador.analisis.AnalisisRepository;
import com.tesis.orquestador.analisis.EstadoAnalisis;
import com.tesis.orquestador.etiquetado.Condicion;
import com.tesis.orquestador.etiquetado.EtiquetaRepository;
import com.tesis.orquestador.etiquetado.EtiquetadoService;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;
import com.tesis.orquestador.llm.ProveedorLlm;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.RecomendacionRepository;
import com.tesis.orquestador.llm.SalidaLlm;
import com.tesis.orquestador.llm.Veredicto;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:metricas", "orquestador.sonar.token=" })
@AutoConfigureMockMvc
class MetricasWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	AnalisisRepository analisis;

	@Autowired
	HallazgoRepository hallazgos;

	@Autowired
	RecomendacionRepository recomendaciones;

	@Autowired
	EtiquetaRepository etiquetas;

	@Autowired
	EtiquetadoService etiquetado;

	@BeforeEach
	void datos() {
		etiquetas.deleteAll();
		recomendaciones.deleteAll();
		hallazgos.deleteAll();
		analisis.deleteAll();
		var a = new Analisis("https://ejemplo/demo.git", "demo");
		a.setTotalHallazgos(2);
		a.finalizar(EstadoAnalisis.COMPLETADO);
		Long id = analisis.save(a).getId();
		List<Hallazgo> hs = hallazgos.saveAll(List.of(
				new Hallazgo(id, "k1", "java:S112", "CODE_SMELL", "MAJOR", "m1", "A.java", 1, 1),
				new Hallazgo(id, "k2", "java:S125", "CODE_SMELL", "MAJOR", "m2", "B.java", 2, 2)));
		etiquetado.guardar(hs.get(0), "E1", Condicion.VERDADERO_POSITIVO, null);
		etiquetado.guardar(hs.get(1), "E1", Condicion.FALSO_POSITIVO, null);
		guardar(hs.get(0), Veredicto.VERDADERO_POSITIVO);
		guardar(hs.get(1), Veredicto.FALSO_POSITIVO);
	}

	@Test
	void muestraLaComparacion() throws Exception {
		mvc.perform(get("/metricas"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("haiku · prompt v1")))
				.andExpect(content().string(containsString("Precisión")))
				.andExpect(content().string(containsString("kappa de Fleiss")));
	}

	@Test
	void exportaUnExcelValido() throws Exception {
		byte[] xlsx = mvc.perform(get("/metricas/excel"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();

		try (var libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertEquals(List.of("Resumen", "Por repositorio", "Detalle hallazgos", "Etiquetas (ficha)", "Llamadas LLM"),
					List.of(libro.getSheetName(0), libro.getSheetName(1), libro.getSheetName(2),
							libro.getSheetName(3), libro.getSheetName(4)));
			var detalle = libro.getSheet("Detalle hallazgos");
			assertEquals(3, detalle.getPhysicalNumberOfRows()); // cabecera + 2 hallazgos
		}
	}

	private void guardar(Hallazgo h, Veredicto v) {
		var r = new Recomendacion(h.getId(), "claude-code", "haiku", "v1", "prompt");
		r.completar(new SalidaLlm(v, 0.9, "j", "e", "r", ""),
				new ProveedorLlm.RespuestaLlm("{}", "claude-haiku", 100, 50, 0.01, 1000));
		recomendaciones.save(r);
	}
}
