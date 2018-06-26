package org.mule.alsp.converters;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.mulesoft.language.common.dtoTypes.IValidationIssue;

public class ValidationHandler implements org.mulesoft.language.client.jvm.ValidationHandler {
	private List<LanguageClient> remoteProxies;
	
	public ValidationHandler(List<LanguageClient> remoteProxies) {
		this.remoteProxies = remoteProxies;
	}
	
	public void success(String pointOfView, List<IValidationIssue> issues) {
		List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
		
		issues.forEach(issue -> {
			Range range = LSPEclipseUtils.range(issue.range().start(), issue.range().end(), new TextDocumentIdentifier(pointOfView));
			
			diagnostics.add(new Diagnostic(range, issue.text()));
		});
		
		remoteProxies.stream().forEach(p -> p.publishDiagnostics(new PublishDiagnosticsParams(pointOfView, diagnostics)));
	}
}
