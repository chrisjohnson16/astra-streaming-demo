package com.datastax.demo.function;

import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Function;
import org.slf4j.Logger;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.common.schema.KeyValue;

public class FilterStockByTicker implements Function<GenericObject, Stock> {
    private static String APPLE_SYMBOL = "AAPL";
    private static String APPLE_TOPIC = "persistent://as-demo/stocks/stocks-aapl";

    private static String GOOGLE_SYMBOL = "GOOG";
    private static String GOOGLE_TOPIC= "persistent://as-demo/stocks/stocks-goog";

    @Override
    public Stock process(GenericObject input, Context context) {
        Logger LOG = context.getLogger();
        
        KeyValue<GenericRecord, GenericRecord> keyValue = 
        		(KeyValue<GenericRecord, GenericRecord>) input.getNativeObject();

    	Stock stock = convertToPojo(keyValue.getKey(), keyValue.getValue());
    	Producer<Stock> producer = null;
    	
    	try {
            LOG.info("Processing stock stock for: " + stock.getSymbol());
            
            if (stock.getSymbol().equals(APPLE_SYMBOL)) {
                producer = context.getPulsarClient()
            			.newProducer(Schema.JSON(Stock.class))
            			.topic(APPLE_TOPIC)
            			.create();
                producer.send(stock);
            }
            else if (stock.getSymbol().equals(GOOGLE_SYMBOL)) {
                producer = context.getPulsarClient()
                			.newProducer(Schema.JSON(Stock.class))
                			.topic(GOOGLE_TOPIC)
                			.create();
                producer.send(stock);
            }
            
        }
        catch(Exception ex){
            LOG.error("An error occurred parsing CDC message, \"{}\"", ex.getMessage());
        }
    	
    	return stock;
    }
    
    private Stock convertToPojo(GenericRecord key, GenericRecord values) {
 
    	Stock stock = new Stock();
    	stock.setUuid((String)key.getField("uid"));
    	stock.setSymbol((String)values.getField("symbol"));
    	stock.setOpenPrice((Float) values.getField("open_price"));
    	stock.setClosePrice((Float) values.getField("close_price"));
    	stock.setLowPrice((Float) values.getField("low_price"));
    	stock.setHighPrice((Float) values.getField("high_price"));
    	stock.setVolume((Integer) values.getField("volume"));
    	stock.setDate((String) values.getField("trade_date"));

    	return stock;
    }
}