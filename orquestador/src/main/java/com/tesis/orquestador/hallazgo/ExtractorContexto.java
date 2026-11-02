package com.tesis.orquestador.hallazgo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;

/**
 * Contexto sintáctico de un hallazgo (OE1): el método, constructor o inicializador que lo contiene; para campos
 * y hallazgos de clase, la clase completa (así el LLM ve todos los usos). Si el bloque es demasiado largo o el
 * archivo no se puede analizar, se usa una ventana de líneas alrededor del hallazgo.
 */
public final class ExtractorContexto {

	static final int MAX_LINEAS = 150;
	static final int VENTANA = 20;

	private ExtractorContexto() {
	}

	public static Contexto extraer(String fuente, Integer lineaInicio, Integer lineaFin) {
		String[] lineas = fuente.split("\r?\n", -1);
		int ini = lineaInicio != null ? lineaInicio : 1;
		int fin = lineaFin != null ? Math.max(lineaFin, ini) : ini;

		Optional<CompilationUnit> cu = analizar(fuente);
		if (cu.isEmpty()) {
			return ventana(lineas, ini, fin, "fragmento del archivo (no se pudo analizar la sintaxis)");
		}
		if (lineaInicio == null) {
			return cu.get().getPrimaryType()
					.map(t -> bloque(lineas, t, ini, fin))
					.orElseGet(() -> ventana(lineas, 1, 1, "inicio del archivo"));
		}
		Range objetivo = Range.range(ini, 1, fin, 1);
		Optional<Node> contenedor = cu.get().findAll(BodyDeclaration.class).stream()
				.map(Node.class::cast)
				.filter(d -> d.getRange().map(r -> r.begin.line <= ini && r.end.line >= fin).orElse(false))
				.min(Comparator.comparingInt(d -> d.getRange().map(Range::getLineCount).orElse(Integer.MAX_VALUE)));
		if (contenedor.isEmpty()) {
			return ventana(lineas, objetivo.begin.line, objetivo.end.line, "fragmento fuera de una clase");
		}
		Node d = contenedor.get();
		if (d instanceof FieldDeclaration) {
			d = claseDe(d).map(Node.class::cast).orElse(d);
		}
		return bloque(lineas, d, ini, fin);
	}

	private static Contexto bloque(String[] lineas, Node d, int ini, int fin) {
		Range r = d.getRange().orElseThrow();
		String descripcion = describir(d);
		if (r.getLineCount() > MAX_LINEAS) {
			return ventana(lineas, ini, fin, descripcion + " (fragmento; el bloque completo tiene "
					+ r.getLineCount() + " líneas)");
		}
		int desde = d.getComment().flatMap(Node::getRange).map(c -> c.begin.line).orElse(r.begin.line);
		return new Contexto(descripcion, desde, unir(lineas, desde, r.end.line));
	}

	private static Contexto ventana(String[] lineas, int ini, int fin, String descripcion) {
		int desde = Math.max(1, ini - VENTANA);
		int hasta = Math.min(lineas.length, fin + VENTANA);
		return new Contexto(descripcion, desde, unir(lineas, desde, hasta));
	}

	static String describir(Node d) {
		String clase = claseDe(d).filter(t -> t != d).map(t -> " de la clase " + t.getNameAsString()).orElse("");
		if (d instanceof CallableDeclaration<?> c) {
			String parametros = c.getParameters().stream()
					.map(p -> p.getType().asString())
					.collect(Collectors.joining(", "));
			String tipo = c.isConstructorDeclaration() ? "constructor " : "método ";
			return tipo + c.getNameAsString() + "(" + parametros + ")" + clase;
		}
		if (d instanceof InitializerDeclaration i) {
			return (i.isStatic() ? "bloque static" : "bloque de inicialización") + clase;
		}
		if (d instanceof TypeDeclaration<?> t) {
			return "clase " + t.getNameAsString() + " completa";
		}
		if (d instanceof NodeWithSimpleName<?> n) {
			return n.getNameAsString() + clase;
		}
		return "declaración" + clase;
	}

	private static Optional<TypeDeclaration<?>> claseDe(Node n) {
		if (n instanceof TypeDeclaration<?> t) {
			return Optional.of(t);
		}
		return n.getParentNode().flatMap(ExtractorContexto::claseDe);
	}

	private static Optional<CompilationUnit> analizar(String fuente) {
		var config = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
		var resultado = new JavaParser(config).parse(fuente);
		return resultado.isSuccessful() ? resultado.getResult() : Optional.empty();
	}

	private static String unir(String[] lineas, int desde, int hasta) {
		return String.join("\n", List.of(lineas).subList(desde - 1, Math.min(hasta, lineas.length)));
	}
}
