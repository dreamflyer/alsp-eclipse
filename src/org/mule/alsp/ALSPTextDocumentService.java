package org.mule.alsp;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.mulesoft.als.suggestions.interfaces.ISuggestion;
import org.mulesoft.language.client.jvm.FS;
import org.mulesoft.language.client.jvm.JAVAStructureNode;
import org.mulesoft.language.client.jvm.ServerProcess;
import org.mulesoft.language.client.jvm.StructureHandler;
import org.mulesoft.language.client.jvm.SuggestionsHandler;
import org.mulesoft.language.client.jvm.ValidationHandler;
import org.mulesoft.language.common.dtoTypes.IOpenedDocument;
import org.mulesoft.language.common.dtoTypes.IValidationIssue;
import org.mulesoft.language.outline.structure.structureInterfaces.StructureNodeJSON;

public class ALSPTextDocumentService implements TextDocumentService {
	private Hover mockHover;
	private List<? extends Location> mockDefinitionLocations;
	private List<? extends TextEdit> mockFormattingTextEdits;
	private SignatureHelp mockSignatureHelp;
	private List<DocumentLink> mockDocumentLinks;

	private CompletableFuture<DidSaveTextDocumentParams> didSaveCallback;
	private CompletableFuture<DidCloseTextDocumentParams> didCloseCallback;

	private Function<?,? extends CompletableFuture<?>> _futureFactory;
	private List<LanguageClient> remoteProxies;
	private Location mockReferences;
	private List<Command> mockCodeActions;
	
	private static Map<String, String> textMap = new HashMap<String, String>();
	
	private static Map<String, Integer> textVersion = new HashMap<String, Integer>();
	
	static {
		ServerProcess.init();
		
		ServerProcess.setFS(new FS() {
			public String content(String uri) {
				return textMap.get("file://" + uri);
			}
		});
	}
	
	public <U> ALSPTextDocumentService(Function<U, CompletableFuture<U>> futureFactory) {
		ServerProcess.onValidation(new ValidationHandler() {
			public void success(String pointOfView, List<IValidationIssue> issues) {
				String text = textMap.get(pointOfView);
				
				List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
				
				issues.forEach(issue -> diagnostics.add(new Diagnostic(positionsToRange(issue.range().start(), issue.range().end(), text), issue.text())));
				
				remoteProxies.stream().forEach(p -> p.publishDiagnostics(new PublishDiagnosticsParams(pointOfView, diagnostics)));
			}
		});
		
		this._futureFactory = futureFactory;
		mockHover = new Hover(Collections.singletonList(Either.forLeft("Mock hover")), null);
		
		this.remoteProxies = new ArrayList<>();
	}

	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>)this._futureFactory).apply(value);
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position) {
		CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = new <Either<List<CompletionItem>, CompletionList>>CompletableFuture();
		
		ServerProcess.getSuggestions(position.getTextDocument().getUri(), pointToPosition(position.getPosition().getLine(), position.getPosition().getCharacter(), textMap.get(position.getTextDocument().getUri())), new SuggestionsHandler() {
			public void success(List<ISuggestion> list) {
				List<CompletionItem> resultList = new ArrayList<CompletionItem>();
				
				list.forEach(item -> {
					CompletionItem resultItem = new CompletionItem();
					
					resultItem.setLabel(item.displayText() == null ? item.displayText() : item.text());
					
					resultItem.setDetail("");
					
					resultList.add(resultItem);
				});
				
				result.complete(Either.forLeft(resultList));
			}
			
			public void failure(Throwable throwable) {
				throwable.printStackTrace();
			}
		});
		
		return result;
	}

	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		return CompletableFuture.completedFuture(unresolved);
	}

	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(mockHover);
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(new SignatureHelp());
	}

	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(mockDefinitionLocations);
	}

	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		return futureFactory(Collections.singletonList(this.mockReferences));
	}

	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}

	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		CompletableFuture<List<? extends SymbolInformation>> result = new <List<SymbolInformation>>CompletableFuture();
		
		//ServerProcess.documentChanged(params.getTextDocument().getUri(), textMap.get(params.getTextDocument().getUri()), textVersion.get(params.getTextDocument().getUri()) + 1);
				
		ServerProcess.getStructure(params.getTextDocument().getUri(), new StructureHandler() {
			public void success(Map<String, JAVAStructureNode> map) {
				List<SymbolInformation> list = outlineSymbols(params.getTextDocument().getUri(), textMap.get(params.getTextDocument().getUri()), map);
				
				//SymbolsTree.refine(list);
				
				Range listRange = getRange(list);
				
				list.add(new SymbolInformation("root", SymbolKind.Class, new Location(params.getTextDocument().getUri(), listRange)));
				
				result.complete(list);
			}
			
			public void failure(Throwable throwable) {
				throwable.printStackTrace();
			}
		});
		
		return result;
	}
	
	private void collectSymbols(JAVAStructureNode rootNode, String uri, String text, List<SymbolInformation> list, int parentEnd, String parentName) {
		StructureNodeJSON node = rootNode.node();
		
		int nodeEnd = parentEnd <= node.end() ? parentEnd - 1 : node.end();
		
		Range range = positionsToRange(node.start(), nodeEnd, text);
		
		//Range nrange = new Range(range.getStart(), new Position(range.getEnd().getLine(), range.getEnd().getCharacter() + 500));
		
		String nodeText = node.text().replace("/", "");
				
		String label = parentName + "_" + (nodeText.isEmpty() ? "empty" : nodeText);// + rangeToString(range);// + " " + rangeToString(range) + " " + node.icon() + " " + node.key();
		
		if(!nodeText.isEmpty()) {
			list.add(new SymbolInformation(label, kindFromNode(rootNode), new Location(uri, range), parentName));
		}
		rootNode.children().forEach(child -> {
			collectSymbols(child, uri, text, list, nodeEnd, nodeText.isEmpty() ? parentName : label);
		});
	}
	
	private List<SymbolInformation> categorySymbols(String category, String uri, String text, Map<String, JAVAStructureNode> map) {
		List<SymbolInformation> list = new ArrayList<SymbolInformation>();
		
		collectSymbols(map.get(category), uri, text, list, map.get(category).node().end(), "root");
				
		return list;
	}
	
	private String rangeToString(Range range) {
		return "[" + positionToString(range.getStart()) + ", " + positionToString(range.getEnd()) + "]";
	}
	
	private String positionToString(Position pos) {
		return "(" + pos.getLine() + ", " + pos.getCharacter() + ")";
	}
	
	private List<SymbolInformation> outlineSymbols(String uri, String text, Map<String, JAVAStructureNode> map) {
		List<SymbolInformation> result = new ArrayList<SymbolInformation>();
		
		map.entrySet().forEach(entry -> {
			result.addAll(categorySymbols(entry.getKey(), uri, text, map));
		});
		
		return result;
	}
	
	private SymbolKind kindFromNode(JAVAStructureNode rootNode) {
		return SymbolKind.Field;
	}
	
	private Range getRange(List<SymbolInformation> list) {
		Position min = new Position(Integer.MAX_VALUE, Integer.MAX_VALUE);
		Position max = new Position(0, 0);
		
		for(int i = 0; i < list.size(); i++) {
			SymbolInformation node = list.get(i);
			
			Position start = node.getLocation().getRange().getStart();
			Position end = node.getLocation().getRange().getEnd();
			
			if(comparePositions(min, start)) {
				min = start;
			}
			
			if(comparePositions(end, max)) {
				max = end;
			}
		}
		
		return new Range(min, new Position(max.getLine() + 1, max.getCharacter() + 1));
	}
	
	static boolean compareRanges(Range rg1, Range rg2) {
		if(comparePositions(rg1.getStart(), rg2.getStart())) {
			return false;
		}
		
		if(comparePositions(rg2.getEnd(), rg1.getEnd())) {
			return false;
		}
		
		return comparePositions(rg2.getStart(), rg1.getStart());
	}
	
	static boolean comparePositions(Position pos1, Position pos2) {
		if(pos1.getLine() > pos2.getLine()) {
			return true;
		}
		
		if(pos1.getLine() == pos2.getLine() && pos1.getCharacter() > pos2.getCharacter()) {
			return true;
		}
		
		return false;
	}
	
	@Override
	public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
		return CompletableFuture.completedFuture(mockDocumentLinks);
	}

	@Override
	public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
		return CompletableFuture.completedFuture(this.mockCodeActions);
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return CompletableFuture.completedFuture(mockFormattingTextEdits);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		this.textMap.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
		this.textVersion.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
		
		ServerProcess.documentOpened(new IOpenedDocument(params.getTextDocument().getUri(), params.getTextDocument().getVersion(), params.getTextDocument().getText()));
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
				
		params.getContentChanges().forEach(textDocumentChange -> {
			this.textMap.put(params.getTextDocument().getUri(), textDocumentChange.getText());
			this.textVersion.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
						
			ServerProcess.documentChanged(params.getTextDocument().getUri(), textDocumentChange.getText(), params.getTextDocument().getVersion());
		});
		
//		if (didChangeCallback != null) {
//			didChangeCallback.complete(params);
//			didChangeCallback = null;
//		}
	}
	
	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		if (didCloseCallback != null) {
			didCloseCallback.complete(params);
			didCloseCallback = null;
		}
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		if (didSaveCallback != null) {
			didSaveCallback.complete(params);
			didSaveCallback = null;
		}
	}
	
	public void setMockCompletionList(CompletionList completionList) {
		//this.mockCompletionList = completionList;
	}

	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		//this.didChangeCallback = didChangeExpectation;
	}
	
	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.didSaveCallback = didSaveExpectation;
	}
	
	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.didCloseCallback = didCloseExpectation;
	}
	
	public void setMockHover(Hover hover) {
		this.mockHover = hover;
	}
	
	public void setMockDefinitionLocations(List<? extends Location> definitionLocations) {
		this.mockDefinitionLocations = definitionLocations;
	}

	public void setMockReferences(Location location) {
		this.mockReferences = location;
	}
	
	public void setMockFormattingTextEdits(List<? extends TextEdit> formattingTextEdits) {
		this.mockFormattingTextEdits = formattingTextEdits;
	}

	public void setMockDocumentLinks(List<DocumentLink> documentLinks) {
		this.mockDocumentLinks = documentLinks;
	}
	
	public void reset() {
//		this.mockCompletionList = new CompletionList();
//		this.mockDefinitionLocations = Collections.emptyList();
//		this.mockHover = null;
//		this.mockReferences = null;
//		this.remoteProxies = new ArrayList<LanguageClient>();
//		this.mockCodeActions = new ArrayList<Command>();
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		//this.diagnostics = diagnostics;
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.remoteProxies.add(remoteProxy);
	}

	public void setCodeActions(List<Command> commands) {
		this.mockCodeActions = commands;
	}
	
	public void setSignatureHelp(SignatureHelp signatureHelp) {
		this.mockSignatureHelp = signatureHelp;
	}
	
	private int pointToPosition(int row, int column, String text) {
		BufferedReader reader = new BufferedReader(new StringReader(text));
		
		int result = 0;
		int currentRow = 0;
		
		try {
			String line = reader.readLine();
			
			while(line != null && currentRow < row) {
				result += line.length() + 1;
				
				currentRow += 1;
				
				line = reader.readLine();
			}
			
			result += column;
		} catch(Throwable exception) {
			return 0;
		}
		
		return result;
	}
	
	private Position positionToPoint(int position, String text) {
		int row = 0;
		int col = 0;
		
		int covered = 0;
		
		BufferedReader reader = new BufferedReader(new StringReader(text));
		
		try {
			String line = reader.readLine();
			
			while(line != null && covered + line.length() + 1 < position) {				
				covered += line.length() + 1;
				
				row+=1;
				
				line = reader.readLine();
			}
			
			col = position - covered;
		} catch(Throwable exception) {
			return null;
		}
		
		return new Position(row, col);
	}
	
	private Range positionsToRange(int start, int end, String text) {
		return new Range(positionToPoint(start, text), positionToPoint(end, text));
	}
}

class SymbolsTree {
	SymbolInformation info;
	
	List<SymbolsTree> children = new ArrayList<SymbolsTree>();
	
	SymbolsTree(SymbolInformation info) {
		this.info = info;
	}
	
	static void refine(List<SymbolInformation> infos) {
		SymbolsTree result = new SymbolsTree(null);
		
		infos.forEach(info -> result.addChild(info));
		
		result.refineRanges();
	}
	
	int refineRanges() {
		SymbolsTree lastChild = lastChild();
		
		int value = 1 + (lastChild == null ? 0 : lastChild.refineRanges());
		
		refineLastColumn(value);
		
		for(int i = 0; i < children.size(); i++) {
			SymbolsTree child = children.get(i);
			
			if(child != lastChild) {
				child.refineRanges();
			}
		}
		
		return value;
	}
	
	private void refineLastColumn(int value) {
		if(info == null) {
			return;
		}
		
		Position endPosition = info.getLocation().getRange().getEnd();
		
		Position newEndPosition = new Position(endPosition.getLine(), endPosition.getCharacter() + value);
		
		info.setLocation(new Location(info.getLocation().getUri(), new Range(info.getLocation().getRange().getStart(), newEndPosition)));
	}
	
	private SymbolsTree lastChild() {
		if(info == null) {
			return null;
		}
		
		Range range = info.getLocation().getRange();
		
		for(int i = 0; i < children.size(); i++) {
			SymbolsTree child = children.get(i);
			
			Range childRange = child.info.getLocation().getRange();
			
			if(childRange.getEnd().getLine() == range.getEnd().getLine() && childRange.getEnd().getCharacter() == range.getEnd().getCharacter()) {
				return child;
			}
		}
		
		return null;
	}
	
	void addChild(SymbolInformation info) {
		int index = indexOfParentOf(info);
		
		if(index != -1) {
			SymbolsTree parent = children.get(index);
			
			parent.addChild(info);
			
			return;
		}
		
		SymbolsTree newItem = new SymbolsTree(info);
		
		index = indexOfChildOf(info);
		
		if(index != -1) {			
			SymbolInformation child = children.get(index).info;
			
			children.remove(index);
			
			newItem.addChild(child);
		}
		
		children.add(newItem);
	}
	
	private int indexOfParentOf(SymbolInformation info) {
		for(int i = 0; i < children.size(); i++) {
			if(compareSymbols(children.get(i).info, info)) {
				return i;
			}
		}
		
		return -1;
	}
	
	private int indexOfChildOf(SymbolInformation info) {
		for(int i = 0; i < children.size(); i++) {
			if(compareSymbols(info, children.get(i).info)) {
				return i;
			}
		}
		
		return -1;
	}
	
	private static boolean compareSymbols(SymbolInformation s1, SymbolInformation s2) {
		return ALSPTextDocumentService.compareRanges(s1.getLocation().getRange(), s2.getLocation().getRange());
	}
}