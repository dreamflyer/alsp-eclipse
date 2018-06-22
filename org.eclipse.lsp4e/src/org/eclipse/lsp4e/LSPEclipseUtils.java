/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Lucas Bullen (Red Hat Inc.) - Get IDocument from IEditorInput
 *  Angelo Zerr <angelo.zerr@gmail.com> - Bug 525400 - [rename] improve rename support with ltk UI
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.RewriteSessionEditProcessor;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import com.google.gson.Gson;

/**
 * Some utility methods to convert between Eclipse and LS-API types
 */
public class LSPEclipseUtils {

	private LSPEclipseUtils() {
		// this class shouldn't be instantiated
	}

	public static ITextEditor getActiveTextEditor() {
		IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		if(editorPart instanceof ITextEditor) {
			return (ITextEditor) editorPart;
		} else if (editorPart instanceof MultiPageEditorPart) {
			MultiPageEditorPart multiPageEditorPart = (MultiPageEditorPart) editorPart;
			Object page = multiPageEditorPart.getSelectedPage();
			if (page instanceof ITextEditor) {
				return (ITextEditor) page;
			}
		}
		return null;
	}

	public static Position toPosition(int offset, IDocument document) throws BadLocationException {
		Position res = new Position();
		res.setLine(document.getLineOfOffset(offset));
		res.setCharacter(offset - document.getLineInformationOfOffset(offset).getOffset());
		return res;
	}
	
	public static Position position(int offset, TextDocumentIdentifier id) {
		try {
			return toPosition(offset, toDocument(id));
		} catch(BadLocationException e) {
			return new Position(-1, -1);
		}
	}
	
	public static int toOffset(Position position, IDocument document) throws BadLocationException {
		return document.getLineInformation(position.getLine()).getOffset() + position.getCharacter();
	}
	
	public static int offset(Position position, TextDocumentIdentifier id) {
		try {
			return toOffset(position, toDocument(id));
		} catch(BadLocationException e) {
			return -1;
		}
	}
	
	public static Range range(int startOffset, int endOffset, TextDocumentIdentifier id) {
		IDocument document = toDocument(id);
		
		try {
			return new Range(toPosition(startOffset, document), toPosition(endOffset, document));
		} catch(BadLocationException e) {
			return new Range(new Position(-1, -1), new Position(-1, -1));
		}
	}

	public static TextDocumentPositionParams toTextDocumentPosistionParams(URI fileUri, int offset, IDocument document)
			throws BadLocationException {
		Position start = toPosition(offset, document);
		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(start);
		param.setUri(fileUri.toString());
		TextDocumentIdentifier id = new TextDocumentIdentifier();
		id.setUri(fileUri.toString());
		param.setTextDocument(id);
		return param;
	}
	
	public static IDocument toDocument(TextDocumentIdentifier id) {
		return LSPEclipseUtils.getDocument(findResourceFor(id.getUri()));
	}

	public static int toEclipseMarkerSeverity(DiagnosticSeverity lspSeverity) {
		if (lspSeverity == null) {
			// if severity is empty it is up to the client to interpret diagnostics
			return IMarker.SEVERITY_ERROR;
		}
		switch (lspSeverity) {
		case Error:
			return IMarker.SEVERITY_ERROR;
		case Warning:
			return IMarker.SEVERITY_WARNING;
		default:
			return IMarker.SEVERITY_INFO;
		}
	}
	
	@Nullable
	public static IResource findResourceFor(@Nullable String uri) {
		if (uri == null || uri.isEmpty()) {
			return null;
		}
		String convertedUri = uri.replace("file:///", "file:/"); //$NON-NLS-1$//$NON-NLS-2$
		convertedUri = convertedUri.replace("file://", "file:/"); //$NON-NLS-1$//$NON-NLS-2$
		IPath path = Path.fromOSString(new File(URI.create(convertedUri)).getAbsolutePath());
		IProject project = null;
		for (IProject aProject : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			IPath location = aProject.getLocation();
			if (location != null && location.isPrefixOf(path)
					&& (project == null || project.getLocation().segmentCount() < location.segmentCount())) {
				project = aProject;
			}
		}
		if (project == null) {
			return null;
		}
		IPath projectRelativePath = path.removeFirstSegments(project.getLocation().segmentCount());
		if (projectRelativePath.isEmpty()) {
			return project;
		} else {
			return project.findMember(projectRelativePath);
		}
	}

	public static void applyEdit(TextEdit textEdit, IDocument document) throws BadLocationException {
		document.replace(
				LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document),
				LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document),
				textEdit.getNewText());
	}

	/**
	 * Method will apply all edits to document as single modification. Needs to
	 * be executed in UI thread.
	 *
	 * @param document
	 *            document to modify
	 * @param edits
	 *            list of LSP TextEdits
	 */
	public static void applyEdits(IDocument document, List<? extends TextEdit> edits) {
		if (document == null || edits.isEmpty()) {
			return;
		}

		IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
		if (manager != null) {
			manager.beginCompoundChange();
		}

		MultiTextEdit edit = new MultiTextEdit();
		for (TextEdit textEdit : edits) {
			if (textEdit != null) {
				try {
					int offset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
					int length = LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - offset;
					edit.addChild(new ReplaceEdit(offset, length, textEdit.getNewText()));
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}
		try {
			RewriteSessionEditProcessor editProcessor = new RewriteSessionEditProcessor(document, edit,
					org.eclipse.text.edits.TextEdit.NONE);
			editProcessor.performEdits();
		} catch (MalformedTreeException | BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		if (manager != null) {
			manager.endCompoundChange();
		}
	}
	
	public static String textContent(String uri) {
		IDocument doc = getDocument(findResourceFor(uri));
		
		return doc.get();
	}

	@Nullable
	public static IDocument getDocument(@Nullable IResource resource) {
		if (resource == null) {
			return null;
		}

		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		IDocument document = null;
		ITextFileBuffer buffer = bufferManager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
		if (buffer != null) {
			document = buffer.getDocument();
		} else if (resource.getType() == IResource.FILE) {
			try {
				bufferManager.connect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
				return document;
			}
			buffer = bufferManager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
			if (buffer != null) {
				document = buffer.getDocument();
			}
		}
		return document;
	}

	public static void openInEditor(Location location, IWorkbenchPage page) {
		IEditorPart part = null;
		IDocument targetDocument = null;
		IResource targetResource = LSPEclipseUtils.findResourceFor(location.getUri());
		try {
			if (targetResource != null && targetResource.getType() == IResource.FILE) {
				part = IDE.openEditor(page, (IFile) targetResource);
				targetDocument = FileBuffers.getTextFileBufferManager()
				        .getTextFileBuffer(targetResource.getFullPath(), LocationKind.IFILE).getDocument();
			} else {
				URI fileUri = URI.create(location.getUri()).normalize();
				IFileStore fileStore =  EFS.getLocalFileSystem().getStore(fileUri);
				IFileInfo fetchInfo = fileStore.fetchInfo();
				if (!fetchInfo.isDirectory() && fetchInfo.exists()) {
					part = IDE.openEditorOnFileStore(page, fileStore);
					ITextFileBuffer fileStoreTextFileBuffer = FileBuffers.getTextFileBufferManager()
							.getFileStoreTextFileBuffer(fileStore);
					targetDocument = fileStoreTextFileBuffer.getDocument();
				}
			}
		} catch (PartInitException e) {
			LanguageServerPlugin.logError(e);
		}
		try {
			if (part instanceof AbstractTextEditor) {
				AbstractTextEditor editor = (AbstractTextEditor) part;
				int offset = LSPEclipseUtils.toOffset(location.getRange().getStart(), targetDocument);
				int endOffset = LSPEclipseUtils.toOffset(location.getRange().getEnd(), targetDocument);
				editor.getSelectionProvider()
				        .setSelection(new TextSelection(offset, endOffset > offset ? endOffset - offset : 0));
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	public static IDocument getDocument(ITextEditor editor) {
		try {
			Method getSourceViewerMethod= AbstractTextEditor.class.getDeclaredMethod("getSourceViewer"); //$NON-NLS-1$
			getSourceViewerMethod.setAccessible(true);
			ITextViewer viewer = (ITextViewer) getSourceViewerMethod.invoke(editor);
			return viewer.getDocument();
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			return null;
		}
	}
	
	public static IDocument getDocument(IEditorInput editorInput) {
		if(editorInput instanceof IFileEditorInput) {
			IFileEditorInput fileEditorInput = (IFileEditorInput)editorInput;
				return getDocument(fileEditorInput.getFile());
		}else if(editorInput instanceof IPathEditorInput) {
			IPathEditorInput pathEditorInput = (IPathEditorInput)editorInput;
			return getDocument(ResourcesPlugin.getWorkspace().getRoot().getFile(pathEditorInput.getPath()));
		}else if(editorInput instanceof IURIEditorInput) {
			IURIEditorInput uriEditorInput = (IURIEditorInput)editorInput;
			return getDocument(findResourceFor(uriEditorInput.getURI().toString()));
		}
		return null;
	}

	/**
	 * Applies a workspace edit. It does simply change the underlying documents.
	 *
	 * @param wsEdit
	 */
	public static void applyWorkspaceEdit(WorkspaceEdit wsEdit) {
		CompositeChange change = toCompositeChange(wsEdit);
		PerformChangeOperation changeOperation = new PerformChangeOperation(change);
		try {
			ResourcesPlugin.getWorkspace().run(changeOperation, new NullProgressMonitor());
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	/**
	 * Returns a ltk {@link CompositeChange} from a lsp {@link WorkspaceEdit}.
	 *
	 * @param wsEdit
	 * @return a ltk {@link CompositeChange} from a lsp {@link WorkspaceEdit}.
	 */
	public static CompositeChange toCompositeChange(WorkspaceEdit wsEdit) {
		CompositeChange change = new CompositeChange("LSP Workspace Edit"); //$NON-NLS-1$
		List<TextDocumentEdit> documentChanges = wsEdit.getDocumentChanges();
		if (documentChanges != null) {
			// documentChanges are present, the latter are preferred over changes
			// see specification at
			// https://github.com/Microsoft/language-server-protocol/blob/master/protocol.md#workspaceedit
			documentChanges.stream().forEach(action -> {
				VersionedTextDocumentIdentifier id = action.getTextDocument();
				String uri = id.getUri();
				List<TextEdit> textEdits = action.getEdits();
				fillTextEdits(uri, textEdits, change);
			});
		} else {
			Map<String, List<TextEdit>> changes = wsEdit.getChanges();
			if (changes != null) {
				for (java.util.Map.Entry<String, List<TextEdit>> edit : changes.entrySet()) {
					String uri = edit.getKey();
					List<TextEdit> textEdits = edit.getValue();
					fillTextEdits(uri, textEdits, change);
				}
			}
		}
		return change;
	}

	/**
	 * Transform LSP {@link TextEdit} list into ltk {@link DocumentChange} and add
	 * it in the given ltk {@link CompositeChange}.
	 *
	 * @param uri
	 *            document URI to update
	 * @param textEdits
	 *            LSP text edits
	 * @param change
	 *            ltk change to update
	 */
	private static void fillTextEdits(String uri, List<TextEdit> textEdits, CompositeChange change) {
		IDocument document = LSPEclipseUtils.getDocument(LSPEclipseUtils.findResourceFor(uri));
		for (TextEdit textEdit : textEdits) {
			try {
				int offset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
				int length = LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - offset;
				DocumentChange documentChange = new DocumentChange("Change in document " + uri, document); //$NON-NLS-1$
				documentChange.initializeValidationData(new NullProgressMonitor());
				documentChange.setEdit(new ReplaceEdit(offset, length, textEdit.getNewText()));
				change.add(documentChange);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	public static URI toUri(IPath absolutePath) {
		return toUri(absolutePath.toFile());
	}

	public static URI toUri(IResource resource) {
		return toUri(resource.getLocation());
	}

	public static URI toUri(File file) {
		// URI scheme specified by language server protocol and LSP
		try {
			return new URI("file", "", file.getAbsoluteFile().toURI().getPath(), null); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (URISyntaxException e) {
			LanguageServerPlugin.logError(e);
			return file.getAbsoluteFile().toURI();
		}
	}

	// TODO consider using Entry/SimpleEntry instead
	private static final class Pair<K, V> {
		K key;
		V value;
		Pair(K key,V value) {
			this.key = key;
			this.value = value;
		}
	}

	/**
	 * Very empirical and unsafe heuristic to turn unknown command arguments
	 * into a workspace edit...
	 */
	public static WorkspaceEdit createWorkspaceEdit(List<Object> commandArguments, IResource initialResource) {
		WorkspaceEdit res = new WorkspaceEdit();
		Map<String, List<TextEdit>> changes = new HashMap<>();
		res.setChanges(changes);
		Pair<IResource, List<TextEdit>> currentEntry = new Pair<>(initialResource, new ArrayList<>());
		commandArguments.stream().flatMap(item -> {
			if (item instanceof List) {
				return ((List<?>)item).stream();
			} else {
				return Collections.singleton(item).stream();
			}
		}).forEach(arg -> {
			if (arg instanceof String) {
				changes.put(currentEntry.key.getLocationURI().toString(), currentEntry.value);
				IResource resource = LSPEclipseUtils.findResourceFor((String)arg);
				if (resource != null) {
					currentEntry.key = resource;
					currentEntry.value = new ArrayList<>();
				}
			} else if (arg instanceof WorkspaceEdit) {
				changes.putAll(((WorkspaceEdit)arg).getChanges());
			} else if (arg instanceof TextEdit) {
				currentEntry.value.add((TextEdit)arg);
			} else if (arg instanceof Map) {
				Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
				TextEdit edit = gson.fromJson(gson.toJson(arg), TextEdit.class);
				if (edit != null) {
					currentEntry.value.add(edit);
				}
			}
		});
		changes.put(currentEntry.key.getLocationURI().toString(), currentEntry.value);
		return res;
	}

	@Nullable public static IFile getFile(IDocument document) {
		ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(document);
		if (buffer == null) {
			return null;
		}
		final IPath location = buffer.getLocation();
		return ResourcesPlugin.getWorkspace().getRoot().getFile(location);
	}
}
