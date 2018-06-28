package org.mule.alsp;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class DebugInputStream extends InputStream {
	private InputStream stream;
	
	private String name;
	
	DebugInputStream(InputStream stream, String name) {
		this.stream = stream;
		
		this.name = name;
	}
	
	public int read() {
		try {
			return stream.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return -1;
	}
	
	public int read(byte[] b, int off, int len) throws IOException {
		return this.stream.read(b, off, len);
	}
	
	public int available() throws IOException {
		return stream.available();
	}
	
	public void close() throws IOException {
		stream.close();
	}
}
