/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;

public class LSPFormatter {

	public void applyEdits(IDocument document, List<? extends TextEdit> edits) {
		LSPEclipseUtils.applyEdits(document, edits);
	}

	public CompletableFuture<List<? extends TextEdit>> requestFormatting(@NonNull IDocument document,
			@NonNull ITextSelection textSelection) {
		Collection<@NonNull LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(document, capabilities -> supportFormatting(capabilities));
		if (infos.isEmpty()) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		// TODO consider a better strategy for that, maybe iterate on all LS until one gives a result
		LSPDocumentInfo info = infos.iterator().next();
		try {
			return requestFormatting(info, textSelection);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	private CompletableFuture<List<? extends TextEdit>> requestFormatting(LSPDocumentInfo info,
			ITextSelection textSelection) throws BadLocationException {
		TextDocumentIdentifier docId = new TextDocumentIdentifier(info.getFileUri().toString());
		ServerCapabilities capabilities = info.getCapabilites();
		// use range formatting if standard formatting is not supported or text is selected
		if (capabilities != null && Boolean.TRUE.equals(capabilities.getDocumentRangeFormattingProvider())
				&& (capabilities.getDocumentFormattingProvider() == null
						|| !capabilities.getDocumentFormattingProvider() || textSelection.getLength() != 0)) {
			DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
			params.setTextDocument(docId);
			params.setOptions(new FormattingOptions());
			boolean fullFormat = textSelection.getLength() == 0;
			Position start = LSPEclipseUtils.toPosition(fullFormat ? 0 : textSelection.getOffset(), info.getDocument());
			Position end = LSPEclipseUtils.toPosition(
					fullFormat ? info.getDocument().getLength() : textSelection.getOffset() + textSelection.getLength(),
					info.getDocument());
			params.setRange(new Range(start, end));
			return info.getLanguageClient().getTextDocumentService().rangeFormatting(params);
		}

		DocumentFormattingParams params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(new FormattingOptions());
		return info.getLanguageClient().getTextDocumentService().formatting(params);
	}

	public static boolean supportFormatting(ServerCapabilities capabilities) {
		return Boolean.TRUE.equals(capabilities.getDocumentFormattingProvider())
				|| Boolean.TRUE.equals(capabilities.getDocumentRangeFormattingProvider());
	}

}
