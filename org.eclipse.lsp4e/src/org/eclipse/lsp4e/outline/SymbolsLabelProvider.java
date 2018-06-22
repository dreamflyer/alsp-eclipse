/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.progress.ProgressManager;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonLabelProvider;

public class SymbolsLabelProvider extends LabelProvider implements ICommonLabelProvider, IStyledLabelProvider {
	private Map<Image, Image[]> overlays = new HashMap<>();
	
	private boolean showLocation;
	
	private boolean showKind;
	
	private boolean showRange = false;
	
	public SymbolsLabelProvider() {
		this(false, true);
	}
	
	public SymbolsLabelProvider(boolean showLocation, boolean showKind) {
		this.showLocation = showLocation;
		
		this.showKind = showKind;
	}
	
	@Override
	public Image getImage(Object element) {
		if(element == null) {
			return null;
		}
		
		if(element == LSSymbolsContentProvider.COMPUTING) {
			return JFaceResources.getImage(ProgressManager.WAITING_JOB_KEY);
		}
		
		if(element instanceof Throwable) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
		}
		
		SymbolInformation symbolInformation = (SymbolInformation) element;
		
		Image res = LSPImages.getImage(LSPImages.IMG_RIGHTARROW);
		
		IResource resource = LSPEclipseUtils.findResourceFor(symbolInformation.getLocation().getUri());
		
		if(resource != null) {
			try {
				IDocument doc = LSPEclipseUtils.getDocument(resource);
				
				int maxSeverity = -1;
				
				for(IMarker marker : resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
					int offset = marker.getAttribute(IMarker.CHAR_START, -1);
					
					if(offset >= 0 && offset >= startOffset(symbolInformation, doc) && offset <= endOffset(symbolInformation, doc)) {
						maxSeverity = Math.max(maxSeverity, marker.getAttribute(IMarker.SEVERITY, -1));
					}
				}
				
				if(maxSeverity > IMarker.SEVERITY_INFO) {
					return getOverlay(res, maxSeverity);
				}
			} catch(CoreException | BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return res;
	}
	
	private int startOffset(SymbolInformation info, IDocument doc) throws BadLocationException {
		return LSPEclipseUtils.toOffset(info.getLocation().getRange().getStart(), doc);
	}
	
	private int endOffset(SymbolInformation info, IDocument doc) throws BadLocationException {
		return LSPEclipseUtils.toOffset(info.getLocation().getRange().getEnd(), doc);
	}
	
	private Image getOverlay(Image res, int maxSeverity) {
		if(maxSeverity != 1 && maxSeverity != 2) {
			throw new IllegalArgumentException("Severity " + maxSeverity + " not supported."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		if(!this.overlays.containsKey(res)) {
			this.overlays.put(res, new Image[2]);
		}
		
		Image[] currentOverlays = this.overlays.get(res);
		
		if(currentOverlays[maxSeverity - 1] == null) {
			String overlayId = null;
			
			if(maxSeverity == IMarker.SEVERITY_ERROR) {
				overlayId = ISharedImages.IMG_DEC_FIELD_ERROR;
			} else if(maxSeverity == IMarker.SEVERITY_WARNING) {
				overlayId = ISharedImages.IMG_DEC_FIELD_WARNING;
			}
			
			ImageDescriptor imageDescriptor = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(overlayId);
			
			DecorationOverlayIcon overlayIcon = new DecorationOverlayIcon(res, imageDescriptor, IDecoration.BOTTOM_LEFT);
			
			currentOverlays[maxSeverity - 1] = overlayIcon.createImage();
		}
		
		return currentOverlays[maxSeverity - 1];
	}
	
	@Override
	public String getText(Object element) {
		return getStyledText(element).getString();
	}
	
	@Override
	public StyledString getStyledText(Object element) {
		if(element == LSSymbolsContentProvider.COMPUTING) {
			return new StyledString(Messages.outline_computingSymbols);
		}
		
		if(element instanceof Throwable) {
			String message = ((Throwable) element).getMessage();
			
			if(message == null) {
				message = element.getClass().getName();
			}
			
			return new StyledString(message);
		}
		
		if(element instanceof LSPDocumentInfo) {
			return new StyledString(((LSPDocumentInfo) element).getFileUri().getPath());
		}
		
		StyledString res = new StyledString();
		
		if(element == null) {
			return res;
		}
		
		SymbolInformation symbol = (SymbolInformation) element;
		
		if(symbol.getName() != null) {
			res.append(symbol.getName(), null);
		}
		
		if(showRange) {
			res.append(" " + rangeToString(symbol.getLocation().getRange()), null);
		}
		
		if(showLocation) {
			URI uri = URI.create(symbol.getLocation().getUri());
			
			res.append(' ');
			
			res.append(uri.getPath(), StyledString.QUALIFIER_STYLER);
		}
		
		return res;
	}
	
	@Override
	public void restoreState(IMemento aMemento) {
		
	}
	
	@Override
	public void saveState(IMemento aMemento) {
		
	}
	
	@Override
	public String getDescription(Object anElement) {
		return null;
	}
	
	@Override
	public void init(ICommonContentExtensionSite aConfig) {
		
	}
	
	private String rangeToString(Range range) {
		return "[" + positionToString(range.getStart()) + ", " + positionToString(range.getEnd()) + "]";
	}
	
	private String positionToString(Position pos) {
		return "(" + pos.getLine() + ", " + pos.getCharacter() + ")";
	}
}
