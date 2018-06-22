/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.ProjectSpecificLanguageServerWrapper;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class CodeActionMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private @NonNull Command command;

	public CodeActionMarkerResolution(@NonNull Command command) {
		this.command = command;
	}

	@Override
	public String getLabel() {
		return this.command.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		// This is a *client-side* command, no need to go through workspace/executeCommand operation
		// TODO? Consider binding LS commands to Eclipse commands and handlers???
		if (command.getArguments() == null) {
			return;
		}

		if (marker.getResource().getType() == IResource.FILE) {
			String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
			if (languageServerId != null) {
				IFile file = (IFile) marker.getResource();
				LanguageServerDefinition definition = LanguageServersRegistry.getInstance()
						.getDefinition(languageServerId);
				if (definition != null) {
					try {
						ProjectSpecificLanguageServerWrapper wrapper = LanguageServiceAccessor
								.getLSWrapperForConnection(file.getProject(), definition);
						LanguageServer server = wrapper.getServer();
						ServerCapabilities capabilities = wrapper.getServerCapabilities();
						if (server != null && capabilities != null) {
							ExecuteCommandOptions provider = capabilities.getExecuteCommandProvider();
							if (provider != null && provider.getCommands().contains(command.getCommand())) {
								ExecuteCommandParams params = new ExecuteCommandParams();
								params.setCommand(command.getCommand());
								params.setArguments(command.getArguments());
								server.getWorkspaceService().executeCommand(params);
								return;
							}
						}
					} catch (IOException e) {
						// log and let the code fall through for LSPEclipseUtils to handle
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
		WorkspaceEdit edit = LSPEclipseUtils.createWorkspaceEdit(command.getArguments(), marker.getResource());
		LSPEclipseUtils.applyWorkspaceEdit(edit);
	}

	@Override
	public String getDescription() {
		return command.getTitle();
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		// TODO Auto-generated method stub
		return new IMarker[0];
	}

}
