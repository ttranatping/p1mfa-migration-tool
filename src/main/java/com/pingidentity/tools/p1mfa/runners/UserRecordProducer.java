package com.pingidentity.tools.p1mfa.runners;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

public class UserRecordProducer implements Runnable {
	
	private final Logger logger = Logger.getLogger(UserRecordProducer.class);
			
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
				logger.error("Error adding filename to queue: " + fileName, e);
			}
		}
		
		
	}


}
