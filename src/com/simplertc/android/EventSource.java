package com.simplertc.android;
import java.util.HashMap;
import org.json.JSONObject;

public  class EventSource {
	private HashMap<String, EventCommand> commandMap;
	public EventSource(){
		commandMap=new HashMap<String,EventSource.EventCommand>();
	}
	public void onEvent(String type,JSONObject data){
		EventCommand cmd=commandMap.get(type);
		if(cmd!=null){
			try {
				cmd.onExcute(type, data);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void addListener(String type,EventCommand callback){
		commandMap.put(type, callback);
	}
	
	public interface EventCommand
	{
		public void onExcute(String type,JSONObject data) throws Exception;
	}
}
