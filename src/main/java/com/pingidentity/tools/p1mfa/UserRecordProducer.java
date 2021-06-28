package com.pingidentity.tools.p1mfa;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

public class UserRecordProducer implements Runnable {
	
	private final BlockingQueue<String> queue;
	private final String producerFolder;

	public UserRecordProducer(BlockingQueue<String> queue, Properties configuration, String producerFolder) {
		this.queue = queue;			
		this.producerFolder = producerFolder;
	}

	@Override
	public void run() {
		
		File producerFolderFile = new File(this.producerFolder);
		
		for(String fileName: producerFolderFile.list())
		{
			try {
				queue.put(fileName);
			} catch (InterruptedException e) {
				System.err.println("Error adding filename to queue: " + fileName);
			}
		}
		
		
	}


}
