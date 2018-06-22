package org.mule.alsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import org.eclipse.lsp4e.server.StreamConnectionProvider1;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

public class ALSPConnectionProvider implements StreamConnectionProvider1 {
	private ALSPSever server = null;
	
	@Override
	public void handleMessage(Message message, LanguageServer languageServer, URI rootURI) {
		StreamConnectionProvider1.super.handleMessage(message, languageServer, rootURI);
	}
	
	@Override
	public Object getInitializationOptions(URI rootUri) {
		return StreamConnectionProvider1.super.getInitializationOptions(rootUri);
	}
	
	@Override
	public void start() throws IOException {
		new Thread(new Runnable() {
			public void run() {
				try {
					server = new ALSPSever();
					
					server.start();	
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}).start();
		
		sleepUntilInitialized();
	}
	
	synchronized void sleepUntilInitialized() {
		while(server == null) {
			try {
				wait(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public InputStream getInputStream() {
		sleepUntilInitialized();
		
		return server.consumerInputStream;
	}
	
	@Override
	public OutputStream getOutputStream() {
		sleepUntilInitialized();
		
		return server.consumerOutputStream;
	}
	
	@Override
	public void stop() {
		
	}
}
