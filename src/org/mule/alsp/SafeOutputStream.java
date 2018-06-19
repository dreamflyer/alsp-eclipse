package org.mule.alsp;

import java.io.IOException;
import java.io.OutputStream;

public class SafeOutputStream extends OutputStream {
	private OutputStream stream;
	
	private WriteTask task = new WriteTask();
	
	SafeOutputStream(OutputStream stream) {
		this.stream = stream;
		
		new Thread(new Runnable() {
			public void run() {
				try {
					synchronized(SafeOutputStream.this) {
						while(task != null) {
							SafeOutputStream.this.wait();
						
							if(stream == null) {
								return;
							}
						
							stream.write(task.b, task.off, task.len);
							
							SafeOutputStream.this.notify();
						}
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}				
			}
		}).start();
	}
	
	public synchronized void write(byte[] b, int off, int len) throws IOException {
		task.setup(b, off, len);
		
		this.notify();
		
		try {
			this.wait();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void write(int b) throws IOException {
		stream.write(b);
	}
}

class WriteTask {
	byte[] b;
	
	int off;
	
	int len;
	
	void setup(byte[] b, int off, int len) {
		this.b = b;
		
		this.off = off;
		
		this.len = len;
	}
}
