/* Interacción de la interfaz del orquestador (sin dependencias). Todo es mejora progresiva:
 * sin JavaScript las pantallas siguen funcionando con enlaces y formularios normales. */
(function () {
	"use strict";

	function guardar(clave, valor) {
		try { valor == null ? localStorage.removeItem(clave) : localStorage.setItem(clave, valor); } catch (e) { /* sin almacenamiento */ }
	}
	function leer(clave) {
		try { return localStorage.getItem(clave); } catch (e) { return null; }
	}

	/* ---- Tema claro / oscuro / sistema ---- */
	function aplicarTema(tema) {
		if (tema === "light" || tema === "dark") document.documentElement.dataset.theme = tema;
		else delete document.documentElement.dataset.theme;
		document.querySelectorAll("[data-tema]").forEach(function (b) {
			b.setAttribute("aria-pressed", String((b.dataset.tema || "") === (tema || "")));
		});
	}
	document.querySelectorAll("[data-tema]").forEach(function (b) {
		b.addEventListener("click", function () {
			var tema = b.dataset.tema || null;
			guardar("tema", tema);
			aplicarTema(tema);
		});
	});
	aplicarTema(leer("tema"));

	/* ---- Filas de tabla que llevan a un detalle ---- */
	document.querySelectorAll("tr[data-href]").forEach(function (tr) {
		tr.addEventListener("click", function (ev) {
			if (ev.target.closest("a, button, input, select, label")) return;
			if (window.getSelection && String(window.getSelection())) return;
			if (ev.ctrlKey || ev.metaKey) window.open(tr.dataset.href, "_blank");
			else location.href = tr.dataset.href;
		});
	});

	/* ---- Filtro de tablas: búsqueda + chips + selects ---- */
	document.querySelectorAll("[data-filtrar]").forEach(function (barra) {
		var tabla = document.getElementById(barra.dataset.filtrar);
		if (!tabla) return;
		var filas = Array.prototype.slice.call(tabla.querySelectorAll("tbody tr"));
		var buscar = barra.querySelector("input[type=search]");
		var contador = barra.querySelector(".contador");
		var vacio = document.getElementById(barra.dataset.filtrar + "-vacio");
		var estado = {};

		/* Paginación opcional: <div data-paginacion="id-tabla"> con un select de tamaño y un <nav class="paginas"> */
		var pag = document.querySelector('[data-paginacion="' + barra.dataset.filtrar + '"]');
		var clavePagina = "pagina:" + location.pathname + ":" + barra.dataset.filtrar;
		var tamano = 20;
		var pagina = 1;
		if (pag) {
			var guardado = parseInt(leer("por-pagina"), 10);
			var selTamano = pag.querySelector("select");
			if (!isNaN(guardado)) { tamano = guardado; selTamano.value = String(guardado); }
			if (selTamano.value !== String(tamano)) selTamano.value = String(tamano = 20);
			try { pagina = parseInt(sessionStorage.getItem(clavePagina), 10) || 1; } catch (e) { pagina = 1; }
			selTamano.addEventListener("change", function () {
				tamano = parseInt(selTamano.value, 10) || 0;
				guardar("por-pagina", String(tamano));
				pagina = 1;
				aplicar();
			});
			pag.hidden = false;
		}

		function irA(n, enfocar) {
			pagina = n;
			aplicar();
			if (enfocar) tabla.scrollIntoView({ block: "start", behavior: "smooth" });
		}

		function botonPagina(n, texto, etiqueta) {
			var b = document.createElement("button");
			b.type = "button";
			b.textContent = texto;
			b.setAttribute("aria-label", etiqueta || "Página " + n);
			if (n === pagina && !etiqueta) b.setAttribute("aria-current", "page");
			b.addEventListener("click", function () { irA(n, true); });
			return b;
		}

		function dibujarPaginas(total, paginas, desde, hasta) {
			if (!pag) return;
			pag.querySelector(".rango").textContent = total === 0 ? "" : (desde + 1) + "–" + hasta + " de " + total;
			var nav = pag.querySelector(".paginas");
			nav.textContent = "";
			if (paginas <= 1) return;
			if (pagina > 1) nav.appendChild(botonPagina(pagina - 1, "‹", "Página anterior"));
			var ultima = 0;
			for (var n = 1; n <= paginas; n++) {
				if (n === 1 || n === paginas || Math.abs(n - pagina) <= 1) {
					if (ultima && n - ultima > 1) {
						var s = document.createElement("span");
						s.className = "salto";
						s.textContent = "…";
						nav.appendChild(s);
					}
					nav.appendChild(botonPagina(n, String(n)));
					ultima = n;
				}
			}
			if (pagina < paginas) nav.appendChild(botonPagina(pagina + 1, "›", "Página siguiente"));
		}

		function aplicar() {
			var q = buscar ? buscar.value.trim().toLowerCase() : "";
			var coinciden = [];
			filas.forEach(function (tr) {
				var ok = !q || (tr.dataset.texto || tr.textContent).toLowerCase().indexOf(q) >= 0;
				Object.keys(estado).forEach(function (campo) {
					if (ok && estado[campo] && tr.dataset[campo] !== estado[campo]) ok = false;
				});
				tr.hidden = true;
				if (ok) coinciden.push(tr);
			});
			var total = coinciden.length;
			var porPagina = pag && tamano > 0 ? tamano : total || 1;
			var paginas = Math.max(1, Math.ceil(total / porPagina));
			if (pagina > paginas) pagina = paginas;
			if (pagina < 1) pagina = 1;
			var desde = (pagina - 1) * porPagina;
			var hasta = Math.min(total, desde + porPagina);
			for (var i = desde; i < hasta; i++) coinciden[i].hidden = false;
			try { sessionStorage.setItem(clavePagina, String(pagina)); } catch (e) { /* sin almacenamiento */ }
			dibujarPaginas(total, paginas, desde, hasta);
			if (contador) contador.textContent = total === filas.length
				? filas.length + " hallazgos" : total + " de " + filas.length + " hallazgos";
			if (vacio) vacio.hidden = total !== 0;
		}

		barra.querySelectorAll("[data-campo]").forEach(function (control) {
			var campo = control.dataset.campo;
			if (control.tagName === "SELECT") {
				control.addEventListener("change", function () { estado[campo] = control.value; pagina = 1; aplicar(); });
			} else {
				control.addEventListener("click", function () {
					var activo = control.getAttribute("aria-pressed") === "true";
					barra.querySelectorAll('button[data-campo="' + campo + '"]').forEach(function (b) {
						b.setAttribute("aria-pressed", "false");
					});
					var valor = activo ? "" : control.dataset.valor;
					if (valor) control.setAttribute("aria-pressed", "true");
					else { var todos = barra.querySelector('button[data-campo="' + campo + '"][data-valor=""]'); if (todos) todos.setAttribute("aria-pressed", "true"); }
					estado[campo] = valor;
					pagina = 1;
					aplicar();
				});
			}
		});
		if (buscar) buscar.addEventListener("input", function () { pagina = 1; aplicar(); });
		aplicar();
	});

	/* ---- Atajos de teclado: data-atajo="ArrowLeft" / "v" / "1" ---- */
	document.addEventListener("keydown", function (ev) {
		var t = ev.target;
		var escribiendo = t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) && !/^(radio|checkbox)$/.test(t.type));
		if (ev.key === "Enter" && (ev.ctrlKey || ev.metaKey)) {
			var form = t && t.form || document.querySelector("form[data-principal]");
			if (form) { ev.preventDefault(); form.requestSubmit ? form.requestSubmit() : form.submit(); }
			return;
		}
		if (escribiendo || ev.ctrlKey || ev.metaKey || ev.altKey) return;
		if (ev.key === "/") {
			var caja = document.querySelector("input[type=search]");
			if (caja) { ev.preventDefault(); caja.focus(); }
			return;
		}
		var destino = document.querySelector('[data-atajo="' + (ev.key.length === 1 ? ev.key.toLowerCase() : ev.key) + '"]');
		if (!destino) return;
		ev.preventDefault();
		if (destino.tagName === "INPUT") { destino.checked = true; destino.focus(); destino.dispatchEvent(new Event("change", { bubbles: true })); }
		else destino.click();
	});

	/* ---- Copiar al portapapeles ---- */
	document.querySelectorAll("[data-copiar]").forEach(function (b) {
		b.addEventListener("click", function () {
			var origen = document.getElementById(b.dataset.copiar);
			if (!origen) return;
			var texto = origen.innerText;
			var original = b.textContent;
			function hecho(msg) { b.textContent = msg; setTimeout(function () { b.textContent = original; }, 1600); }
			if (navigator.clipboard) navigator.clipboard.writeText(texto).then(function () { hecho("Copiado"); }, function () { hecho("No se pudo copiar"); });
			else hecho("No se pudo copiar");
		});
	});

	/* ---- El registro del análisis se lee desde el final ---- */
	document.querySelectorAll("[data-al-final]").forEach(function (el) { el.scrollTop = el.scrollHeight; });

	/* ---- Desplazar la línea marcada a la vista dentro del visor del archivo completo ---- */
	document.querySelectorAll("details[data-centrar]").forEach(function (d) {
		d.addEventListener("toggle", function () {
			if (!d.open) return;
			var visor = d.querySelector(".codigo.archivo");
			var marcada = visor && visor.querySelector(".linea.marcada");
			if (marcada) visor.scrollTop = marcada.offsetTop - visor.clientHeight / 3;
		});
	});
})();
