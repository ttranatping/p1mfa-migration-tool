package com.pingidentity.tools.p1mfa;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class UserRecordConsumer implements Runnable {

	private static final long SLEEP_TIMER = 5000;

	private final String consumerName, producerFolder, consumerFolder;
	private final BlockingQueue<String> queue;
	private final Properties configuration;

	private PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();

	public UserRecordConsumer(String consumerName, BlockingQueue<String> queue, Properties configuration,
			String producerFolder, String consumerFolder) {
		this.consumerName = consumerName;
		this.producerFolder = producerFolder;
		this.consumerFolder = consumerFolder;
		this.queue = queue;
		this.configuration = configuration;

	}

	@Override
	public void run() {

		while (true) {
			if (queue.isEmpty()) {
				try {
					Thread.sleep(SLEEP_TIMER);
				} catch (InterruptedException e) {
					// do nothing
				}

				if (queue.isEmpty()) {
					System.out.println("Queue is empty, terminating consumer: " + this.consumerName);
					break;
				}
			}

			String record = null;
			try {
				record = queue.take();
				System.out.println(this.consumerName + ":" + record);

				String id = createUser(record);

			} catch (InterruptedException | IOException e) {
				System.err.println("Failed to take queue item. E:" + e.getMessage());
				return;
			}
		}

	}

	public String createUser(String username) throws IOException {

		String fileContent = FileUtils.readFileToString(
				new File(producerFolder + File.separator + Constants.FOLDER_CREATE_USERS + File.separator + username),
				Charset.defaultCharset());

		System.out.println(fileContent);

		return null;
	}

}
