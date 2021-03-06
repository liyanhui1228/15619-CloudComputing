package com.cloudcomputing.samza.pitt_cabs;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.HashMap;

import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.Entry;
import org.apache.samza.storage.kv.KeyValueIterator;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.apache.samza.task.WindowableTask;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import kafka.utils.Json;

/**
 * Consumes the stream of driver location updates and rider cab requests.
 * Outputs a stream which joins these 2 streams and gives a stream of rider
 * to driver matches.
 */
public class DriverMatchTask implements StreamTask, InitableTask, WindowableTask {

  /* Define per task state here. (kv stores etc) */
	private KeyValueStore<String, String> driverloc;
	//private KeyValueStore<String, String> riderloc;
	//private KeyValueStore<String, String> driverlist;
  @Override
  @SuppressWarnings("unchecked")
  public void init(Config config, TaskContext context) throws Exception {
	//Initialize stuff (maybe the kv stores?)
	 //to save driver's location and block
	  //just like (block+driverid, lat+lon )
	driverloc =(KeyValueStore<String, String>)context.getStore("driver-loc");
	//driverlist =(KeyValueStore<String, String>)context.getStore("driver-list");
	
  }

  @Override
  @SuppressWarnings("unchecked")
  public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator) {
	// The main part of your code. Remember that all the messages for a particular partition
	// come here (somewhat like MapReduce). So for task 1 messages for a blockId will arrive
	// at one task only, thereby enabling you to do stateful stream processing.
	  String incomingStream = envelope.getSystemStreamPartition().getStream();
	  
	  if(incomingStream.equals(DriverMatchConfig.DRIVER_LOC_STREAM.getStream())){
		 //process location log
		  processdriverlocations((Map<String, Object>) envelope.getMessage(), collector);
	  }else if(incomingStream.equals(DriverMatchConfig.EVENT_STREAM.getStream())){
		  //process event
		  processevent((Map<String, Object>)envelope.getMessage(), collector);
	  }else{
	      throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
	  }
  }

  public void processdriverlocations(Map<String, Object> message, MessageCollector collector) {
	//if it is not location file  throw exception
	  if(!message.get("type").equals("DRIVER_LOCATION")){
	      throw new IllegalStateException("Unexpected event type on follows stream: " + message.get("event"));
	}
	  
	 //parse values
	String driverId = (int) message.get("driverId")+"";
	String latitude =(int) message.get("latitude")+"";
	String longitude = (int) message.get("longitude")+"";
	String block = (int)message.get("blockId")+"";
	//put in the store 
	driverloc.put(block+":"+driverId,latitude+":"+longitude);

  }
  
  public void processevent(Map<String, Object> message, MessageCollector collector) { 
	//get the driver's event
	  if(message.get("type").equals("LEAVING_BLOCK")||message.get("type").equals("ENTERING_BLOCK")){
		  String driverId = (int) message.get("driverId")+"";
		  String latitude =(int) message.get("latitude")+"";
		  String longitude = (int) message.get("longitude")+"";
		  String block = (int)message.get("blockId")+"";
		  //if availabe put in store
		  if(message.get("status").equals("AVAILABLE")){
			  driverloc.put(block+":"+driverId,latitude+":"+longitude);
		  }else{
		//if not just delete it
			  driverloc.delete(block+":"+driverId);
		  }		
		  //if it is RIDE_COMP put back to store
	}else if(message.get("type").equals("RIDE_COMPLETE")){
		String driverId = (int) message.get("driverId")+"";
		String latitude =(int) message.get("latitude")+"";
		String longitude = (int) message.get("longitude")+"";
		String block = (int)message.get("blockId")+"";
		driverloc.put(block+":"+driverId,latitude+":"+longitude);

	}else if(message.get("type").equals("RIDE_REQUEST")){
		//if it is a request
		//parse values
		String riderId = (int) message.get("riderId")+"";
		int rlatitude =(int) message.get("latitude");
		int rlongitude = (int) message.get("longitude"); 
		String rblock = (int)message.get("blockId")+"";
		
		// scan map and get the nearest driver
		//just like example code 
		// Colon is used as separator, and semicolon is lexicographically after colon
		KeyValueIterator<String, String> drivers = driverloc.range(rblock+":", rblock+";");
		String driverId = "";
		String key = "";
		int distance = Integer.MAX_VALUE;
		try{
			while(drivers.hasNext()){
				Entry<String, String> e = drivers.next();
				String ekey = e.getKey();
				String evalue=e.getValue();
				String[] ll = evalue.split(":");
				if((ll.length ==2)){
					int dlat = Integer.parseInt(ll[0]);
					int dlon = Integer.parseInt(ll[1]);
					//calculate the distance and always get the nearest driver
					int b =((int)Math.pow((dlat-rlatitude), 2)+(int)Math.pow((dlon-rlongitude), 2));
					if(b<distance){
						distance = b;
						driverId = ekey.split(":")[1];
						key =  ekey;
					}
				}
			}
		}catch(NoSuchElementException e){
			
		}
		drivers.close();
		
		//finish scan and send out driverid and riderid 
		HashMap<String,Object> match = new HashMap<>();
		if((!driverId.equals(""))&&(!key.equals(""))){
			match.put("driverId", Integer.parseInt(driverId));
			match.put("riderId", Integer.parseInt(riderId));
			//most important     if match a driver delete from the map
			driverloc.delete(key);
			collector.send(new OutgoingMessageEnvelope(DriverMatchConfig.MATCH_STREAM,null,null,match));
		}
		
	}
  }
  

  @Override
  public void window(MessageCollector collector, TaskCoordinator coordinator) {
	//this function is called at regular intervals, not required for this project
  }
}
