package com.simplertc.android;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class EventSource {
	private HashMap<String, List<EventHandler>> eventHandlers;

	public EventSource() {
		eventHandlers = new HashMap<String, List<EventHandler>>();
	}

	public void notifyListeners(String type, JSONObject data) {
		List<EventHandler> handlers = eventHandlers.get(type);
		if (handlers != null) {
			for (EventHandler handler : handlers) {
				try {
					handler.onExcute(type, data);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public void notifyListeners(String type, String dataKey, String dataValue) {
		JSONObject data = new JSONObject();
		try {
			data.put(dataKey, dataValue);
			notifyListeners(type, data);
		} catch (JSONException e) {
			e.printStackTrace();
		}

	}

	public void addListener(String type, EventHandler handler) {
		List<EventHandler> handlers = eventHandlers.get(type);
		if (handlers == null) {
			handlers = new LinkedList<EventSource.EventHandler>();
			eventHandlers.put(type, handlers);
		}
		handlers.add(handler);
	}

	public boolean removeListener(String type, EventHandler handler) {
		List<EventHandler> cmds = eventHandlers.get(type);
		if (cmds == null) {
			return false;
		}
		return cmds.remove(handler);
	}

	public interface EventHandler {
		public void onExcute(String type, JSONObject data) throws Exception;
	}
}
