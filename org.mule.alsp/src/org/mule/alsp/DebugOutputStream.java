package org.mule.alsp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.net.Socket;

public class DebugOutputStream extends OutputStream {
	private OutputStream stream;
	
	DebugOutputStream(OutputStream stream) {
		this.stream = stream;
	}
	
	public void write(byte[] b, int off, int len) throws IOException {
		stream.write(b, off, len);
	}
	
	public void write(int b) throws IOException {
		stream.write(b);
	}	
}
