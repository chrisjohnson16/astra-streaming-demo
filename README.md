# Stock Ticker Workshop

This workshop will provide good exposure to the tools and processes needed to create a complete data stream from source to target.  We will use the Pulsar CLI, locally run sources, Astra based topics, functions, and sinks, and finally ElasticSearch and Snowflake.

The stream you will create is a simulation of a stock trade stream.  The process you will follow for this workshop is as follows:
    
* Incoming data will come from a file that will be consumed with a Pulsar source running locally on your laptop.  This will provide experience in using the Pulsar CLI to interact with Astra.  
* We will deploy a function that will enrich the messages, and publish them to an Astra DB sink.
* CDC will detect changes in the table the sink writes to and publish them to a data topic
* We will deploy a function that filters messages and publishes them to multiple topics
* Messages from those topics will be consumed with sinks to ElasticSearch and Snowflake

## Prerequisites
To execute this workshop, ensure that you have the following:
* Java 11 installed
* [Pulsar 2.10.x](https://github.com/datastax/pulsar/releases/download/ls210_2.5/lunastreaming-all-2.10.2.5-bin.tar.gz)
* Astra Account with streaming enabled
* An [ElasticSearch](http://elasticsearch.com) account
* A [Snowflake](http://snowflake.com) account
    * Follow Step 1 on [this blog post](https://medium.com/building-the-open-data-stack/datastax-presents-snowflake-sink-connector-for-apache-pulsar-53629b196064)

### ElasticSearch Sink
If you do not have an ElastSearch account, create a free trial account.  Once done, create a deployment in the uscentral1 GCP region.  <b>Be sure to save the credentials that are provided</b>.  You'll need them later.

Once it is ready, click on your deployment in the menu on the left.  This page will provide you with a number of items you'll need in later steps.
* ElasticSearch url
* Kibana url

### Snowflake Sink
If you don't have a Snowflake account, you can create a free trial account. You'll configure this in a later step.. 

## Setup
In this section, we will walk through setup of the tools needed to execute this workshop.  This includes the Pulsar CLI, our Cassandra table, and accounts with ElasticSearch and Snowflake.

### Pulsar Tenant and Namespace
1. Use the Astra Streaming UI to create the following:
    * A streaming tenant in the uscentral1 GCP region
        * Pick an easy name.  You'll need to reference it in later steps.
    * A namespace in your tenant named `stocks`
    * An unpartitioned topic in the `stocks` namespace named `stocks-in`.  The topic should be persistent.
    * Add a `String` schema to your `stocks-in` topic

2. Once these are created, download a configuration file that will let you use the admin and client CLIs in later steps.  Click on your stream on the left and then the `Connect` tab, and download the `client.conf` file.  

3. Navigate to `<YOUR PULSAR DIR>/conf` on your laptop.  Move the existing `client.conf` file to `client.conf.local` and move the file you just downloaded into this directory with the name `client.conf`.  

### Pulsar CLI
The various Pulsar CLIs are installed as part of the Pulsar distribution and can be found the `<YOUR PULSAR DIR>/bin` directory.  We will be using the following CLIs for this workshop:
* pulsar-admin
* pulsar-client

## Creating the Pulsar Stream

### Pulsar from the Command Line

As part of our setup, you created the first topic in our stream, `stocks-in`.  

1. Before you do anything else, test this topic using the pulsar-client CLI.  If you aren't there, navigate to the `<YOUR PULSAR DIR>/bin` directory in your laptop console.  You can test your connectivity with this command:

    `./pulsar-admin namespaces list <YOUR TENANT>`

2. Next, create a consumer using the following command.  The `-s` option specifies your subscription name and the `-n 0` option tells the client to consume messages continuously.
    
    `./pulsar-client consume -s test -n 0 <YOUR TENANT>/stocks/stocks-in`

Now create a producer with the following command in a separate console:

    `./pulsar-client produce -m hello <YOUR TENANT>/stocks/stocks-in`
     
You should see the message content `key:[null], properties:[], content:hello` as the output in the consumer's console.  At this point you have your first topic created and you have verified that you can connect to Astra Streaming and produce/consume messages.
    
### File Source

Now that you have a topic that you can publish to, create a Pulsar file source connector running locally on your laptop and let it process an input file. You will specify a folder that the connector will use loo for files.  

1. Create a directory `/tmp/stocks`.

2. Download the [file connector](https://www.apache.org/dyn/mirrors/mirrors.cgi?action=download&filename=pulsar/pulsar-2.10.3/connectors/pulsar-io-file-2.10.3.nar) from the Apache Pulsar site.  Place this file in `<YOUR PULSAR DIR>/connectors`.

3. To create the function, you'll need to specify the following found in your `client.conf` file:
    * Token from the `client.conf` file downloaded from Astra.  
    * Broker service URL from the `client.conf` file downloaded from Astra.  
    * The file source configuration found in the GitHub project folder.

4. Execute the following command to run the file connector locally.  The directory locations need to be the fully qualified directory and not the relative path.
```
pulsar-admin sources localrun --broker-service-url <BROKER SERVICE URL> \
    --client-auth-plugin org.apache.pulsar.client.impl.auth.AuthenticationToken \
    --client-auth-params "<TOKEN>" \
    --archive <YOUR PULSAR DIR>/connectors/pulsar-io-file-2.10.3.nar \
    --name stocks-file-source \
    --destination-topic-name <YOUR TENANT>/stocks/stocks-in \
    --source-config-file <YOUR GITHUB PROJECT DIR>/stock-price-file-connector.yaml
```

5. Once the file source has started, start the same consumer from the previous step if it's not still running, then copy `<YOUR GITHUB PROJECT DIR>/stock-prices-10.csv` to `/tmp/stocks` directory.

You should see the log for the file source display processing statements, and you should see new messages output by the consumer.  There will be a message for each line in the file.

### Enrichment Function

Next we will add a function to the stream.  This function will consume messages from the `stocks-in` topic, convert the message to a Java object, add a GUID, and then publish a message as a JSON schema.  You can find the function code in `<YOUR GITHUB PROJECT DIR>src/main/java/com/datastax/demo/function/AddUuidToStockFunction.java` in the GitHub repo you cloned.  

1. Create a topic called `stocks-enriched` in your stocks namespace using the `pulsar-admin` CLI or the Astra Streaming UI.

2. compile the code with the command `./mvnw clean install` from your GitHub project directory.

3. Create a function in Astra Streaming using the the `Functions` tab of your streaming tenant in the UI.  You can find the jar file in `<YOUR GITHUB PROJECT>/target`.  The function should consume the `stocks-in` topic and publish to the `stocks-enriched` topic.  Leave the advanced configuration items set to the defaults.  You can watch the startup of your function by clicking the name and scrolling to the bottom where the logs are displayed.

4. Create a consumer with the `pulsar-client` CLI consuming the `stocks-enriched` topic.

5. Copy your stock file to your temp directory again.  

You should see messages consumed by the Pulsar client we just created.  They should be in JSON format.

### Storing Data in Astra DB

The messages that are created by consuming the stock file and enriched by the first function will be inserted into a table in Astra DB. 

1. In the Astra DB UI, create a database called as-demo in the uscentral1 GCP region with a keyspace named demo.  **Be sure to download your token details.** You'll need them to create the Astra DB sink.

2. Once the database is created, create the following table:
    ```
    create table demo.stocks ( 
        uid uuid primary key,
        symbol text, 
        trade_date text, 
        open_price float, 
        high_price float, 
        low_price float, 
        close_price float, 
        volume int 
    );
    ```
    DO NOT enable CDC on this table yet.

3. To create an Astra DB sink, go to the your Astra Streaming tenant and click on the Sinks tab. Once there click Create and fill in the form with the information based on what we've done to this point. You'll consume the `stocks-enriched` topic and you'll need to use the following mapping. Do not use the default mapping if the field is populated:

```
uid=value.uuid,symbol=value.symbol,trade_date=value.date,open_price=value.openPrice,high_price=value.highPrice,low_price=value.lowPrice,close_price=value.closePrice,volume=value.volume
```

    For the token, you'll use the token value found in the credentials file you just downloaded when creating your Astra DB instance. Use the defaults for everything else.

4. Copy the `<YOUR GITHUB PROJECT DIR>/stock-prices-10.csv` file to the `/tmp/stocks` directory. You should see 10 records inserted into the table once it completes.

### Change Data Capture 

Now that we have a table, let's enable CDC and look at what gets created.

1. Click on the CDC tab in your Astra DB instance.  Click the `Enable CDC` button at the top right and then pick your tenant, keyspace, and enter the table name.  The table should be enabled quickly.  

2. Return to your streaming tenant and click on the `Namespaces and Topics` tab.  You should now see an `astracdc` namespace.  If you open that namespace you should see a data topic and a log topic.

3. Create a consumer from the CLI that consumes the data topic and copy your data file to the temp directory again.  Your consumer should receive 10 messages. 

### Stock Filter Function
The last part of the stream prior to sending data to external systems is to create the filter function in Astra Streaming.  This function will consume data from the CDC data topic and publish a new message to the topic that corresponds to the symbol in the message.

1. Create three topics from the command line using the "pulsar-admin".  Give this a try using the CLI if you want to get experience creating things from that point of view.

    * stocks-aapl
    * stocks-goog
    * stocks-default

    Look at the filter function code in your GitHub project.  This code provides an example of how you can publish messages to multiple topics from one function.  It works by looking at the stock symbol field of the incoming message and filters based on the value.  It will pass all messages that match AAPL to the "stocks-aapl" topic and all messages that match "GOOG" to the "stocks-goog" topic.  All messages will be published to the `stocks-default` topic.

2. Edit `FilterStockByTicker.java` changing the tenant name to your tenant.
3. Compile the class with `./mvnw clean package`
4. Deploy the function to Astra Streaming using the CDC data topic as the input and `stocks-default` as the destionation topic.
5. Create a consumer for each of the output topics, and then copy your data file to your temp directory to verify everything works.

### Send Data to External Targets
#### ElasticSearch Sink
The ElasticSearch sink is a built in connector for Astra Streaming.  Once you have an ElasticSearch account created, you'll need the following values in order to create the sink.

* Elastic URL which you can get from your deployment configuration in the Elastic dashboard
* The username/password from the credentials file you downloaded when creating your deployment

1. With those values available, click on the Sinks tab within your Astra Streaming tenant.  Pick your `stocks` namespace, add a name for your sink, choose Elastic Search as the sink type.  Once the sink type is chosen, the configuation items needed will be displayed below.  Fill in those fields with the following values.

    * Elastic URL
    * Use the `stocks` namespace
    * Use the `stocks-aapl` topic as the input
    * Use the username/password from the credentials file
        * You can skip the token and API key fields
    * Disable `Ignore Record Key`
    * Disable `Strip Nulls`
    * Disable `Enable Schemas`
    * Enable `Copy Key Fields`

    For all other values, you can leave them set to the defaults and click the `Create` button.  Click the sink name on the following page and you can see the configuration and logs for the sink as it's being created.

2. Once the sink is running, copy the stocks data file to `/tmp/stocks`.  If you still have your function and command line consumers running you should see messages flow through the various topics.

3. Now that the data has been moved, go to the home page in your Kibana deployment, and click on `Enterprise Search`.  On the next page, click `Indices` and you should see an index called `appl-index`.  Click it and then the `Documents` tab, and you'll see records that were sent through the AAPL topic by the filter function created in the previous step.

You can follow the same steps to create a sink for the `stocks-goog` topic if you want to try out creating multiple sinks.

#### Snowflake Sink

Snowflake is a very common destination for change data.  The blog post by Andrey Yergorov mentioned in the prerequisites is a great step-by-step guide to setting up Snowflake and connecting it to Pulsar.  As mentioned in the prereqs, Step 1 should be completed in order to move forward.

1. After you've completed step one from the blog post, create an offset topic required by the Snowflake sink.  Create a topic in the `astracdc` namespace called `snowflake-cdc-offset`.  
    
2. Create a Snowflake sink in the UI using the following values:

    <b>Editable Sink-Specific Fields</b>
    ```
    topic: "persistent://<YOUR TENANT>/stocks/stocks-default"
    offsetStorageTopic: "<YOUR TENANT>/astracdc/snowflake-cdc-offset"
    batchSize: "1"
    lingerTimeMs: "10"
    ```

    <b>Kafka Connector Config Properties</b>
    ```
    name: "snowflake-cdc-test"
    connector.class: "com.snowflake.kafka.connector.SnowflakeSinkConnector"
    tasks.max: "1"
    topics: "snowflake-cdc-test"
    buffer.count.records: "1"
    buffer.flush.time: "10"
    buffer.size.bytes: "10"
    snowflake.url.name: "<YOUR SNOWFLAKE SERVER URL>"
    snowflake.user.name: "<SNOWFLAKE PULSAR USER>"
    snowflake.private.key: "<PRIVATE KEY CREATED IN PREREQS>"
    snowflake.database.name: "pulsar_db"
    snowflake.schema.name: "pulsar_schema"
    key.converter: "org.apache.kafka.connect.storage.StringConverter"
    value.converter: "com.snowflake.kafka.connector.records.SnowflakeAvroConverter"
    ```

    If you'd like to try creating the sink using the CLI, update the snowflake-sink.yaml file in your GitHub project with the values above and then execute the following command from `<YOUR PULSAR DIR>/bin` directory:

    ```
    pulsar-admin sinks create -t snowflake --name snowflake-sink --sink-config-file <YOUR GITHUB DIR>/snowflake-sink.yaml --tenant <YOUR TENANT> -i persistent://<YOUR TENANT>/astracdc/<YOUR DATA TOPIC>
    ```

3. Before you execute the stream, log out of Snowflake, and then log back in using Pulsar username and password created while setting up Snowflake.

4. Click on `Databases` on the left side, then open `PULSAR_SCHEMA` and then `TABLES`.  You shouldn't see any tables at this point.

5. Truncate your table in Astra DB.  Truncates are not picked up by CDC, so you won't see anything move through the various downstream topics.

6. Ensure your local file connector is still running, then copy your stocks test file to the`/tmp/stocks` directory.  If you still have your various consumers running, you should see messages displayed.  If you look at the logs for the various functions and sinks you've created, the logs should display output.  

7. Refresh your Snowflake table list using the menu just above PULSAR_DB on the worksheets page.  You should now see a table displayed.  Click on the table name, and then the `Data Preview` tab.  You should see a record for each line in the file.
