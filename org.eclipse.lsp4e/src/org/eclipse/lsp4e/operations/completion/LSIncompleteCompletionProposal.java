/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Lucas Bullen (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Lucas Bullen (Red Hat Inc.) - Refactored for incomplete completion lists
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension7;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

import com.google.common.collect.ImmutableList;

public class LSIncompleteCompletionProposal
		implements ICompletionProposal, ICompletionProposalExtension3, ICompletionProposalExtension4,
		ICompletionProposalExtension5, ICompletionProposalExtension6, ICompletionProposalExtension7,
		IContextInformation {

	protected CompletionItem item;
	private int initialOffset = -1;
	protected int bestOffset = -1;
	protected ITextViewer viewer;
	private IRegion selection;
	private LinkedPosition firstPosition;
	private LSPDocumentInfo info;

	public LSIncompleteCompletionProposal(@NonNull CompletionItem item, int offset, LSPDocumentInfo info) {
		this.item = item;
		this.info = info;
		this.initialOffset = offset;
		this.bestOffset = getPrefixCompletionStart(info.getDocument(), offset);
	}

	public int getBestOffset() {
		return this.bestOffset;
	}

	public void updateOffset(int offset) {
		this.bestOffset = getPrefixCompletionStart(info.getDocument(), offset);
	}

	public CompletionItem getItem() {
		return this.item;
	}

	@Override
	public StyledString getStyledDisplayString(IDocument document, int offset, BoldStylerProvider boldStylerProvider) {
		String rawString = getDisplayString();
		StyledString res = new StyledString(rawString);
		if (offset > this.bestOffset) {
			try {
				String subString = document.get(this.bestOffset, offset - this.bestOffset);
				if (item.getTextEdit() != null) {
					int start = LSPEclipseUtils.toOffset(item.getTextEdit().getRange().getStart(), document);
					int end = offset;
					subString = document.get(start, end - start);
				}
				int lastIndex = 0;
				subString = subString.toLowerCase();
				String lowerRawString = rawString.toLowerCase();
				for (Character c : subString.toCharArray()) {
					int index = lowerRawString.indexOf(c, lastIndex);
					if (index < 0) {
						return res;
					} else {
						res.setStyle(index, 1, boldStylerProvider.getBoldStyler());
						lastIndex = index + 1;
					}
				}
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return res;
	}

	@Override
	public String getDisplayString() {
		return this.item.getLabel();
	}

	@Override
	public StyledString getStyledDisplayString() {
		return new StyledString(getDisplayString());
	}

	@Override
	public boolean isAutoInsertable() {
		// TODO consider what's best
		return false;
	}

	@Override
	public IInformationControlCreator getInformationControlCreator() {
		return new AbstractReusableInformationControlCreator() {
			@Override
			public IInformationControl doCreateInformationControl(Shell shell) {
				return new DefaultInformationControl(shell, true);
			}
		};
	}

	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		ServerCapabilities capabilities = info.getCapabilites();
		if (capabilities != null) {
			CompletionOptions options = capabilities.getCompletionProvider();
			if (options != null && Boolean.TRUE.equals(options.getResolveProvider())) {
				CompletableFuture<CompletionItem> resolvedItem = info.getLanguageClient().getTextDocumentService()
						.resolveCompletionItem(item);
				try {
					updateCompletionItem(resolvedItem.get(500, TimeUnit.MILLISECONDS));
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}

		StringBuilder res = new StringBuilder();
		if (this.item.getDetail() != null) {
			res.append("<p>" + this.item.getDetail() + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (res.length() > 0) {
			res.append("<br/>"); //$NON-NLS-1$
		}
		if (this.item.getDocumentation() != null) {
			res.append("<p>" + this.item.getDocumentation() + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return res.toString();
	}

	private void updateCompletionItem(CompletionItem resolvedItem) {
		if (resolvedItem == null) {
			return;
		}
		if (resolvedItem.getLabel() != null) {
			item.setLabel(resolvedItem.getLabel());
		}
		if (resolvedItem.getKind() != null) {
			item.setKind(resolvedItem.getKind());
		}
		if (resolvedItem.getDetail() != null) {
			item.setDetail(resolvedItem.getDetail());
		}
		if (resolvedItem.getDocumentation() != null) {
			item.setDocumentation(resolvedItem.getDocumentation());
		}
		if (resolvedItem.getInsertText() != null) {
			item.setInsertText(resolvedItem.getInsertText());
		}
		if (resolvedItem.getInsertTextFormat() != null) {
			item.setInsertTextFormat(resolvedItem.getInsertTextFormat());
		}
		if (resolvedItem.getTextEdit() != null) {
			item.setTextEdit(resolvedItem.getTextEdit());
		}
		if (resolvedItem.getAdditionalTextEdits() != null) {
			item.setAdditionalTextEdits(resolvedItem.getAdditionalTextEdits());
		}
	}

	@Override
	public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
		return item.getInsertText().substring(completionOffset - this.bestOffset);
	}

	@Override
	public int getPrefixCompletionStart(IDocument document, int completionOffset) {
		if (this.item.getTextEdit() != null) {
			try {
				return LSPEclipseUtils.toOffset(this.item.getTextEdit().getRange().getStart(), document);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		String insertText = getInsertText();
		try {
			String subDoc = document.get(
					Math.max(0, completionOffset - insertText.length()),
					Math.min(insertText.length(), completionOffset));
			for (int i = 0; i < insertText.length() && i < completionOffset; i++) {
				String tentativeCommonString = subDoc.substring(i);
				if (insertText.startsWith(tentativeCommonString)) {
					return completionOffset - tentativeCommonString.length();
				}
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return completionOffset;
	}

	@Override
	public void apply(IDocument document) {
		apply(document, Character.MIN_VALUE, 0, this.bestOffset);
	}

	protected void apply(IDocument document, char trigger, int stateMask, int offset) {
		String insertText = null;
		TextEdit textEdit = item.getTextEdit();
		try {
			if (textEdit == null) {
				insertText = getInsertText();
				Position start = LSPEclipseUtils.toPosition(this.bestOffset, document);
				Position end = LSPEclipseUtils.toPosition(offset, document); // need 2 distinct objects
				textEdit = new TextEdit(new Range(start, end), insertText);
			} else if (offset > this.initialOffset) {
				// characters were added after completion was activated
				int shift = offset - this.initialOffset;
				textEdit.getRange().getEnd().setCharacter(textEdit.getRange().getEnd().getCharacter() + shift);
			}
			{ // workaround https://github.com/Microsoft/vscode/issues/17036
				Position start = textEdit.getRange().getStart();
				Position end = textEdit.getRange().getEnd();
				if (start.getLine() > end.getLine() || (start.getLine() == end.getLine() && start.getCharacter() > end.getCharacter())) {
					textEdit.getRange().setEnd(start);
					textEdit.getRange().setStart(end);
				}
			}
			{ // allow completion items to be wrong with a too wide range
				Position documentEnd = LSPEclipseUtils.toPosition(document.getLength(), document);
				Position textEditEnd = textEdit.getRange().getEnd();
				if (documentEnd.getLine() < textEditEnd.getLine()
					|| (documentEnd.getLine() == textEditEnd.getLine() && documentEnd.getCharacter() < textEditEnd.getCharacter())) {
					textEdit.getRange().setEnd(documentEnd);
				}
			}

			if (insertText != null) {
				// try to reuse existing characters after completion location
				int shift = offset - this.bestOffset;
				int commonSize = 0;
				while (commonSize < insertText.length() - shift
					&& document.getLength() > offset + commonSize
					&& document.getChar(this.bestOffset + shift + commonSize) == insertText.charAt(commonSize + shift)) {
					commonSize++;
				}
				textEdit.getRange().getEnd().setCharacter(textEdit.getRange().getEnd().getCharacter() + commonSize);
			}
			insertText = textEdit.getNewText();
			LinkedHashMap<String, List<LinkedPosition>> regions = new LinkedHashMap<>();
			int insertionOffset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
			insertionOffset = computeNewOffset(item.getAdditionalTextEdits(), insertionOffset, document);
			if (item.getInsertTextFormat() == InsertTextFormat.Snippet) {
				int currentOffset = 0;
				while ((currentOffset = insertText.indexOf('$', currentOffset)) != -1) {
					StringBuilder keyBuilder = new StringBuilder();
					String defaultValue = ""; //$NON-NLS-1$
					int length = 1;
					while (currentOffset + length < insertText.length() && Character.isDigit(insertText.charAt(currentOffset + length))) {
						keyBuilder.append(insertText.charAt(currentOffset + length));
						length++;
					}
					if (length == 1 && insertText.length() >= 2 && insertText.charAt(currentOffset + 1) == '{') {
						length++;
						while (currentOffset + length < insertText.length() && Character.isDigit(insertText.charAt(currentOffset + length))) {
							keyBuilder.append(insertText.charAt(currentOffset + length));
							length++;
						}
						if (currentOffset + length < insertText.length() && insertText.charAt(currentOffset + length) == ':') {
							length++;
						}
						while (currentOffset + length < insertText.length() && insertText.charAt(currentOffset + length) != '}') {
							defaultValue += insertText.charAt(currentOffset + length);
							length++;
						}
						if (currentOffset + length < insertText.length() && insertText.charAt(currentOffset + length) == '}') {
							length++;
						}
					}
					if (keyBuilder.length() > 0) {
						String key = keyBuilder.toString();
						if (!regions.containsKey(key)) {
							regions.put(key, new ArrayList<>());
						}
						insertText = insertText.substring(0, currentOffset) + defaultValue + insertText.substring(currentOffset + length);
						LinkedPosition position = new LinkedPosition(document, insertionOffset + currentOffset, defaultValue.length());
						if (firstPosition == null) {
							firstPosition = position;
						}
						regions.get(key).add(position);
						currentOffset += defaultValue.length();
					} else {
						currentOffset++;
					}
				}
			}
			textEdit.setNewText(insertText); // insertText now has placeholder removed
			List<TextEdit> additionalEdits = item.getAdditionalTextEdits();
			if (additionalEdits != null && !additionalEdits.isEmpty()) {
				ImmutableList.Builder<TextEdit> allEdits = ImmutableList.builder();
				allEdits.add(textEdit);
				allEdits.addAll(additionalEdits);
				LSPEclipseUtils.applyEdits(document, allEdits.build());
			} else {
				LSPEclipseUtils.applyEdit(textEdit, document);
			}

			if (viewer != null && !regions.isEmpty()) {
				LinkedModeModel model = new LinkedModeModel();
				for (List<LinkedPosition> positions: regions.values()) {
					LinkedPositionGroup group = new LinkedPositionGroup();
					for (LinkedPosition position : positions) {
						group.addPosition(position);
					}
					model.addGroup(group);
				}
				model.forceInstall();

				LinkedModeUI ui = new EditorLinkedModeUI(model, viewer);
				// ui.setSimpleMode(true);
				// ui.setExitPolicy(new ExitPolicy(closingCharacter, document));
				// ui.setExitPosition(getTextViewer(), exit, 0, Integer.MAX_VALUE);
				ui.setCyclingMode(LinkedModeUI.CYCLE_NEVER);
				ui.enter();
			} else {
				selection = new Region(insertionOffset + textEdit.getNewText().length(), 0);
			}
		} catch (BadLocationException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private int computeNewOffset(List<TextEdit> additionalTextEdits, int insertionOffset, IDocument doc) {
		if (additionalTextEdits != null && !additionalTextEdits.isEmpty()) {
			int adjustment = 0;
			for (TextEdit edit : additionalTextEdits) {
				try {
					Range rng = edit.getRange();
					int start = LSPEclipseUtils.toOffset(rng.getStart(), doc);
					if (start <= insertionOffset) {
						int end = LSPEclipseUtils.toOffset(rng.getEnd(), doc);
						int orgLen = end - start;
						int newLeng = edit.getNewText().length();
						int editChange = newLeng - orgLen;
						adjustment += editChange;
					}
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
			return insertionOffset + adjustment;
		}
		return insertionOffset;
	}

	protected String getInsertText() {
		String insertText = this.item.getInsertText();
		if (this.item.getTextEdit() != null) {
			insertText = this.item.getTextEdit().getNewText();
		}
		if (insertText == null) {
			insertText = this.item.getLabel();
		}
		return insertText;
	}

	@Override
	public Point getSelection(IDocument document) {
		if (this.firstPosition != null) {
			return new Point(this.firstPosition.getOffset(), this.firstPosition.getLength());
		}
		if (selection == null) {
			return null;
		}
		return new Point(selection.getOffset(), selection.getLength());
	}

	@Override
	public String getAdditionalProposalInfo() {
		return this.item.getDetail();
	}

	@Override
	public Image getImage() {
		return LSPImages.imageFromCompletionItem(this.item);
	}

	@Override
	public IContextInformation getContextInformation() {
		return this;
	}

	@Override
	public String getContextDisplayString() {
		return getAdditionalProposalInfo();
	}

	@Override
	public String getInformationDisplayString() {
		return getAdditionalProposalInfo();
	}

	public String getSortText() {
		if (item.getSortText() != null && !item.getSortText().isEmpty()) {
			return item.getSortText();
		}
		return item.getLabel();
	}

	public int getNumberOfModifsBeforeOffset() {
		if (this.item.getTextEdit() == null) {
			// only insertion and offset is moved back in case document contains prefix
			// of insertion, so no change done before offset
			return 0;
		}
		int res = 0;
		try {
			int startOffset = LSPEclipseUtils.toOffset(this.item.getTextEdit().getRange().getStart(), this.info.getDocument());
			String insert = this.item.getTextEdit().getNewText();
			String subDoc = this.info.getDocument().get(startOffset, Math.min(
					startOffset + insert.length(),
					this.info.getDocument().getLength() - startOffset));
			for (int i = 0; i < subDoc.length() && i < insert.length(); i++) {
				if (subDoc.charAt(i) != insert.charAt(i)) {
					res++;
				}
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return res;

	}

}
