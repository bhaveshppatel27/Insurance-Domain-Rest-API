package com.validation;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.plans.RedisConnection;

import redis.clients.jedis.Jedis;

public class ValidateJSON {

	public static Boolean isJSONValid(String jsonFile) throws ProcessingException, IOException {

		Jedis jedis = RedisConnection.getConnection();

		String planSchema = jedis.get("planschema");

		if (ValidationUtils.isJsonValid(planSchema, jsonFile)) {
			return true;
		} else {
			return false;
		}
	}
	
	public static Boolean isPartialJSONObjectValid(String jsonFile, String type) throws ProcessingException, IOException {

		Jedis jedis = RedisConnection.getConnection();
		String key = "planschema_"+type;
		
		//if key does not exist, it was newly added so pass the request as it was not in the schema 
		if(!jedis.exists(key)){
			return true;
		}

		String planSchema = jedis.get(key);

		if (ValidationUtils.isJsonValid(planSchema, jsonFile)) {
			return true;
		} else {
			return false;
		}
	}
	
	public static Boolean isPartialJSONArrayValid(JSONObject plan,String type) throws ProcessingException, IOException {

		Jedis jedis = RedisConnection.getConnection();
		String key = "planschema_"+type;
		
		//if key does not exist, it was newly added so pass the request as it was not in the schema 
		if(!jedis.exists(key)){
			return true;
		}
		// get the array from the json
		Set keys = plan.keySet();
		Iterator itr = keys.iterator();
			String k = (String) itr.next();
			JSONArray arr = (JSONArray) plan.get(k);
		
		String planSchema = jedis.get(key);

		if (ValidationUtils.isJsonValid(planSchema, arr.toJSONString())) {
			return true;
		} else {
			return false;
		}
	}

}
