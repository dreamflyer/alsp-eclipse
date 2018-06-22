package org.mule.alsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

public class ALSPSever implements LanguageServer {
	private ALSPTextDocumentService textDocumentService = new ALSPTextDocumentService(this::buildMaybeDelayedFuture);
	
	private ALSPWorkspaceService workspaceService = new ALSPWorkspaceService(this::buildMaybeDelayedFuture);
	
	private InitializeResult initializeResult = new InitializeResult();
	
	private long delay = 0;
	
	private boolean started;
	
	private PipedInputStream inputStream = new PipedInputStream();
	
	public OutputStream consumerOutputStream;
	public InputStream consumerInputStream = new PipedInputStream();
	
	ALSPSever() {
		try {
			consumerOutputStream = new SafeOutputStream(new PipedOutputStream(inputStream));
			
			resetInitializeResult();
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
	
	public void start() throws InterruptedException, ExecutionException {
		Launcher<LanguageClient> l = null;
		
		try {
			l = LSPLauncher.createServerLauncher(this, new DebugInputStream(inputStream), new SafeOutputStream(new PipedOutputStream((PipedInputStream) consumerInputStream)));
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		Future<?> f = l.startListening();
		
		addRemoteProxy(l.getRemoteProxy());
		
		f.get();
	}
	
	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.textDocumentService.addRemoteProxy(remoteProxy);
		
		this.started = true;
	}
	
	private void resetInitializeResult() {
		ServerCapabilities capabilities = new ServerCapabilities();
		
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
		
		CompletionOptions completionProvider = new CompletionOptions(false, Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
		
		capabilities.setCompletionProvider(completionProvider);
		capabilities.setHoverProvider(false);
		capabilities.setDefinitionProvider(false);
		capabilities.setReferencesProvider(false);
		capabilities.setDocumentFormattingProvider(false);
		capabilities.setCodeActionProvider(false);
		// capabilities.setDocumentLinkProvider(new DocumentLinkOptions());
		// capabilities.setSignatureHelpProvider(new SignatureHelpOptions());
		capabilities.setDocumentSymbolProvider(true);
		
		initializeResult.setCapabilities(capabilities);
	}
	
	<U> CompletableFuture<U> buildMaybeDelayedFuture(U value) {
		if(delay > 0) {
			return CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(delay);
				} catch(InterruptedException e) {
					throw new RuntimeException(e);
				}
			}).thenApply(new Function<Void, U>() {
				public U apply(Void v) {
					return value;
				}
			});
		}
		
		return CompletableFuture.completedFuture(value);
	}
	
	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		return buildMaybeDelayedFuture(initializeResult);
	}
	
	@Override
	public ALSPTextDocumentService getTextDocumentService() {
		return textDocumentService;
	}
	
	@Override
	public WorkspaceService getWorkspaceService() {
		return workspaceService;
	}
	
	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		this.textDocumentService.setDidChangeCallback(didChangeExpectation);
	}
	
	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.textDocumentService.setDidSaveCallback(didSaveExpectation);
	}
	
	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.textDocumentService.setDidCloseCallback(didCloseExpectation);
	}
	
	public void setCompletionTriggerChars(Set<String> chars) {
		if(chars != null) {
			initializeResult.getCapabilities().getCompletionProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}
	
	public void setContextInformationTriggerChars(Set<String> chars) {
		if(chars != null) {
			initializeResult.getCapabilities().getSignatureHelpProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}
	
	public InitializeResult getInitializeResult() {
		return initializeResult;
	}
	
	@Override
	public CompletableFuture<Object> shutdown() {
		this.started = false;
		
		this.delay = 0;
		
		resetInitializeResult();
		
		this.textDocumentService.reset();
		
		return CompletableFuture.completedFuture(Collections.emptySet());
	}
	
	@Override
	public void exit() {
	}
	
	public void setTimeToProceedQueries(int i) {
		this.delay = i;
	}
	
	public boolean isRunning() {
		return this.started;
	}
}
