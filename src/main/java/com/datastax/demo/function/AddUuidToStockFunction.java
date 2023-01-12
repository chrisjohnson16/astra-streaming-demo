package com.datastax.demo.function;

import java.util.UUID;
import java.util.function.Function;

/**
 * The classic Exclamation Function that appends an exclamation at the end
 * of the input.
 */
public class AddUuidToStockFunction implements Function<String, Stock> {

	@Override
	public Stock apply(String input) {
        String[] values = input.split(",");

        Stock stock = new Stock();
        stock.setSymbol(values[0]);
        stock.setDate(values[1]);
        stock.setOpenPrice(Float.valueOf(values[2]));
        stock.setHighPrice(Float.valueOf(values[3]));
        stock.setLowPrice(Float.valueOf(values[4]));
        stock.setClosePrice(Float.valueOf(values[5]));
        stock.setVolume(Integer.valueOf(values[6]));
        
		stock.setUuid(UUID.randomUUID().toString());

		System.out.println("Received stock record for " + stock.getSymbol() + 
				           " and adding UUID " + stock.getUuid());

		return stock;
	}
	
}