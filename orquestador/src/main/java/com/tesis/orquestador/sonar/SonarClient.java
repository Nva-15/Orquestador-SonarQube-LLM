package com.tesis.orquestador.sonar;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.tesis.orquestador.OrquestadorProperties;

/** Acceso mínimo a la Web API de SonarQube (los POST aceptan los parámetros en la query). */
@Component
public class SonarClient {

	private final RestClient http;

	public SonarClient(OrquestadorProperties props) {
		var sonar = props.sonar();
		this.http = RestClient.builder().baseUrl(sonar.url())
				.defaultHeaders(h -> {
					if (sonar.tieneToken()) {
						h.set(HttpHeaders.AUTHORIZATION, "Bearer " + sonar.token());
					}
				})
				.build();
	}

	public boolean existeProyecto(String projectKey) {
		Map<?, ?> r = get("/api/projects/search?projects={k}", projectKey);
		return !((List<?>) r.get("components")).isEmpty();
	}

	public void crearProyecto(String projectKey, String nombre) {
		post("/api/projects/create?project={k}&name={n}", projectKey, nombre);
	}

	public Optional<String> buscarPerfilJava(String nombre) {
		Map<?, ?> r = get("/api/qualityprofiles/search?language=java");
		return ((List<?>) r.get("profiles")).stream()
				.map(Map.class::cast)
				.filter(p -> nombre.equals(p.get("name")))
				.map(p -> (String) p.get("key"))
				.findFirst();
	}

	public String crearPerfilJava(String nombre) {
		Map<?, ?> r = post("/api/qualityprofiles/create?language=java&name={n}", nombre);
		return (String) ((Map<?, ?>) r.get("profile")).get("key");
	}

	public void activarRegla(String perfilKey, String regla) {
		post("/api/qualityprofiles/activate_rule?key={p}&rule={r}", perfilKey, regla);
	}

	public void asignarPerfil(String projectKey, String nombrePerfil) {
		post("/api/qualityprofiles/add_project?language=java&project={k}&qualityProfile={q}", projectKey,
				nombrePerfil);
	}

	/** Estado de la tarea del Compute Engine: PENDING, IN_PROGRESS, SUCCESS, FAILED, CANCELED. */
	public TareaCe tareaCe(String ceTaskId) {
		Map<?, ?> task = (Map<?, ?>) get("/api/ce/task?id={id}", ceTaskId).get("task");
		return new TareaCe((String) task.get("status"), (String) task.get("errorMessage"));
	}

	/** Issues abiertos del proyecto (todas las páginas). */
	public List<Map<?, ?>> issuesAbiertos(String projectKey) {
		return paginar(p -> get("/api/issues/search?components={k}&resolved=false&ps=500&p={p}", projectKey, p),
				"issues");
	}

	/** Security hotspots pendientes de revisar (Sonar 26 informa casi todo como issue, pero se leen ambos). */
	public List<Map<?, ?>> hotspotsPendientes(String projectKey) {
		return paginar(p -> get("/api/hotspots/search?project={k}&status=TO_REVIEW&ps=500&p={p}", projectKey, p),
				"hotspots");
	}

	/** Nombre y descripción (HTML) de una regla. */
	public Regla regla(String clave) {
		Map<?, ?> rule = (Map<?, ?>) get("/api/rules/show?key={k}", clave).get("rule");
		StringBuilder html = new StringBuilder();
		if (rule.get("descriptionSections") instanceof List<?> secciones) {
			for (Object s : secciones) {
				html.append(((Map<?, ?>) s).get("content")).append('\n');
			}
		}
		if (html.isEmpty() && rule.get("htmlDesc") != null) {
			html.append(rule.get("htmlDesc"));
		}
		return new Regla((String) rule.get("name"), html.toString());
	}

	public record Regla(String nombre, String descripcionHtml) {
	}

	public record TareaCe(String estado, String error) {
	}

	private static List<Map<?, ?>> paginar(java.util.function.IntFunction<Map<?, ?>> pagina, String campo) {
		List<Map<?, ?>> todos = new java.util.ArrayList<>();
		for (int p = 1;; p++) {
			Map<?, ?> r = pagina.apply(p);
			List<?> elementos = (List<?>) r.get(campo);
			elementos.forEach(e -> todos.add((Map<?, ?>) e));
			Map<?, ?> paging = (Map<?, ?>) r.get("paging");
			int total = paging != null ? ((Number) paging.get("total")).intValue() : todos.size();
			if (elementos.isEmpty() || todos.size() >= total || todos.size() >= 10_000) {
				return todos;
			}
		}
	}

	private Map<?, ?> get(String uri, Object... vars) {
		return llamar(() -> http.get().uri(uri, vars).retrieve().body(Map.class));
	}

	private Map<?, ?> post(String uri, Object... vars) {
		return llamar(() -> http.post().uri(uri, vars).retrieve().body(Map.class));
	}

	private static Map<?, ?> llamar(java.util.function.Supplier<Map<?, ?>> llamada) {
		try {
			Map<?, ?> r = llamada.get();
			return r != null ? r : Map.of();
		} catch (RestClientResponseException e) {
			throw new SonarException("Sonar respondió " + e.getStatusCode().value() + ": "
					+ e.getResponseBodyAsString(), e);
		}
	}
}
