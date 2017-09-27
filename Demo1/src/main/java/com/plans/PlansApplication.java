package com.plans;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.hamcrest.CoreMatchers.instanceOf;

import java.util.Iterator;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.encryption.GenerateToken;

import ch.qos.logback.core.net.SyslogOutputStream;
import redis.clients.jedis.Jedis;

@SpringBootApplication
public class PlansApplication {
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws ParseException {
		SpringApplication.run(PlansApplication.class, args);

		
		
		FileReader obj = new FileReader();
		String planSchema = obj.getFileWithUtil("planschema/planSchemaNew.json");
		
		JSONParser parser = new JSONParser();
		Object jsonObj = parser.parse(planSchema);
		JSONObject jsonObject = (JSONObject) jsonObj;
		
		Jedis jedis = RedisConnection.getConnection();
		jedis.set("planschema", planSchema);
		JSONObject mainProperties = (JSONObject)jsonObject.get("properties");
		postSchema(mainProperties,jedis);
		
		String dataToEncrypt = obj.getFileWithUtil("planschema/dataToEncrypt.json");
		String dataToEncrypt1 = obj.getFileWithUtil("planschema/dataToEncrypt1.json");
		String s = null;
		try {
			s = GenerateToken.encrypt(dataToEncrypt);
			GenerateToken.encrypt(dataToEncrypt1);
			GenerateToken.decrypt(s);
		} catch (Exception e) {
			System.out.println("Encryption Error");
		}
	
	}
	
	public static void postSchema(JSONObject mainProperties, Jedis jedis){
		Set keys = mainProperties.keySet();
		Iterator itr = keys.iterator();
		
		while(itr.hasNext()){
			String key = (String) itr.next();
			JSONObject objprop = (JSONObject) mainProperties.get(key);
			if(objprop.get("type").toString().equalsIgnoreCase("object")){
				
				String type = ((JSONObject)((JSONObject)objprop.get("properties")).get("_type")).get("description").toString();
				jedis.set("planschema_"+type, mainProperties.get(key).toString());
				
				JSONObject prop = (JSONObject) objprop.get("properties");
				Set keys1 = prop.keySet();
				Iterator itr1 = keys1.iterator();
				
				while(itr1.hasNext()){
					String key1 = (String) itr1.next();
					JSONObject objprop1 = (JSONObject) prop.get(key1);
					if(objprop1.get("type").toString().equalsIgnoreCase("object") || objprop1.get("type").toString().equalsIgnoreCase("array")){
						postSchema(prop, jedis);
					}
				}
				
			}else if (objprop.get("type").toString().equalsIgnoreCase("array")){
				jedis.set("planschema_"+key, mainProperties.get(key).toString());
				JSONObject o = (JSONObject) mainProperties.get(key);
				JSONObject items = (JSONObject) o.get("items");
				JSONObject properties = (JSONObject) items.get("properties");
				String type = ((JSONObject)properties.get("_type")).get("description").toString();
				jedis.set("planschema_"+type,items.toString());

				Set keys2 = properties.keySet();
				Iterator itr2 = keys2.iterator();
				
				while(itr2.hasNext()){
					String key2 = (String) itr2.next();
					JSONObject objprop1 = (JSONObject) properties.get(key2);
					if(objprop1.get("type").toString().equalsIgnoreCase("object") || objprop1.get("type").toString().equalsIgnoreCase("array")){
						postSchema(properties, jedis);
					}
				}
				
			}
		}
	}
}
