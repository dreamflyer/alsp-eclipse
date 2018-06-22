package org.mule.alsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4e.server.StreamConnectionProvider1;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

public class ALSPConnectionProvider implements StreamConnectionProvider1 {
	private Socket socket;
	
	private ALSPSever mockServer = null;
	
	private static int cnt = 0;
	
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
					mockServer = new ALSPSever();
					
					mockServer.start();	
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}).start();
		
		sleepUntilInitialized();
	}
	
	synchronized void sleepUntilInitialized() {
		while(mockServer == null) {
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
		
		return mockServer.consumerInputStream;
	}
	
	@Override
	public OutputStream getOutputStream() {
		sleepUntilInitialized();
		
		return mockServer.consumerOutputStream;
	}
	
	@Override
	public void stop() {
		
	}
}
