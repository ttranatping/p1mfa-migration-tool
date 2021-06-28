package com.pingidentity.tools.p1mfa.runners;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.json.JSONObject;

import com.pingidentity.tools.p1mfa.utils.HttpResponseObj;
import com.pingidentity.tools.p1mfa.utils.MASSLClient;

public class UserRecordConsumer implements Runnable {

	private static final long SLEEP_TIMER = 5000;

	private final String consumerName, producerFolder, consumerFolder;
	private final BlockingQueue<String> queue;

	private final String authBaseUrl;
	private final String apiBaseUrl;
	private final String scopes;
	private final String clientId;
	private final String clientSecret;
	private final String envId;
	private final String tokenEndpoint;
	private final String mode;

	private String currentAccessToken = null;
	private Long currentAccessTokenExpiresIn = null;
	
	private int printReportCount = 1000;

	private PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();

	public UserRecordConsumer(String consumerName, BlockingQueue<String> queue, Properties configuration,
			String producerFolder, String consumerFolder, String mode) {
		this.consumerName = consumerName;
		this.producerFolder = producerFolder;
		this.consumerFolder = consumerFolder;
		this.queue = queue;
		
		this.mode = mode;

		this.authBaseUrl = configuration.getProperty("p1mfa.connection.auth.baseurl", "https://auth.pingone.com");
		this.apiBaseUrl = configuration.getProperty("p1mfa.connection.api.baseurl", "https://api.pingone.com/v1");
		this.scopes = configuration.getProperty("p1mfa.connection.client.scopes", "");
		this.clientId = configuration.getProperty("p1mfa.connection.client.id", "");
		this.clientSecret = configuration.getProperty("p1mfa.connection.client.secret", "");
		this.envId = configuration.getProperty("p1mfa.environment.id", "");

		int connectionTimeout = Integer.parseInt(configuration.getProperty("http.timeout.connection", "10000"));
		int maxRoute = Integer.parseInt(configuration.getProperty("http.max.route", "5"));
		int maxConnections = Integer.parseInt(configuration.getProperty("http.max.connections", "10"));
		
		printReportCount = Integer.parseInt(configuration.getProperty("print.report.count", "1000"));
		
		File idUsernameMappingDir = new File(this.consumerFolder + File.separator + "username-id-mappings");
		idUsernameMappingDir.mkdirs();
		
		poolingConnManager.setValidateAfterInactivity(connectionTimeout);
		poolingConnManager.setDefaultMaxPerRoute(maxRoute);
		poolingConnManager.setMaxTotal(maxConnections);

		this.tokenEndpoint = (this.envId != null && !this.envId.trim().equals(""))
				? String.format(authBaseUrl + "/%s/as/token", this.envId)
				: authBaseUrl + "/as/token";

	}

	@Override
	public void run() {
		
		int counter = 0;
		
		long start = Instant.now().getEpochSecond();

		while (true) {
			
			counter++;
			
			printReport(start, counter);
			
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
				refreshAccessToken();
				String idMappingFilename = this.consumerFolder + File.separator + "username-id-mappings" + File.separator + record;
				File idUsernameMapping = new File(idMappingFilename);	
				
				if(mode.equalsIgnoreCase("PROVISION-USERS"))
				{
					String id = createUser(record);
					
					if(id != null)
						FileUtils.writeStringToFile(idUsernameMapping, id, Charset.defaultCharset(), false);
				}
				else if(mode.equalsIgnoreCase("PROVISION-MFA-EMAIL"))
				{
					String id = FileUtils.readFileToString(idUsernameMapping, Charset.defaultCharset()).trim();
					createMFADevice(record, id, "email");
				}
				else if(mode.equalsIgnoreCase("PROVISION-MFA-SMS"))
				{
					String id = FileUtils.readFileToString(idUsernameMapping, Charset.defaultCharset()).trim();
					createMFADevice(record, id, "sms");
				}

			} catch (InterruptedException | IOException e) {
				System.err.println("Failed to take queue item. E:" + e.getMessage());
				return;
			}
		}

	}

	private void printReport(long start, int counter) {
		
		int remainder = (counter%printReportCount);
		
		if(remainder != 0)
			return;
		
		Instant now = Instant.now();
		
		long timeDiff = now.getEpochSecond()-start;
		
		Float minutes = Float.parseFloat("" + timeDiff)/60;
		Float tpm = counter/minutes;
		
		System.out.println(String.format("Thread: %s, Minutes: %.2f, Tx per minute: %.2f", this.consumerName, minutes, tpm));
		
	}

	private String refreshAccessToken() {

		if (this.currentAccessToken != null && this.currentAccessTokenExpiresIn != null
				&& this.currentAccessTokenExpiresIn > (new Date()).getTime()) {
			return this.currentAccessToken;
		}

		System.out.println("Getting new access_token");

		Map<String, String> headers = new HashMap<String, String>();

		headers.put("Content-Type", "application/x-www-form-urlencoded");

		Map<String, String> params = new HashMap<String, String>();
		params.put("grant_type", "client_credentials");
		params.put("scope", this.scopes);
		params.put("client_id", this.clientId);
		params.put("client_secret", this.clientSecret);

		HttpResponseObj response = null;
		try {
			response = MASSLClient.executeHTTP(this.poolingConnManager, this.tokenEndpoint, "POST", headers, params,
					null, null, null, null, null, false, 30000);

		} catch (Exception e) {
			System.err.println("Could not receive Access Token:" + e.getMessage());
			return null;
		}

		if (response == null) {
			System.err.println("Could not receive Access Token. HTTP Response is null");
			return null;
		}

		if (response.getStatusCode() != 200) {
			System.err.println("Could not receive Access Token. HTTP Response status is: " + response.getStatusCode());
			return null;
		}

		JSONObject responseObject = new JSONObject(response.getResponseBody());

		this.currentAccessToken = responseObject.getString("access_token");

		Integer accessTokenExpires = responseObject.getInt("expires_in");
		if (accessTokenExpires != null) {
			// 750 = 1000ms less 25%
			this.currentAccessTokenExpiresIn = (new Date()).getTime() + (accessTokenExpires * 750);
		}

		return this.currentAccessToken;
	}

	private String createUser(String username) throws IOException {

		String incomingFileName = producerFolder + File.separator
				+ username;
		String endpoint = String.format(this.apiBaseUrl + "/environments/%s/users", this.envId);

		return createObject(username, incomingFileName, endpoint);
	}

	private String createMFADevice(String username, String id, String mfaType) throws IOException {
		String incomingFileName = producerFolder + File.separator + username;
		
		if(new File(incomingFileName).exists())
		{
			String endpoint = String.format(this.apiBaseUrl + "/environments/%s/users/%s/devices", this.envId, id);
	
			return createObject(username, incomingFileName, endpoint);
		}
		else
			return null;

	}

	private String createObject(String username, String incomingFileName, String endpoint) throws IOException {

		File incomingFile = new File(incomingFileName);

		String fileContent = FileUtils.readFileToString(incomingFile, Charset.defaultCharset());

		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + this.currentAccessToken);

		HttpResponseObj response = null;
		try {
			response = MASSLClient.executeHTTP(this.poolingConnManager, endpoint, "POST", headers, fileContent, null,
					null, null, null, null, false, 30000);
		} catch (Exception e) {
			System.err.println("Could not receive Access Token:" + e.getMessage());
			return null;
		}

		String responseBody = response.getResponseBody();
		
		String processedFile = this.consumerFolder + File.separator + response.getStatusCode() + File.separator + mode + File.separator + incomingFileName
				.replace(this.producerFolder, "");

		String processedDirectory = processedFile.substring(0, processedFile.lastIndexOf(File.separator));

		File processedDirectoryFile = new File(processedDirectory);
		
		if(response.getStatusCode() != 429)
			FileUtils.moveFileToDirectory(incomingFile, processedDirectoryFile, true);

		FileUtils.write(new File(processedFile + ".response"), responseBody,
				Charset.defaultCharset(), false);

		if (response.getStatusCode() == 201) {
			JSONObject responseObject = new JSONObject(responseBody);

			return responseObject.getString("id");
		} else
			return null;
	}

}
