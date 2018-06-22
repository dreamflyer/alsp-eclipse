package org.mule.alsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class ALSPWorkspaceService implements WorkspaceService {
	private Function<?, ?> _futureFactory;
	
	public <U> ALSPWorkspaceService(Function<U, CompletableFuture<U>> futureFactory) {
		this._futureFactory = futureFactory;
	}
	
	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>) this._futureFactory).apply(value);
	}
	
	@Override
	public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
		return null;
	}
	
	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		
	}
	
	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		
	}
}