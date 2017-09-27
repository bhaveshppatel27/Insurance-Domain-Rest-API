package com.plans;

import org.springframework.web.bind.annotation.RestController;

import com.encryption.GenerateToken;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import com.validation.ValidateJSON;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@RestController
public class PlansController {

	public static final String AUTHENTICATION_HEADER = "Authorization";
	public static final String REF = "ref_plan";
	public static final String PARENT = "_parent";
	public static final String WORK_QUEUE = "WORK_QUEUE";
	public static final String WORK_QUEUE1 = "WORK_QUEUE1";
	Map<String, String> mapOfJson = null;
	Set<String> setOfKeys = new HashSet<String>();

	Calendar cal = Calendar.getInstance();
	DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	
	@RequestMapping(value = "/plan/{planId}", method = RequestMethod.GET, headers = "Accept=application/json")
	public Object getPlan(@PathVariable String planId,HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "GET");
		JSONObject acc = new JSONObject();
		if(access.contains(",")){
			
			acc.put("error", "You are not authorized to make a GET Request");
			response.setStatus(401);
			return acc;
		}else if(access.contains("false")){
			acc.put("error", "Invalid Token");
			response.setStatus(403);
			return acc;
		}
		
		Jedis jedis = RedisConnection.getConnection();
		
		if(!jedis.exists(planId)){
			
			JSONObject errorMessage = new JSONObject();
			errorMessage.put("error", "Plan does not exist");
			return errorMessage;
		}
		
		String eTag = jedis.get(planId+"_eTag");
		String header = request.getHeader("If-None-Match");
		if (eTag.equals(header)) {
			response.setStatus(304);
			return new JSONObject();
		} else {
			response.setHeader("eTag", eTag);
		}
		
		JSONObject rootJson = new JSONObject();
		
		JSONObject plan = (JSONObject) recreateJSON(planId, rootJson, jedis,planId);
		response.setHeader("eTag", eTag);
		try{
			String jsonPath = request.getParameter("path");
			if(jsonPath != null){
				System.out.println(jsonPath);
				Object queriedJson = JsonPath.read(plan, jsonPath);
				return queriedJson;
			}else{
				return plan;
			}
		}catch(Exception e){
			return new JSONObject();
		}
		
		

	}
	
	@RequestMapping(value = "/{type}/{id}", method = RequestMethod.GET, headers = "Accept=application/json")
	public JSONObject partialGetObject(@PathVariable String type,@PathVariable String id,HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "GET");
		JSONObject acc = new JSONObject();
		if(access.contains(",")){
			
			acc.put("error", "You are not authorized to make a GET Request");
			response.setStatus(401);
			return acc;
		}else if(access.contains("false")){
			acc.put("error", "Invalid Token");
			response.setStatus(403);
			return acc;
		}
		
		Jedis jedis = RedisConnection.getConnection();
		
		String root_planId = "";
		String partialPlanId = REF + "_"+type+"_"+id;
		
		if(jedis.type(partialPlanId).equalsIgnoreCase("none")){
			
			JSONObject errorMessage = new JSONObject();
			errorMessage.put("error", "Object does not exist");
			return errorMessage;
		}
		
		JSONObject rootJson = new JSONObject();
				
		JSONObject plan = (JSONObject) recreateJSON(partialPlanId, rootJson, jedis,root_planId);
		//response.setHeader("eTag", jedis.get(planId+"_eTag"));
		return plan;

	}
	
	@RequestMapping(value = "/{resource}/{planId}/{key}", method = RequestMethod.GET, headers = "Accept=application/json")
	public JSONObject partialGetArray(@PathVariable String resource, @PathVariable String planId,@PathVariable String key,HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "GET");
		JSONObject acc = new JSONObject();
		if(access.contains(",")){
			
			acc.put("error", "You are not authorized to make a GET Request");
			response.setStatus(401);
			return acc;
		}else if(access.contains("false")){
			acc.put("error", "Invalid Token");
			response.setStatus(403);
			return acc;
		}
		
		Jedis jedis = RedisConnection.getConnection();
		
		String root_planId = planId;
		String partialPlanId = planId + "_"+key;
		
		if(jedis.type(partialPlanId).equalsIgnoreCase("none")){
			
			JSONObject errorMessage = new JSONObject();
			errorMessage.put("error", "Array does not exist");
			return errorMessage;
		}
		
		JSONObject rootJson = new JSONObject();
		
		JSONArray arr = (JSONArray) recreateJSON(partialPlanId, new JSONArray(), jedis,root_planId);
		rootJson.put(key, arr);
		return rootJson;

	}

	@RequestMapping(value = "/{resource}", method = RequestMethod.POST)
	@ResponseBody
	public String postPlan(@RequestBody JSONObject postedPlan, @PathVariable String resource,HttpServletRequest request,
			HttpServletResponse response)
			throws Exception {
		
		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "POST");

		if(access.contains(",")){
			response.setStatus(401);
			return "You are not authorized to make a POST Request";
		}else if(access.contains("false")){
			response.setStatus(403);
			return "Invalid Token";
		}
		
		
		Boolean isJSONValid = ValidateJSON.isJSONValid(postedPlan.toJSONString());
		if (isJSONValid == false) {
			return "JSON payload invalidated against schema";
		} else {
			Jedis jedis = RedisConnection.getConnection();

			Map<String, Object> mapOfJSON = new Gson().fromJson(postedPlan.toJSONString(),
					new TypeToken<Map<String, Object>>() {
					}.getType());

			//String planId = parseJson(mapOfJSON, jedis);
			String plan_uuid = "plan_" + UUID.randomUUID().toString();
			mapOfJson = new HashMap();
			Date date = new Date();
			mapOfJson.put("createdAt", dateFormat.format(date));
			mapOfJson.put("updatedAt", dateFormat.format(date));
			String planId = parseJson(mapOfJSON, jedis,plan_uuid,mapOfJson,plan_uuid);

			fillQueue(planId,jedis);
			
			String eTag = generateEtag(dateFormat.format(date));
			jedis.set(planId+"_eTag", eTag);
			response.setHeader("eTag", eTag);
			return "planId == > " + planId + " \neTag == > " + eTag;

		}

	}

	@RequestMapping(value = "/plan/{planId}", method = RequestMethod.DELETE)
	@ResponseBody
	public String deletePlan(@RequestBody JSONObject postedPlan, @PathVariable String planId,HttpServletRequest request,HttpServletResponse response) throws Exception {
		
		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "DELETE");

		if(access.contains(",")){
			response.setStatus(401);
			return "You are not authorized to make a DELETE Request";
		}else if(access.contains("false")){
			response.setStatus(403);
			return "Invalid Token";
		}
		
		Jedis jedis = RedisConnection.getConnection();
				
		if(!jedis.exists(planId)){
			return "Invalid delete request. Key does not exist in Redis";
		}
		
		collectAllKeysToDelete(planId, jedis,planId);
		deletePlanKeys(jedis, planId);
				
		return "Plan ID ==> " + planId + " Deleted Successfully";

	}
	
	@RequestMapping(value = "/plan/{planId}/{type}/{id}", method = RequestMethod.DELETE)
	@ResponseBody
	public String deletePartialPlan(@RequestBody JSONObject postedPlan, @PathVariable String planId,@PathVariable String type,@PathVariable String id) throws NoSuchAlgorithmException {
		Jedis jedis = RedisConnection.getConnection();
		
		String  customId = planId+"_"+type+"_"+id; 
		if(!jedis.exists(customId) && !type.equalsIgnoreCase("plan")){
			return "Invalid delete request. Key does not exist in Redis";
		}
		if(type.equalsIgnoreCase("plan")){
			customId = planId;
			
			collectAllKeysToDelete(customId, jedis,planId);
			deletePlanKeys(jedis, planId);
					
			return "Plan ID ==> " + planId + " Deleted Successfully";
		}
		
		Date date =new Date();
		Map<String, String> retrieveMap = jedis.hgetAll(planId);
		retrieveMap.put("updatedAt", dateFormat.format(date));
		jedis.hmset(planId, retrieveMap);
	
		String eTag = generateEtag(dateFormat.format(date));
		
		
		collectAllKeysToDelete(customId, jedis,planId);
		deletePlanKeys(jedis, planId);
		jedis.set(planId+"_eTag", eTag);	
		return "Plan ID ==> " + planId + " with type "+type+" and id " +id+" Deleted Successfully";

	}

	@RequestMapping(value = "/plan/{plan_uuid}", method = RequestMethod.PUT)
	@ResponseBody
	public String putPlan(@RequestBody JSONObject postedPlan, @PathVariable String plan_uuid,HttpServletRequest request,HttpServletResponse response)
			throws Exception {

		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "PUT");

		if(access.contains(",")){
			response.setStatus(401);
			return "You are not authorized to make a PUT Request";
		}else if(access.contains("false")){
			response.setStatus(403);
			return "Invalid Token";
		}
		
		Jedis jedis = RedisConnection.getConnection();
		Boolean x = jedis.exists(plan_uuid);
		if(!jedis.exists(plan_uuid)){
			return "Plan does not Exist";
		}
		
		Boolean isJSONValid = ValidateJSON.isJSONValid(postedPlan.toJSONString());
		if (isJSONValid == false) {
			return "JSON payload invalidated against schema";
		} else {
			
			String eTag = jedis.get(plan_uuid+"_eTag");
			String header = request.getHeader("If-Match");
			if (header == null) {
				response.setStatus(403);
				return "Include If-Match in the Header";
			}
			else if (!eTag.equals(header)) {
				response.setStatus(412);
				return "Plan Changed since last update";
			}
			
			
			//get the created date
			Map<String, String> retrieveMap = jedis.hgetAll(plan_uuid);
			//delete original data in this plan
			collectAllKeysToDelete(plan_uuid, jedis,plan_uuid);
			
			deletePlanKeys(jedis, plan_uuid);
			
			
			
			Map<String, Object> mapOfJSON = new Gson().fromJson(postedPlan.toJSONString(),
					new TypeToken<Map<String, Object>>() {
					}.getType());

			mapOfJson = new HashMap();
			Date date = new Date();
			
			retrieveMap.get("createdAt");
			mapOfJson.put("createdAt", retrieveMap.get("createdAt"));
			mapOfJson.put("updatedAt", dateFormat.format(date));
			
			String planId = parseJson(mapOfJSON, jedis,plan_uuid,mapOfJson,plan_uuid);

			String eTagNew = generateEtag(dateFormat.format(date));
			jedis.set(planId+"_eTag", eTagNew);
			response.setHeader("eTag", eTagNew);
			return "Put Request Successful "+" \nplanId == > " + planId + " \neTag == > " + eTagNew;

		}
	}
	
	@RequestMapping(value = "/{type}/{id}", method = RequestMethod.PATCH)
	@ResponseBody
	public String patch(@RequestBody JSONObject postedPlan,@PathVariable String type,@PathVariable String id,HttpServletRequest request,
			HttpServletResponse response)
			throws Exception {
		
		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "PATCH");

		if(access.contains(",")){
			response.setStatus(401);
			return "You are not authorized to make a PATCH Request";
		}else if(access.contains("false")){
			response.setStatus(403);
			return "Invalid Token";
		}
		
		
		Boolean isJSONValid = ValidateJSON.isPartialJSONObjectValid(postedPlan.toJSONString(),type);
		if (isJSONValid == false) {
			return "JSON payload invalidated against schema";
		} else {
		
			Jedis jedis = RedisConnection.getConnection();
			String pathchId = REF+"_"+type+"_"+id;
			String planId = "PATCH";
			//Proceed only if there is a key in redis to make a patch request
			if(!jedis.exists(pathchId)){
				return "Invalid patch request. Key does not exist in Redis";
			}
			Map<String, Object> mapOfJSON = new Gson().fromJson(postedPlan.toJSONString(),
					new TypeToken<Map<String, Object>>() {
					}.getType());
			
			String planId_patch = parseJson(mapOfJSON, jedis,pathchId,new HashMap(),planId);
			
			Date date =new Date();
			String eTag = generateEtag(dateFormat.format(date));
			Set<String> set = jedis.smembers(pathchId+PARENT);
			List<String> list = new ArrayList();
			list.addAll(set);
			int size = list.size();
			for(int i = 0; i< size; i++) { 
				String rootId = list.get(i);
				Map<String, String> retrieveMap = jedis.hgetAll(rootId);
				retrieveMap.put("updatedAt", dateFormat.format(date));
				jedis.hmset(rootId, retrieveMap);
				jedis.set(rootId+"_eTag", eTag);
		      }
			
			fillQueue(planId_patch+"_parent", jedis);
			response.setHeader("eTag", eTag);
			return "Patch Request Successful\neTag == > " + eTag;
		}	
	}
	
	
	@RequestMapping(value = "/plan/{planId}/{type}", method = RequestMethod.PATCH)
	@ResponseBody
	public String patchArray(@RequestBody String postedPlan1, @PathVariable String planId,@PathVariable String type,HttpServletRequest request,
			HttpServletResponse response)
			throws Exception {
		
		String authCode[] = request.getHeader(AUTHENTICATION_HEADER).split(" ");
		String access = validateToken(authCode[1], "PATCH");

		if(access.contains(",")){
			response.setStatus(401);
			return "You are not authorized to make a PATCH Request";
		}else if(access.contains("false")){
			response.setStatus(403);
			return "Invalid Token";
		}
		
		JSONParser parser = new JSONParser();
		Object jsonObj = parser.parse(postedPlan1);
		JSONObject postedPlan = (JSONObject) jsonObj;
		
		Boolean isJSONValid = ValidateJSON.isPartialJSONArrayValid(postedPlan,type);
		if (isJSONValid == false) {
			return "JSON payload invalidated against schema";
		} else {
		
			Jedis jedis = RedisConnection.getConnection();
			String pathchId = planId+"_"+type;
			
			//Proceed only if there is a key in redis to make a patch request
			if(!jedis.exists(pathchId)){
				return "Invalid patch request. Key does not exist in Redis";
			}
			Map<String, Object> mapOfJSON = new Gson().fromJson(postedPlan.toJSONString(),
					new TypeToken<Map<String, Object>>() {
					}.getType());
			
			String planId_patch = parseJsonArray(mapOfJSON, jedis,pathchId,new HashMap(),planId);

			Date date =new Date();
			Map<String, String> retrieveMap = jedis.hgetAll(planId);
			retrieveMap.put("updatedAt", dateFormat.format(date));
			jedis.hmset(planId, retrieveMap);
			
			
			String eTag = generateEtag(dateFormat.format(date));
			jedis.set(planId+"_eTag", eTag);
			response.setHeader("eTag", eTag);

			return "Patch Request Successful \n"+"planId == > " + planId + " \neTag == > " + eTag;
		}	
	}
		
		
	public String parseJson(Map postedPlan, Jedis jedis,String planId,Map hm,String planId_root) {

		//String planId = "plan_" + UUID.randomUUID().toString();
		
		Set keys = (Set) postedPlan.keySet();
		Iterator itr = keys.iterator();
		while (itr.hasNext()) {
			String key = (String) itr.next();
			if (postedPlan.get(key) instanceof Map ) {
				String uuid = REF+buildKey((Map)postedPlan.get(key),jedis,planId_root,planId);
				if(planId_root.equalsIgnoreCase("PATCH")){
					jedis.lpush(WORK_QUEUE, uuid+"_parent");
				}else{
					jedis.lpush(WORK_QUEUE, uuid);
				}
				
				hm.put(key, uuid);
				Map hm1 = new HashMap();
				parseJson((Map)postedPlan.get(key),jedis,uuid,hm1,planId_root);
				//jedis.set(planId + "_Map_2_" + key, postedPlan.get(key).toString());
			} else if (postedPlan.get(key) instanceof ArrayList) {

//				String uuid = planId_root+"_"+cal.getTimeInMillis();
				String uuid = planId_root+"_"+key;
				hm.put(key, uuid);
				Map hm_array = new HashMap();
				for (Object value : (ArrayList) postedPlan.get(key)) {
					if(value instanceof Map){
						
						String uuidInArray = REF+buildKey((Map) value,jedis,planId_root,planId);
						if(planId_root.equalsIgnoreCase("PATCH")){
							jedis.lpush(WORK_QUEUE, uuidInArray+"_parent");
						}else{
							jedis.lpush(WORK_QUEUE, uuidInArray);
						}
						jedis.sadd(uuid, uuidInArray);
						Map hm1 = new HashMap();
						parseJson((Map) value,jedis,uuidInArray,hm1,planId_root);
						
					}else{
						jedis.sadd(uuid, value.toString());
					}
					
					
				}

			} else {

				hm.put(key, postedPlan.get(key).toString());

			}
		}

		jedis.hmset(planId, hm);
	
		return planId;
	}
	
	
	public String parseJsonArray(Map postedPlan, Jedis jedis,String planId,Map hm,String planId_root) {

		
		Set keys = (Set) postedPlan.keySet();
		Iterator itr = keys.iterator();
		while (itr.hasNext()) {
			String key = (String) itr.next();
			if (postedPlan.get(key) instanceof ArrayList) {

//				String uuid = planId_root+"_"+cal.getTimeInMillis();
				String uuid = planId;

				for (Object value : (ArrayList) postedPlan.get(key)) {
					if(value instanceof Map){
						
						String uuidInArray = REF+buildKey((Map) value,jedis,planId_root,planId);
						jedis.sadd(uuid, uuidInArray);
						Map hm1 = new HashMap();
						parseJson((Map) value,jedis,uuidInArray,hm1,planId_root);
						
					}else{
						jedis.sadd(uuid, value.toString());
					}
					
					
				}

			}
		}

		//jedis.hmset(planId, hm);
		
		
		return planId;
	}
	
		
	public String validateToken(String token, String method) throws Exception{
		boolean flag = true;
		JSONArray roles = null;
		try{
		String json =  GenerateToken.decrypt(token);

		JSONParser parser = new JSONParser();
		Object obj = parser.parse(json);
		JSONObject jsonObject = (JSONObject) obj;
		roles = (JSONArray) jsonObject.get("roles");
		}catch(Exception e){
			flag = false;
			return "false";
		}
		if(flag == true){
			if(roles != null && roles.contains(method)){
				return "true";
			}else{
				return "false,denied";
			}
		}else{
			return "false";
		}

		
	}
	
	public String buildKey(Map json,Jedis jedis,String planID_root,String patchId){

		Set keys = (Set) json.keySet();
		Iterator itr = keys.iterator();
		String type = null;
		String id = null;
		while (itr.hasNext()) {
			String key = (String) itr.next();
			if(key.equals("_type")){
				type = String.valueOf(json.get("_type"));
			}else if(key.equals("_id")){
				
				//id =(int) ((Double)json.get("_id")).doubleValue();
				
				id = String.valueOf(json.get("_id"));
			}
		}
	
		String time = String.valueOf((System.currentTimeMillis()));
		if(type==null && id == null){
			type = time;
			id = time;
		}else if(type == null && id != null){
			type = time;
		}else if(type != null && id == null){
			id = time;
		}
		if(planID_root != "" && !planID_root.equalsIgnoreCase("PATCH")){
			jedis.sadd(REF+"_"+type+"_"+id+PARENT,planID_root);
		}
		else if(planID_root.equalsIgnoreCase("PATCH")){
			Set<String> set = jedis.smembers(patchId+PARENT);
			List<String> list = new ArrayList();
			list.addAll(set);
			int size = list.size();
			for(int i = 0; i< size; i++) { 
				jedis.sadd(REF+"_"+type+"_"+id+PARENT,list.get(i));

		      }
		}
		
		
		return "_"+type+"_"+id;
	}
	
	public void collectAllKeysToDelete(String id, Jedis jedis,String planId_root){
	
		if(jedis.type(id).equalsIgnoreCase("hash")){
			Map<String, String> retrieveMap = jedis.hgetAll(id);
			setOfKeys.add(id);
			for (String keyMap : retrieveMap.keySet()) {
				String value = retrieveMap.get(keyMap);
				if(value.contains(planId_root) || value.contains(REF)){
					collectAllKeysToDelete(value, jedis,planId_root);
				}
			}
			
		}else if(jedis.type(id).equalsIgnoreCase("set")){
			setOfKeys.add(id);
			Set<String> set = jedis.smembers(id);
			List<String> list = new ArrayList();
			list.addAll(set);
			int size = list.size();
			for(int i = 0; i< size; i++) { 
				collectAllKeysToDelete(list.get(i),jedis,planId_root);
		      }
			
		}
       
	}
	
	public String generateEtag(String valueTohash) throws NoSuchAlgorithmException{
		
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(valueTohash.getBytes());

        byte byteData[] = md.digest();

        //convert the byte to hex format method 1
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < byteData.length; i++) {
         sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        
        return sb.toString();
		
	}
	
	public Object recreateJSON(String id, Object object,Jedis jedis, String planId_root){
		
		
		if(jedis.type(id).equalsIgnoreCase("hash")){
			Map<String, String> retrieveMap = jedis.hgetAll(id);
			for (String keyMap : retrieveMap.keySet()) {
				String value = retrieveMap.get(keyMap);
				if((value.contains(planId_root) ||value.contains(REF)) && jedis.type(value).equals("hash")){
					JSONObject newObject = new JSONObject();
					JSONObject fillObject = (JSONObject) object;
					fillObject.put(keyMap,newObject);
					recreateJSON(value,newObject,jedis,planId_root);
				}else if((value.contains(planId_root) ||value.contains(REF)) && jedis.type(value).equals("set")){
					JSONArray newArray = new JSONArray();
					JSONObject fillObject = (JSONObject) object;
					fillObject.put(keyMap,newArray);
					recreateJSON(value,newArray,jedis,planId_root);
				}
				else{
					JSONObject fillObject = (JSONObject) object;
					fillObject.put(keyMap, value);
				}
			}
			
		}else if(jedis.type(id).equalsIgnoreCase("set")){
			
			Set<String> set = jedis.smembers(id);
			List<String> list = new ArrayList();;
			list.addAll(set);
			int size = list.size();
			JSONArray fillObject = (JSONArray) object;
			for(int i=0;i<size;i++){
				String value = list.get(i);
				if((value.contains(planId_root) ||value.contains(REF)) && jedis.type(value).equals("hash")){
					JSONObject newObject = new JSONObject();
					fillObject.add(newObject);
					recreateJSON(value,newObject,jedis,planId_root);
				}else if((value.contains(planId_root) ||value.contains(REF)) && jedis.type(value).equals("set")){
					JSONArray newArray = new JSONArray();
					fillObject.add(newArray);
					recreateJSON(value,newArray,jedis,planId_root);
				}else{
					fillObject.add(value);
				}
				
			}
			
		}
		
		return object;
	}
	
	public String getModifiedDate(String planId, Jedis jedis){
		Map<String, String> retrieveMap = jedis.hgetAll(planId);
		return retrieveMap.get("updatedAt");
	}
	
	public void deletePlanKeys(Jedis jedis, String planId){
		Iterator<String> it = setOfKeys.iterator();
	     while(it.hasNext()){
	    	 String key = it.next();
	    	 if(key.contains("ref_plan")){
	    		 Set<String> set = jedis.smembers(key+PARENT);
	 			 if(set.size() > 1){
	 				jedis.srem(key+PARENT, planId);
	 			 }else{
	 				jedis.del(key);
	 				jedis.del(key+PARENT);
	 				
	 			 }
	    	 }else{
	    		 jedis.del(key);
	    	 }
	    	 
	     }
	     jedis.del(planId+"_eTag");
	     setOfKeys.clear();
	}
	
	public void fillQueue(String planId,Jedis jedis){
		
		jedis.lpush(WORK_QUEUE1, planId);
		List<String> list = jedis.lrange(WORK_QUEUE, 0 ,100); 
	      int size = list.size();
	      for(int i = (size-1); i>=0; i--) { 
	         jedis.lpush(WORK_QUEUE1, list.get(i)); 
	      }
	      
	      jedis.del(WORK_QUEUE);
	}
}
