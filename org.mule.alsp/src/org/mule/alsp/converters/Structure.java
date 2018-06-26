package org.mule.alsp.converters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.mulesoft.language.client.jvm.JAVAStructureNode;
import org.mulesoft.language.client.jvm.ServerProcess;
import org.mulesoft.language.client.jvm.StructureHandler;
import org.mulesoft.language.outline.structure.structureInterfaces.StructureNodeJSON;

public class Structure {
	private Structure() {
		
	}
	
	public static CompletableFuture<List<? extends SymbolInformation>> get(DocumentSymbolParams params) {
		CompletableFuture<List<? extends SymbolInformation>> result = new <List<? extends SymbolInformation>>CompletableFuture();
		
		ServerProcess.getStructure(params.getTextDocument().getUri(), new StructureHandler() {
			public void success(Map<String, JAVAStructureNode> map) {
				List<SymbolInformation> list = outlineSymbols(params.getTextDocument().getUri(), LSPEclipseUtils.textContent(params.getTextDocument().getUri()), map);
				
				result.complete(list);
			}
			
			public void failure(Throwable throwable) {
				throwable.printStackTrace();
			}
		});
		
		return result;
	}
	
	private static List<SymbolInformation> outlineSymbols(String uri, String text, Map<String, JAVAStructureNode> map) {
		List<SymbolInformation> result = new ArrayList<SymbolInformation>();
		
		map.entrySet().forEach(entry -> {
			result.addAll(categorySymbols(entry.getKey(), uri, text, map));
		});
		
		return result;
	}
	
	private static List<SymbolInformation> categorySymbols(String category, String uri, String text, Map<String, JAVAStructureNode> map) {
		List<SymbolInformation> list = new ArrayList<SymbolInformation>();
		
		String categoryName = category.substring(0, category.indexOf("Category"));
		
		list.add(new SymbolInformation(categoryName, SymbolKind.Package, new Location(uri, new Range(new Position(-1, -1), new Position(-1, -1))), "categories"));
		
		collectSymbols(map.get(category), uri, text, list, 0, categoryName);
		
		return list;
	}
	
	private static void collectSymbols(JAVAStructureNode rootNode, String uri, String text, List<SymbolInformation> list, int parentEnd, String categoryName) {
		StructureNodeJSON node = rootNode.node();
		
		Range range = LSPEclipseUtils.range(node.start(), node.end(), new TextDocumentIdentifier(uri));
		
		String label = (node.text().isEmpty() ? "empty" : node.text());
		
		if(!node.text().isEmpty()) {
			System.out.println(node.category() + " " + node.icon() + " " + node.key() + " " + node.textStyle() + " " + node.typeText().get());
			
			list.add(new SymbolInformation(label, kindFromNode(rootNode), new Location(uri, range), categoryName));
		}
		
		rootNode.children().forEach(child -> {
			collectSymbols(child, uri, text, list, node.end(), categoryName);
		});
	}
	
	private static SymbolKind kindFromNode(JAVAStructureNode rootNode) {
		switch(rootNode.node().icon()) {
			case "ARROW_SMALL_LEFT": {
				return SymbolKind.Field;
			}
			case "PRIMITIVE_SQUARE": {
				return SymbolKind.Field;
			}
			case "PRIMITIVE_DOT": {
				return SymbolKind.Module;
			}
			case "FILE_SUBMODULE": {
				return SymbolKind.File;
			}
			case "TAG": {
				return SymbolKind.Variable;
			}
			case "FILE_BINARY": {
				return SymbolKind.File;
			}
			case "BOOK": {
				return SymbolKind.Module;
			}
			
			default: {
				return SymbolKind.Field;
			}
		}
	}
}
