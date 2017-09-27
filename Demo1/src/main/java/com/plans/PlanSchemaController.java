package com.plans;

import org.springframework.web.bind.annotation.RestController;

import redis.clients.jedis.Jedis;

import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@RestController
public class PlanSchemaController {

	@RequestMapping(value = "/planschema", method = RequestMethod.GET, headers = "Accept=application/json")
	public JSONObject getPlanSchema() {

		Jedis jedis = RedisConnection.getConnection();
		// set the data in redis string
		String planSchema = jedis.get("planschema");
		// Get the stored data and print it
		JSONParser parser = new JSONParser();
		JSONObject obj = null;
		try {
			obj = (JSONObject) parser.parse(planSchema);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return obj;
	}
	
	@RequestMapping(value = "/planschema/{type}", method = RequestMethod.GET, headers = "Accept=application/json")
	public JSONObject getPlanSchema(@PathVariable String type) {

		Jedis jedis = RedisConnection.getConnection();
		// set the data in redis string
		String planSchema = jedis.get("planschema_"+type);
		// Get the stored data and print it
		JSONParser parser = new JSONParser();
		JSONObject obj = null;
		try {
			obj = (JSONObject) parser.parse(planSchema);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return obj;
	}

	@RequestMapping(value = "/planschema", method = RequestMethod.POST)
	@ResponseBody
	public String postPlanSchema(@RequestBody String postedPlan) throws ParseException {
		Jedis jedis = RedisConnection.getConnection();
		jedis.set("planschema", postedPlan);

		JSONParser parser = new JSONParser();
		Object jsonObj = parser.parse(postedPlan);
		JSONObject jsonObject = (JSONObject) jsonObj;
		JSONObject mainProperties = (JSONObject) jsonObject.get("properties");
		PlansApplication.postSchema(mainProperties,jedis);
		
		return "Plan Schema Posted Successfully";
	}

	@RequestMapping(value = "/planschema", method = RequestMethod.PUT)
	@ResponseBody
	public String putPlanSchema(@RequestBody String postedPlan) throws ParseException {
		Jedis jedis = RedisConnection.getConnection();
		jedis.set("planschema", postedPlan);

		JSONParser parser = new JSONParser();
		Object jsonObj = parser.parse(postedPlan);
		JSONObject jsonObject = (JSONObject) jsonObj;
		JSONObject mainProperties = (JSONObject) jsonObject.get("properties");
		PlansApplication.postSchema(mainProperties,jedis);
		
		return "Plan Schema Updated Successfully";
	}
	
	@RequestMapping(value = "/planschema/{type}", method = RequestMethod.PUT)
	@ResponseBody
	public String putPlanSchema(@RequestBody JSONObject postedPlan,@PathVariable String type) throws ParseException {

		Jedis jedis = RedisConnection.getConnection();
		if(jedis.exists("planschema_"+type)){
		jedis.set("planschema_"+type, postedPlan.toJSONString());
		return "Plan Schema Updated Successfully";
		}else{
			return "Invalid key";
		}
	}

	@RequestMapping(value = "/planschema", method = RequestMethod.DELETE)
	@ResponseBody
	public String deletePlan() {
		Jedis jedis = RedisConnection.getConnection();
		Set<String> keys = jedis.keys("planschema*");
		for (String key : keys) {
		    jedis.del(key);
		}
		//jedis.del("planschema");
		return "Plan schema Deleted Successfully";
	}

}
