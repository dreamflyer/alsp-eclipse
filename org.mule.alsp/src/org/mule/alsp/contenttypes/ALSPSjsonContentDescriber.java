package org.mule.alsp.contenttypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.stream.Stream;

import org.eclipse.core.internal.content.LazyInputStream;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.ITextContentDescriber;

public class ALSPSjsonContentDescriber implements ITextContentDescriber {
	public int describe(InputStream contents, IContentDescription description) throws IOException {
		byte[] bytes = new byte[contents.available()];
		
		contents.read(bytes);
		
		String content = new String(bytes, "utf8");
		
		if(content.isEmpty()) {
			return VALID;
		}
		
		if(isValidContent(content)) {
			return VALID;
		}
		
		return INVALID;
	}
	
	public int describe(Reader contents, IContentDescription description) throws IOException {
		Object[] lines = new BufferedReader(contents).lines().toArray();
		
		if(lines.length == 0) {
			return VALID;
		}
		
		for(int i = 0; i < lines.length; i++) {
			if(isValidContent(lines[i].toString())) {
				return VALID;
			}
		}
		
		return INVALID;
	}
	
	private boolean isValidContent(String content) {
		return content.contains("\"swagger\": \"2.0\"");
	}

	public QualifiedName[] getSupportedOptions() {
		return new QualifiedName[0];
	}
}