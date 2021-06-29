package com.pingidentity.tools.p1mfa.runners;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.pingidentity.tools.p1mfa.Constants;

public class ProcessCSV implements Runnable {
	
	private final Logger logger = Logger.getLogger(ProcessCSV.class);
	
	private final Properties configuration;
	private final String populationId, producerFolder;

	public ProcessCSV(Properties configuration, String producerFolder) {
		this.configuration = configuration;
		this.populationId = configuration.getProperty("p1mfa.population.id");
		
		this.producerFolder = producerFolder;
		
		File outputProducer = new File(this.producerFolder);
		outputProducer.mkdirs();
	}

	@Override
	public void run() {
		
		Reader in;
		Iterable<CSVRecord> records = null;
		try {
			in = new FileReader(configuration.getProperty("input.users.csv.file", "users.csv"));
			records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
		} catch (IOException e) {
			logger.error("Exception not load users CSV file", e);
		}
		
		if(records == null)
			logger.error("Could not load users CSV file");
		else
		{
			int counter = 0;

			for(CSVRecord record : records)
			{
				convertCSVToJSON(record);
				convertCSVToJSONMFA(record);
				counter++;
			}
			
			logger.info(String.format("Added %s to the processing queue.\n\n", counter));
		}
		
	}

	private void convertCSVToJSONMFA(CSVRecord record) {
		Map<String, String> mapRecord = record.toMap();
		
		for(String key: mapRecord.keySet())
		{
			if(key.contains("."))
			{
				String topKey = key.split("\\.")[0];
				
				if(!topKey.equalsIgnoreCase("mfa"))
					continue;
				
				String secondKey = key.split("\\.")[1];
				String value = mapRecord.get(key);
				
				if(value == null || value.trim().equals(""))
					continue;
				
				String mfaType = "email";
				
				JSONObject jsonObject = new JSONObject();
				if(secondKey.equals("email"))
				{
					jsonObject.put("type", "EMAIL");
					jsonObject.put("email", value);
				}
				else if(secondKey.equals("sms"))
				{
					mfaType = "sms";
					jsonObject.put("type", "SMS");
					jsonObject.put("phone", value);
				}
				else
					continue;
				
				String fileName = producerFolder + File.separator + Constants.FOLDER_MFA_PREFIX + mfaType + File.separator + record.get("username");
								
				File producerRecordFile = new File(fileName);

				try {
					FileUtils.writeStringToFile(producerRecordFile, jsonObject.toString(4), Charset.defaultCharset(), false);
				} catch (JSONException | IOException e) {
					logger.error("Unable to write to file: " + fileName, e);
				}
			}
		}
	}

	private void convertCSVToJSON(CSVRecord record) {
		JSONObject jsonObject = new JSONObject();
		Map<String, String> mapRecord = record.toMap();
		
		for(String key: mapRecord.keySet())
		{
			if(key.contains("."))
			{
				String topKey = key.split("\\.")[0];
				
				if(topKey.equalsIgnoreCase("mfa"))
					continue;
				
				String secondKey = key.split("\\.")[1];
				
				JSONObject topObject;
				if(jsonObject.has(topKey))
					topObject = jsonObject.getJSONObject(topKey);
				else
				{
					topObject = new JSONObject();
					jsonObject.put(topKey, topObject);
				}
				
				topObject.put(secondKey, mapRecord.get(key));
			}
			else
				jsonObject.put(key, mapRecord.get(key));
		}
		
		JSONObject populationObject = new JSONObject();
		populationObject.put("id", this.populationId);
		
		jsonObject.put("population", populationObject);
		
		jsonObject.put("mfaEnabled", true);
		
		String fileName = producerFolder + File.separator + Constants.FOLDER_CREATE_USERS + File.separator + record.get("username");
		
		File producerRecordFile = new File(fileName);
		
		try {
			FileUtils.writeStringToFile(producerRecordFile, jsonObject.toString(4), Charset.defaultCharset(), false);
			
			return;
		} catch (IOException e) {
			logger.error("Unable to write to file: " + fileName, e);
		}
	}

}
