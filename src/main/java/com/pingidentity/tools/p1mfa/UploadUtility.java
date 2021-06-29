package com.pingidentity.tools.p1mfa;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.PropertyConfigurator;

import com.pingidentity.tools.p1mfa.runners.ProcessCSV;
import com.pingidentity.tools.p1mfa.runners.UserRecordConsumer;
import com.pingidentity.tools.p1mfa.runners.UserRecordProducer;

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

		String producerFolder = configuration.getProperty("producer.output.folder", "output/producer");	
		if(args.length > 2)
			producerFolder = args[2];
		
		String consumerFolder = configuration.getProperty("consumer.output.folder", "output/consumer");		
		if(args.length > 3)
			consumerFolder = args[3];
		
		int consumerThreads = Integer.parseInt(configuration.getProperty("consumer.threads", "10"));
		int queueSize = Integer.parseInt(configuration.getProperty("consumer.queue.size", "100000"));
		
		BlockingQueue<String> queue = new ArrayBlockingQueue<String>(queueSize);
		
		PropertyConfigurator.configure(configuration);
		
		if(mode.equalsIgnoreCase("CREATE"))
		{
			ProcessCSV producer = new ProcessCSV(configuration, producerFolder);	
			producer.run();
		}
		else if(mode.toUpperCase().startsWith("PROVISION-"))
		{
			if(mode.equals("PROVISION-USERS"))
				producerFolder = producerFolder + File.separator + Constants.FOLDER_CREATE_USERS;
			else if(mode.equals("PROVISION-MFA-EMAIL"))
				producerFolder = producerFolder + File.separator + Constants.FOLDER_MFA_EMAIL;
			else if(mode.equals("PROVISION-MFA-SMS"))
				producerFolder = producerFolder + File.separator + Constants.FOLDER_MFA_SMS;
			
			UserRecordProducer producer = new UserRecordProducer(queue, configuration, producerFolder);
	
			new Thread(producer).start();
	
			for (int count = 0; count < consumerThreads; count++) {
				UserRecordConsumer consumer = new UserRecordConsumer("consumer" + count, queue, configuration,
						producerFolder, consumerFolder, mode);
				new Thread(consumer).start();
	
			}
		}

	}
}
