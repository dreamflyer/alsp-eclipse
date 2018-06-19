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

	/**
	 * Use this method to get a future that will wait specified delay before returning
	 * value
	 * @param value the value that will be returned by the future
	 * @return a future that completes to value, after delay from {@link ALSPSever#delay}
	 */
	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>)this._futureFactory).apply(value);
	}
	
	@Override
	public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		// TODO Auto-generated method stub

	}
}