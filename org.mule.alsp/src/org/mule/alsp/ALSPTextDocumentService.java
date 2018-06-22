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
				String contentUri = uri.startsWith("file:///") ? uri : ("file://" + uri);
				
				return textMap.get(contentUri);
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
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(new SignatureHelp());
	}

	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}

	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		return futureFactory(Collections.singletonList(this.mockReferences));
	}

	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}

	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		CompletableFuture<List<? extends SymbolInformation>> result = new <List<? extends SymbolInformation>>CompletableFuture();
		
		//ServerProcess.documentChanged(params.getTextDocument().getUri(), textMap.get(params.getTextDocument().getUri()), textVersion.get(params.getTextDocument().getUri()) + 1);
				
		ServerProcess.getStructure(params.getTextDocument().getUri(), new StructureHandler() {
			public void success(Map<String, JAVAStructureNode> map) {
				List<SymbolInformation> list = outlineSymbols(params.getTextDocument().getUri(), textMap.get(params.getTextDocument().getUri()), map);
				
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
		
		int nodeEnd = node.end();
		
		Range range = positionsToRange(node.start(), nodeEnd, text);
		
		String label = (node.text().isEmpty() ? "empty" : node.text());
		
		if(!node.text().isEmpty()) {
			list.add(new SymbolInformation(label, kindFromNode(rootNode), new Location(uri, range)));
		}
		
		rootNode.children().forEach(child -> {
			collectSymbols(child, uri, text, list, nodeEnd, label);
		});
	}
	
	private List<SymbolInformation> categorySymbols(String category, String uri, String text, Map<String, JAVAStructureNode> map) {
		List<SymbolInformation> list = new ArrayList<SymbolInformation>();
		
		collectSymbols(map.get(category), uri, text, list, map.get(category).node().end(), "root");
				
		return list;
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
	
	@Override
	public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
		return CompletableFuture.completedFuture(null);
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
		return CompletableFuture.completedFuture(null);
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
		textMap.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
		textVersion.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
		
		ServerProcess.documentOpened(new IOpenedDocument(params.getTextDocument().getUri(), params.getTextDocument().getVersion(), params.getTextDocument().getText()));
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		params.getContentChanges().forEach(textDocumentChange -> {
			textMap.put(params.getTextDocument().getUri(), textDocumentChange.getText());
			textVersion.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
						
			ServerProcess.documentChanged(params.getTextDocument().getUri(), textDocumentChange.getText(), params.getTextDocument().getVersion());
		});
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

	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		//this.didChangeCallback = didChangeExpectation;
	}
	
	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.didSaveCallback = didSaveExpectation;
	}
	
	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.didCloseCallback = didCloseExpectation;
	}
	
	public void reset() {
		
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.remoteProxies.add(remoteProxy);
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