package org.mule.alsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
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
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.mule.alsp.converters.Structure;
import org.mule.alsp.converters.Suggestions;
import org.mule.alsp.converters.ValidationHandler;
import org.mulesoft.language.client.jvm.FS;
import org.mulesoft.language.client.jvm.ServerProcess;
import org.mulesoft.language.common.dtoTypes.IOpenedDocument;

public class ALSPTextDocumentService implements TextDocumentService {
	private List<LanguageClient> remoteProxies = new ArrayList<>();
	
	static {
		ServerProcess.init();
		
		ServerProcess.setFS(new FS() {
			public String content(String uri) {
				return LSPEclipseUtils.textContent(uri);
			}
		});
	}
	
	public <U> ALSPTextDocumentService() {
		ServerProcess.onValidation(new ValidationHandler(remoteProxies));
	}
	
	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams positionParams) {
		return Suggestions.get(positionParams);
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
	
	@Override
	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		return Structure.get(params);
	}
	
	@Override
	public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
		return CompletableFuture.completedFuture(null);
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
		ServerProcess.documentOpened(new IOpenedDocument(params.getTextDocument().getUri(), params.getTextDocument().getVersion(), params.getTextDocument().getText()));
	}
	
	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		params.getContentChanges().forEach(textDocumentChange -> {
			ServerProcess.documentChanged(params.getTextDocument().getUri(), textDocumentChange.getText(), params.getTextDocument().getVersion());
		});
	}
	
	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		
	}
	
	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		
	}
	
	public void reset() {
		
	}
	
	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.remoteProxies.add(remoteProxy);
	}
}