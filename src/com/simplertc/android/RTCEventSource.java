package com.simplertc.android;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class RTCEventSource {
	private HashMap<String, List<EventHandler>> eventHandlers;

	public RTCEventSource() {
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

	public void addListener(String type, EventHandler handler) {
		List<EventHandler> handlers = eventHandlers.get(type);
		if (handlers == null) {
			handlers = new LinkedList<RTCEventSource.EventHandler>();
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
