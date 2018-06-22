package converters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
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
		
		collectSymbols(map.get(category), uri, text, list, map.get(category).node().end(), "root");
		
		return list;
	}
	
	private static void collectSymbols(JAVAStructureNode rootNode, String uri, String text, List<SymbolInformation> list, int parentEnd, String parentName) {
		StructureNodeJSON node = rootNode.node();
		
		Range range = LSPEclipseUtils.range(node.start(), node.end(), new TextDocumentIdentifier(uri));
		
		String label = (node.text().isEmpty() ? "empty" : node.text());
		
		if(!node.text().isEmpty()) {
			list.add(new SymbolInformation(label, kindFromNode(rootNode), new Location(uri, range)));
		}
		
		rootNode.children().forEach(child -> {
			collectSymbols(child, uri, text, list, node.end(), label);
		});
	}
	
	private static SymbolKind kindFromNode(JAVAStructureNode rootNode) {
		return SymbolKind.Field;
	}
}
