package org.mule.alsp.contenttypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.eclipse.core.internal.content.LazyInputStream;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.ITextContentDescriber;

public class ALSPRamlContentDescriber implements ITextContentDescriber {
	public int describe(InputStream contents, IContentDescription description) throws IOException {
		byte[] bytes = new byte[contents.available()];
		
		contents.read(bytes);
		
		return VALID;
	}
	
	public int describe(Reader contents, IContentDescription description) throws IOException {
		return VALID;
	}

	public QualifiedName[] getSupportedOptions() {
		return new QualifiedName[0];
	}
}