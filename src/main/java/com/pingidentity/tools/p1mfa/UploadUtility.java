package com.pingidentity.tools.p1mfa;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class UploadUtility {
	
	public static final void main(String[] args) throws IOException {

		String mode = "CREATE";
		
		if(args.length > 0)
			mode = args[0];
		
		String configFile = "config.properties";
		if(args.length > 1)
			configFile = args[1];
		
		Properties configuration = new Properties();
		configuration.load(new FileInputStream(new File(configFile)));
		
		int consumerThreads = Integer.parseInt(configuration.getProperty("consumer.threads", "10"));
		int queueSize = Integer.parseInt(configuration.getProperty("consumer.queue.size", "100000"));

		String producerFolder = configuration.getProperty("producer.output.folder", "output/producer");		
		String consumerFolder = configuration.getProperty("consumer.output.folder", "output/consumer");		
		
		BlockingQueue<String> queue = new ArrayBlockingQueue<String>(queueSize);

		if(mode.equalsIgnoreCase("CREATE"))
		{
			ProcessCSV producer = new ProcessCSV(configuration, producerFolder);	
			producer.run();
		}
		else if (mode.equalsIgnoreCase("PROVISION"))
		{
			UserRecordProducer producer = new UserRecordProducer(queue, configuration, producerFolder);
	
			new Thread(producer).start();
	
			for (int count = 0; count < consumerThreads; count++) {
				UserRecordConsumer consumer = new UserRecordConsumer("consumer" + count, queue, configuration,
						producerFolder, consumerFolder);
				new Thread(consumer).start();
	
			}
		}

	}
}
