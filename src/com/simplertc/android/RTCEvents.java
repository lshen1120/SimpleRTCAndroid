package com.simplertc.android;

public class RTCEvents {
	public static final String iceCandidate="candidate"; // {candidate:"",from:""}
	public static final String createOffer= "createOffer"; // {sdp:"",from:""}
	public static final String createAnswer="createAnswer";// {sdp:"",from:""}
	public static final String addStream="addStream"; // {from:""}
	public static final String removeStream= "removeStream";// {from:""}
	public static final String signalingStateChange="signalingStateChange"; // {connectionId:"",state:""}
	public static final String peerConnectionOpen= "peerConnectionOpen"; //  {connectionId:""}
    //local event
	public static final String channelOpen= "channelOpen"; //  { channel:"" }
	public static final String error= "error"; //{errorMessage:""}
	//server event
	public static final String removeConnection= "removeConnection"; // { connectionId:""  }
	public static final String newConnection= "newConnection"; // { connectionId:""  }	 
	public static final String getConnections= "getConnections"; // { connectionIds:[] }

}
