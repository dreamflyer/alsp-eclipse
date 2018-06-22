/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michal Niewrzal (Rogue Wave Software Inc.) - initial implementation
 *  Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 521020
 *  Lucas Bullen (Red Hat Inc.) - fix Bug 522737
 *******************************************************************************/
package org.eclipse.lsp4e.operations.highlight;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;

/**
 * {@link IReconcilingStrategy} implementation to Highlight Symbol (mark occurrences like).
 *
 */
public class HighlightReconcilingStrategy
		implements IReconcilingStrategy, IReconcilingStrategyExtension, CaretListener, IPreferenceChangeListener {

	public static final String TOGGLE_HIGHLIGHT_PREFERENCE = ".toggle.highlight"; //$NON-NLS-1$

	public static final String READ_ANNOTATION_TYPE = LanguageServerPlugin.PLUGIN_ID + ".read"; //$NON-NLS-1$
	public static final String WRITE_ANNOTATION_TYPE = LanguageServerPlugin.PLUGIN_ID + ".write"; //$NON-NLS-1$
	public static final String TEXT_ANNOTATION_TYPE = LanguageServerPlugin.PLUGIN_ID + ".text"; //$NON-NLS-1$

	private boolean enabled;
	private ISourceViewer sourceViewer;
	private IDocument document;

	private CompletableFuture<List<? extends DocumentHighlight>> request;
	private List<LSPDocumentInfo> infos;

	/**
	 * Holds the current occurrence annotations.
	 */
	private Annotation[] fOccurrenceAnnotations = null;

	public void install(ITextViewer viewer) {
		if (!(viewer instanceof ISourceViewer)) {
			return;
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.addPreferenceChangeListener(this);
		this.enabled = preferences.getBoolean(TOGGLE_HIGHLIGHT_PREFERENCE, true);
		this.sourceViewer = (ISourceViewer) viewer;
		this.sourceViewer.getTextWidget().addCaretListener(this);
	}

	public void uninstall() {
		if (sourceViewer != null) {
			sourceViewer.getTextWidget().removeCaretListener(this);
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.removePreferenceChangeListener(this);
		cancel();
	}

	@Override
	public void setProgressMonitor(IProgressMonitor monitor) {

	}

	@Override
	public void initialReconcile() {
		if (sourceViewer != null) {
			sourceViewer.getTextWidget().getDisplay()
					.asyncExec(() -> {
						if (sourceViewer != null) {
							collectHighlights(sourceViewer.getTextWidget().getCaretOffset());
						}
					});
		}
	}

	@Override
	public void setDocument(IDocument document) {
		this.document = document;
	}

	@Override
	public void caretMoved(CaretEvent event) {
		collectHighlights(event.caretOffset);
	}

	/**
	 * Collect list of highlight for the given caret offset by consuming language
	 * server 'documentHighligh't.
	 *
	 * @param caretOffset
	 */
	private void collectHighlights(int caretOffset) {
		if (sourceViewer == null || !enabled) {
			return;
		}
		if (infos == null) {
			infos = LanguageServiceAccessor.getLSPDocumentInfosFor(document,
					capabilities -> Boolean.TRUE.equals(capabilities.getDocumentHighlightProvider()));
		}
		if (infos.isEmpty()) {
			// The language server has not the highlight capability.
			return;
		}
		// Cancel the last call of 'documentHighlight'.
		cancel();
		try {
			Position position = LSPEclipseUtils.toPosition(caretOffset, document);
			for (LSPDocumentInfo info : infos) {
				TextDocumentIdentifier identifier = new TextDocumentIdentifier(info.getFileUri().toString());
				TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, position);
				request = info.getLanguageClient().getTextDocumentService().documentHighlight(params);
				// Call the 'documentHighlight'.
				request.thenAccept(result -> {
					// and then update the UI annotations.
					updateAnnotations(result, sourceViewer.getAnnotationModel());
				});
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	/**
	 * Cancel the last call of 'documentHighlight'.
	 */
	private void cancel() {
		if (request != null && !request.isDone()) {
			request.cancel(true);
			request = null;
		}
	}

	/**
	 * Update the UI annotations with the given list of DocumentHighlight.
	 *
	 * @param highlights
	 *            list of DocumentHighlight
	 * @param annotationModel
	 *            annotation model to update.
	 */
	private void updateAnnotations(List<? extends DocumentHighlight> highlights, IAnnotationModel annotationModel) {
		Map<Annotation, org.eclipse.jface.text.Position> annotationMap = new HashMap<>(highlights.size());
		for (DocumentHighlight h : highlights) {
			if (h != null) {
				try {
					int start = LSPEclipseUtils.toOffset(h.getRange().getStart(), document);
					int end = LSPEclipseUtils.toOffset(h.getRange().getEnd(), document);
					annotationMap.put(new Annotation(kindToAnnotationType(h.getKind()), false, null),
							new org.eclipse.jface.text.Position(start, end - start));
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations, annotationMap);
			} else {
				removeOccurrenceAnnotations();
				Iterator<Entry<Annotation, org.eclipse.jface.text.Position>> iter = annotationMap.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<Annotation, org.eclipse.jface.text.Position> mapEntry = iter.next();
					annotationModel.addAnnotation(mapEntry.getKey(), mapEntry.getValue());
				}
			}
			fOccurrenceAnnotations = annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
		}
	}

	/**
	 * Returns the lock object for the given annotation model.
	 *
	 * @param annotationModel
	 *            the annotation model
	 * @return the annotation model's lock object
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock = ((ISynchronizable) annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}

	void removeOccurrenceAnnotations() {

		IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
		if (annotationModel == null || fOccurrenceAnnotations == null)
			return;

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations, null);
			} else {
				for (Annotation fOccurrenceAnnotation : fOccurrenceAnnotations)
					annotationModel.removeAnnotation(fOccurrenceAnnotation);
			}
			fOccurrenceAnnotations = null;
		}
	}

	private String kindToAnnotationType(DocumentHighlightKind kind) {
		switch (kind) {
		case Read:
			return READ_ANNOTATION_TYPE;
		case Write:
			return WRITE_ANNOTATION_TYPE;
		default:
			return TEXT_ANNOTATION_TYPE;
		}
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		if (event.getKey().equals(TOGGLE_HIGHLIGHT_PREFERENCE)) {
			this.enabled = Boolean.valueOf(event.getNewValue().toString());
			if (enabled) {
				initialReconcile();
			} else {
				removeOccurrenceAnnotations();
			}
		}
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		// Do nothing
	}

	@Override
	public void reconcile(IRegion partition) {
		// Do nothing
	}

}
