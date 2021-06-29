# P1MFA Migration Tool

The P1MFA Migration Tool is a utility that helps migrate data into PingOne quickly, implementing the following principals to optimise the migration:

* Multi-threaded
* Managed HTTP Connections
* Reduced calls
    * Cached Access Tokens
    * mfaEnabled set on user creation

## Build Maven Project

Build the project using maven with the following command:

> mvn install

## Configuration

Instantiate config.properties.template by copying it into config.properties.

Edit config.properties, notably the following settings:
* p1mfa.environment.id
* p1mfa.population.id
* p1mfa.connection.auth.baseurl
* p1mfa.connection.api.baseurl
* p1mfa.connection.client.id
* p1mfa.connection.client.secret
* input.users.csv.file
* http.proxy.host
* http.proxy.port
* http.proxy.username
* http.proxy.password

## Create User CSV Import File

Create a CSV file with at minimum, the following columns:
* username
* At least one MFA heading:
    * mfa.email
    * mfa.sms

Note that the heading names are mapped to PingOne attributes, so you could populate more values if required. For example:
* name.given
* name.family
* title

An example can be found [here](loadusers.csv).

## Usage

The utility is performed in 3 stages. Separating the stages allows the migration admin to stage their imports, processing users to its entirety before adding MFA devices. This is particularly useful as "429-Too Many Requests" can simply be retried by running the tool again until all records have completed.

Stages:
1. Process CSV into individual API calls. This allows the migration tool to retry events e.g. if rate limiting has been reached.
2. Provision users. 
    * Optional - only required if the user doesn't exist.
3. Provision MFA (SMS/Email). 
    * Optional - only required if provisioning MFA device(s).
    * Note: Running Provision MFA without provisioning users will require an additional lookup to PingOne (to resolve username-id mapping). This will need to be taken into consideration when determining number of threads to stay within the 100 TPS thresholds. 

### Process CSV

This command processes the CSV file and prepares Admin API calls. The processed Admin API calls are stored in the producer output folder. 
* In the command below, the API calls are persisted under the "migration-1234/input" folder.
* The calls are split up between createusers, mfa-sms and/or mfa-email.

Command: 

> java -jar target/p1mfaupload-0.0.1-jar-with-dependencies.jar CREATE config.properties "migration-1234/input" "migration-1234/output"


### Provision Users

This command processes the create users Admin API calls. 
* It iterates through all of the records found under the producer createusers directory. In the command below, the Admin API calls are found under migration-1234/input/createusers.
* The results are stored in the consumer folder according to HTTP Status codes.
    * 429 - Too Many Requests. These will remain unprocessed and in the producer folder (migration-1234/input/createusers).
    * 201 - Success. These calls are moved from the producer folder to the consumer folder (migration-1234/output/201/createusers).
         * The response is retained for record with the filename <record>.response. E.g. migration-1234/output/201/createusers/username.response.
    * 400/Other - Fail. These calls are moved from the producer folder to the consumer folder (migration-1234/output/400/createusers).
         * The response is retained for troubleshooting with the filename <record>.response. E.g. migration-1234/output/400/createusers/username.response.

Command:

> java -jar target/p1mfaupload-0.0.1-jar-with-dependencies.jar PROVISION-USERS config.properties "migration-1234/input" "migration-1234/output"


### Provision MFA Devices - SMS

* It iterates through all of the records found under the producer mfa-sms directory. In the command below, the Admin API calls are found under migration-1234/input/mfa-sms.
* The results are stored in the consumer folder according to HTTP Status codes.
    * 429 - Too Many Requests. These will remain unprocessed and in the producer folder (migration-1234/input/mfa-sms).
    * 201 - Success. These calls are moved from the producer folder to the consumer folder (migration-1234/output/201/mfa-sms).
         * The response is retained for record with the filename <record>.response. E.g. migration-1234/output/201/mfa-sms/username.response.
    * 400/Other - Fail. These calls are moved from the producer folder to the consumer folder (migration-1234/output/400/mfa-sms).
         * The response is retained for troubleshooting with the filename <record>.response. E.g. migration-1234/output/400/mfa-sms/username.response.
         
Command:

> java -jar target/p1mfaupload-0.0.1-jar-with-dependencies.jar PROVISION-MFA-SMS config.properties "migration-1234/input" "migration-1234/output"


### Provision MFA Devices - Email

* It iterates through all of the records found under the producer mfa-email directory. In the command below, the Admin API calls are found under migration-1234/input/mfa-email.
* The results are stored in the consumer folder according to HTTP Status codes.
    * 429 - Too Many Requests. These will remain unprocessed and in the producer folder (migration-1234/input/mfa-email).
    * 201 - Success. These calls are moved from the producer folder to the consumer folder (migration-1234/output/201/mfa-email).
         * The response is retained for record with the filename <record>.response. E.g. migration-1234/output/201/mfa-email/username.response.
    * 400/Other - Fail. These calls are moved from the producer folder to the consumer folder (migration-1234/output/400/mfa-email).
         * The response is retained for troubleshooting with the filename <record>.response. E.g. migration-1234/output/400/mfa-email/username.response.
         
Command:

> java -jar target/p1mfaupload-0.0.1-jar-with-dependencies.jar PROVISION-MFA-SMS config.properties "migration-1234/input" "migration-1234/output"