package com.dequeue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import redis.clients.jedis.Jedis;

@SpringBootApplication
public class DequeueApplication {

	public static final String WORK_QUEUE = "WORK_QUEUE1";
	public static final String BACKUP_QUEUE = "BACKUP_QUEUE1";
	public static final String REF = "ref_plan";
	public static IndexResponse membercostshareResponseIndex = null;
	
	public static void main(String[] args) throws ParseException, InterruptedException, IOException {
		SpringApplication.run(DequeueApplication.class, args);

		Settings settings = Settings.builder().put("cluster.name", "elasticsearch_bigdata").build();
		TransportClient client = new PreBuiltTransportClient(settings)
		        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("127.0.0.1"), 9300));
		CreateIndexRequestBuilder request = null;
                
		boolean exists = client.admin().indices()
			    .prepareExists("plans")
			    .execute().actionGet().isExists();
		if(!exists){
			request = client.admin().indices().prepareCreate("plans");
			DequeueApplication.putParentMapping("membercostshare", "x",client,request);
		}
		
		
		while(true){
			checkQueue(client);
			Thread.sleep(1500);
		}
			

	}
	
	public static void checkQueue(TransportClient client) throws org.json.simple.parser.ParseException, IOException {
		String planDequeued = null;

		Jedis jedis = new Jedis("localhost");
				
		try
		{
		
			planDequeued = jedis.brpoplpush(WORK_QUEUE, BACKUP_QUEUE, 0);			
		} catch (Exception e) {
			System.out.println("Exception is " + e);

		} finally {
			//jedis.close();
		}
		if (planDequeued != null) {
			JSONParser parser = new JSONParser();
			JSONObject rootJson = new JSONObject();
			
			

			
			String [] parts= planDequeued.split("_");
			try{
			if(parts[4]!=null || parts[4]!=""){
				planDequeued = parts[0]+"_"+parts[1]+"_"+parts[2]+"_"+parts[3];
			}}catch(Exception e){
				System.out.println("Here");
			}
			
				JSONObject newJObject = (JSONObject) recreateJSON(planDequeued, rootJson, jedis,planDequeued);

		    System.out.println(planDequeued);
			String object_type = null;
			String object_ID = null;
			
			if(parts.length == 2){
				object_type = parts [0];
				object_ID= parts [1];
			}else if(parts.length == 5){
				object_type = parts [2];
				object_ID= parts [3];
				Set<String> set = jedis.smembers(planDequeued+"_parent");
				List<String> list = new ArrayList();
				list.addAll(set);
				int size = list.size();
				for(int i = 0; i< size; i++) { 
					jedis.lpush(WORK_QUEUE, list.get(i));

			      }
			}else{
				object_type = parts [2];
				object_ID= parts [3];
			}
			
			System.out.println(newJObject);
					
			if(object_type.equalsIgnoreCase(("x"))){
				IndexResponse responseIndex = client.prepareIndex("plans", object_type,object_ID)
						.setParent(membercostshareResponseIndex.getId())
						.setSource(newJObject).execute()
						.actionGet();
			}else if(object_type.equalsIgnoreCase(("membercostshare"))){
				membercostshareResponseIndex = client.prepareIndex("plans", object_type,object_ID).setSource(newJObject).execute()
						.actionGet();
			}else{
				IndexResponse responseIndex = client.prepareIndex("plans", object_type,object_ID)
						.setSource(newJObject).execute()
						.actionGet();
			}
			
			jedis.del(BACKUP_QUEUE);
			
		}
	}
	
	
	public static Object recreateJSON(String id, Object object,Jedis jedis, String planId_root){
		
		
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
					if(!(keyMap.equalsIgnoreCase("_id") || keyMap.equalsIgnoreCase("_type"))){
						fillObject.put(keyMap, value);
					}
					
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
	
	private static void putParentMapping(String parentType, String childType,TransportClient client,CreateIndexRequestBuilder request) throws IOException {
		
//		final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
//                .startObject()
//                    .startObject(childType)
//                        .startObject("_parent")
//                            .field("type", parentType)
//                        .endObject()
//                    .endObject()
//                .endObject();

		
                        request.addMapping(childType, "{\"_parent\": {\n" +
                                "        \"type\": \""+parentType+"\" \n" +
                                "      }}")
                        .execute().actionGet();
		
//        client.admin().indices().preparePutMapping("plan")
//                .setType(childType).setSource(mappingBuilder)
//                .execute().actionGet();
    }
	
}
