package org.mule.alsp.converters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.mulesoft.als.suggestions.interfaces.ISuggestion;
import org.mulesoft.language.client.jvm.ServerProcess;
import org.mulesoft.language.client.jvm.SuggestionsHandler;

public class Suggestions {
	private Suggestions() {
		
	};
	
	public static CompletableFuture<Either<List<CompletionItem>, CompletionList>> get(TextDocumentPositionParams positionParams) {
		CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = new <Either<List<CompletionItem>, CompletionList>>CompletableFuture();
		
		int offset = LSPEclipseUtils.offset(positionParams.getPosition(), positionParams.getTextDocument());
		
		ServerProcess.getSuggestions(positionParams.getTextDocument().getUri(), offset, new SuggestionsHandler() {
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
}
