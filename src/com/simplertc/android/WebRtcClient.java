package com.simplertc.android;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import android.R.bool;
import android.util.Log;
import com.codebutler.android_websockets.WebSocketClient;
import com.simplertc.android.RTCEventSource.EventHandler;

public class WebRtcClient {
	private PeerConnectionFactory factory;
	private HashMap<String, Peer> peers = new HashMap<String, Peer>();
	private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
	private MediaConstraints pcConstraints = new MediaConstraints();
	private MediaStream localStream;
	private RTCListener mListener;
	private VideoSource videoSource;
	private AudioSource audioSource;
	private WebSocketClient client;
	private final RTCEventSource eventSource = new RTCEventSource();
	private final static String TAG = "WebRtcClient";
	private boolean localVideoStopped;

	public boolean isLocalStreamStopped() {
		return localVideoStopped;
	}

	public interface RTCListener {

		void onStatusChanged(String newStatus);

		void onLocalStream(MediaStream localStream);

		void onAddRemoteStream(MediaStream remoteStream);

		void onRemoveRemoteStream(MediaStream remoteStream);

	}

	public void sendMessage(String to, String type, JSONObject message)
			throws JSONException {
		message.put("to", to);
		message.put("type", type);
		client.send(message.toString());
	}

	public void addListener(String type, EventHandler handler) {
		eventSource.addListener(type, handler);
	}

	public WebRtcClient(RTCListener listener) {
		mListener = listener;
		factory = new PeerConnectionFactory();
		localVideoStopped = true;
		iceServers.add(new PeerConnection.IceServer(
				"stun:leechanproxy.cloudapp.net:9001"));
		// iceServers.add(new
		// IceServer("turn:211.64.115.116?transport=tcp","shij","shij"));

		pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveAudio", "true"));
		pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveVideo", "true"));
		localStream = factory.createLocalMediaStream("ARDAMS");

		eventSource.addListener(RTCEvents.getConnections, new EventHandler() {
			@Override
			public void onExcute(String type, JSONObject data) throws Exception {
				JSONArray connectionIds = data.getJSONArray("connectionIds");
				for (int i = 0; i < connectionIds.length(); i++) {
					String peerId = connectionIds.get(i).toString();
					addPeer(peerId);
					Peer peer = peers.get(peerId);
					peer.pc.createOffer(peer, pcConstraints);
				}
			}
		});

		eventSource.addListener(RTCEvents.newConnection, new EventHandler() {

			@Override
			public void onExcute(String type, JSONObject data) throws Exception {
				addPeer(data.getString("connectionId"));
			}
		});

		eventSource.addListener(RTCEvents.removeConnection, new EventHandler() {

			@Override
			public void onExcute(String type, JSONObject data) throws Exception {
				String connectId = data.getString("connectionId");
				removePeer(connectId);
			}
		});

		eventSource.addListener(RTCEvents.iceCandidate, new EventHandler() {
			@Override
			public void onExcute(String type, JSONObject data) throws Exception {
				String peerId = data.getString("from");
				JSONObject candidateJson = data.getJSONObject("candidate");
				PeerConnection pc = peers.get(peerId).pc;
				if (pc.getRemoteDescription() != null) {
					IceCandidate candidate = new IceCandidate(candidateJson
							.getString("sdpMid"), candidateJson
							.getInt("sdpMLineIndex"), candidateJson
							.getString("candidate"));
					pc.addIceCandidate(candidate);
				}
			}
		});

		eventSource.addListener(RTCEvents.createOffer, new EventHandler() {
			@Override
			public void onExcute(String type, JSONObject data) throws Exception {
				String peerId = data.getString("from");
				Peer peer = peers.get(peerId);
				JSONObject sdpJson = data.getJSONObject("sdp");
				SessionDescription sdp = new SessionDescription(
						SessionDescription.Type.fromCanonicalForm(sdpJson
								.getString("type")), preferISAC(sdpJson
								.getString("sdp")));
				peer.pc.setRemoteDescription(peer, sdp);
				peer.pc.createAnswer(peer, pcConstraints);
			}
		});

		eventSource.addListener(RTCEvents.createAnswer, new EventHandler() {
			@Override
			public void onExcute(String type, JSONObject data) throws Exception {
				String peerId = data.getString("from");
				Peer peer = peers.get(peerId);
				JSONObject sdpJson = data.getJSONObject("sdp");
				SessionDescription sdp = new SessionDescription(
						SessionDescription.Type.fromCanonicalForm(sdpJson
								.getString("type")), sdpJson.getString("sdp"));
				peer.pc.setRemoteDescription(peer, sdp);
			}
		});
	}

	public WebRtcClient configVideo(String cameraFacing, String height,
			String width) {
		if(videoSource!=null){
			videoSource.stop();
			localStream.videoTracks.clear();
		}
		
		MediaConstraints videoConstraints = new MediaConstraints();
		videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"maxHeight", height));
		videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"maxWidth", width));
		videoSource = factory.createVideoSource(getVideoCapturer(cameraFacing),
				videoConstraints);
		localStream.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
		localVideoStopped = false;
		mListener.onLocalStream(localStream);
		return this;
	}

	public WebRtcClient configAudio() {
		MediaConstraints audioConstraints = new MediaConstraints();
		audioSource = factory.createAudioSource(audioConstraints);
		localStream.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));
		return this;
	}

	public void connectChannel(final String channelUrl) {
		List<BasicNameValuePair> extraHeaders = Arrays
				.asList(new BasicNameValuePair("Cookie", "session=abcd"));
		client = new WebSocketClient(URI.create(channelUrl),
				new WebSocketClient.Listener() {
					@Override
					public void onConnect() {
						JSONObject data = new JSONObject();
						try {
							data.put("channel", channelUrl);
							eventSource.notifyListeners(RTCEvents.channelOpen,
									data);
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

					@Override
					public void onMessage(String message) {
						Log.d(TAG, String.format("Got string message! %s",
								message));
						try {
							JSONObject jsonObject = new JSONObject(message);
							String type = jsonObject.getString("type");
							eventSource.notifyListeners(type, jsonObject);
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

					@Override
					public void onMessage(byte[] data) {
						Log.d(TAG, String.format("Got binary message! %s"));
					}

					@Override
					public void onDisconnect(int code, String reason) {
						Log.d(TAG, String.format(
								"Disconnected! Code: %d Reason: %s", code,
								reason));

					}

					@Override
					public void onError(Exception error) {
						JSONObject data = new JSONObject();
						try {
							data.put("errorMessage", error.getMessage());
							eventSource.notifyListeners(RTCEvents.error, data);
							Log.e(TAG, "Error!", error);
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}, extraHeaders);
		client.connect();
	}

	public void stopLocalVideo() {
		if (!localVideoStopped && videoSource != null) {
			videoSource.stop();
			localVideoStopped = true;
		}
	}

	public void restartLocalVideo() {
		if (localVideoStopped && videoSource != null) {
			videoSource.restart();
			localVideoStopped = false;
		}
	}

	public void close() {
		if (videoSource != null) {
			videoSource.stop();
		}
		factory.dispose();
		localStream.audioTracks.clear();
		localStream.videoTracks.clear();
		localVideoStopped = true;
		for (Peer peer : peers.values()) {
			peer.pc.close();
		}
		peers.clear();
		if (client != null) {
			client.disconnect();
		}
	}

	private void addPeer(String id) {
		Peer peer = new Peer(id);
		peers.put(id, peer);
	}

	private void removePeer(String id) {
		Peer peer = peers.get(id);
		if (peer != null) {
			peer.pc.close();
			// peer.pc.dispose(); bug
			peers.remove(peer.id);
		}
	}

	/*
	 * Cycle through likely device names for the camera and return the first
	 * capturer that works, or crash if none do.
	 */
	private VideoCapturer getVideoCapturer(String cameraFacing) {
		int[] cameraIndex = { 0, 1 };
		int[] cameraOrientation = { 0, 90, 180, 270 };
		for (int index : cameraIndex) {
			for (int orientation : cameraOrientation) {
				String name = "Camera " + index + ", Facing " + cameraFacing
						+ ", Orientation " + orientation;
				VideoCapturer capturer = VideoCapturer.create(name);
				if (capturer != null) {
					return capturer;
				}
			}
		}
		throw new RuntimeException("Failed to open capturer");
	}

	private void addDTLSConstraintIfMissing(MediaConstraints pcConstraints) {
		for (MediaConstraints.KeyValuePair pair : pcConstraints.mandatory) {
			if (pair.getKey().equals("DtlsSrtpKeyAgreement")) {
				return;
			}
		}
		for (MediaConstraints.KeyValuePair pair : pcConstraints.optional) {
			if (pair.getKey().equals("DtlsSrtpKeyAgreement")) {
				return;
			}
		}
		// DTLS isn't being suppressed (e.g. for debug=loopback calls), so
		// enable
		// it by default.
		pcConstraints.optional.add(new MediaConstraints.KeyValuePair(
				"DtlsSrtpKeyAgreement", "true"));
	}

	// Mangle SDP to prefer ISAC/16000 over any other audio codec.
	private static String preferISAC(String sdpDescription) {
		String[] lines = sdpDescription.split("\r\n");
		int mLineIndex = -1;
		sdpDescription=sdpDescription.replaceAll("m=audio 1 RTP/SAVPF 111 103 9 102 0 8 106 105 13 127 126", 
				"m=audio 1 RTP/SAVPF 111 103 0 8 106 105 13 126");
		String isac16kRtpMap = null;
		Pattern isac16kPattern = Pattern
				.compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
		for (int i = 0; (i < lines.length)
				&& (mLineIndex == -1 || isac16kRtpMap == null); ++i) {
			if (lines[i].startsWith("m=audio ")) {
				mLineIndex = i;
				continue;
			}
			Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
			if (isac16kMatcher.matches()) {
				isac16kRtpMap = isac16kMatcher.group(1);
				continue;
			}
		}
		if (mLineIndex == -1) {
			Log.d(TAG, "No m=audio line, so can't prefer iSAC");
			return sdpDescription;
		}
		if (isac16kRtpMap == null) {
			Log.d(TAG, "No ISAC/16000 line, so can't prefer iSAC");
			return sdpDescription;
		}
		String[] origMLineParts = lines[mLineIndex].split(" ");
		StringBuilder newMLine = new StringBuilder();
		int origPartIndex = 0;
		// Format is: m=<media> <port> <proto> <fmt> ...
		newMLine.append(origMLineParts[origPartIndex++]).append(" ");
		newMLine.append(origMLineParts[origPartIndex++]).append(" ");
		newMLine.append(origMLineParts[origPartIndex++]).append(" ");
		newMLine.append(isac16kRtpMap);
		for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
			if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
				newMLine.append(" ").append(origMLineParts[origPartIndex]);
			}
		}
		lines[mLineIndex] = newMLine.toString();
		StringBuilder newSdpDescription = new StringBuilder();
		for (String line : lines) {
			newSdpDescription.append(line).append("\r\n");
		}
		return newSdpDescription.toString();
	}

	private class Peer implements SdpObserver, PeerConnection.Observer {
		private PeerConnection pc;
		private String id;

		@Override
		public void onCreateSuccess(final SessionDescription oriSdp) {
			SessionDescription sdp = new SessionDescription(oriSdp.type,
					preferISAC(oriSdp.description));
			try {
				JSONObject sdpJson = new JSONObject();
				String type = sdp.type.canonicalForm();
				sdpJson.put("type", sdp.type.canonicalForm());
				sdpJson.put("sdp", sdp.description);
				JSONObject data = new JSONObject();
				data.put("sdp", sdpJson);
				pc.setLocalDescription(Peer.this, sdp);
				if (type.endsWith("answer")) {
					sendMessage(id, "createAnswer", data);
				} else {
					sendMessage(id, "createOffer", data);
				}

			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onSetSuccess() {
		}

		@Override
		public void onCreateFailure(String s) {
		}

		@Override
		public void onSetFailure(String s) {
		}

		@Override
		public void onSignalingChange(
				PeerConnection.SignalingState signalingState) {
		}

		@Override
		public void onIceConnectionChange(
				PeerConnection.IceConnectionState iceConnectionState) {
			if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
				removePeer(id);
			}
			mListener.onStatusChanged(iceConnectionState.toString());
		}

		@Override
		public void onIceGatheringChange(
				PeerConnection.IceGatheringState iceGatheringState) {
		}

		@Override
		public void onIceCandidate(final IceCandidate candidate) {
			try {
				JSONObject candidateJson = new JSONObject();
				candidateJson.put("sdpMLineIndex", candidate.sdpMLineIndex);
				candidateJson.put("sdpMid", candidate.sdpMid);
				// candidateJson.put("candidate", "a="+candidate.sdp+"\r\n");
				candidateJson.put("candidate", candidate.sdp);
				JSONObject message = new JSONObject();
				message.put("candidate", candidateJson);
				sendMessage(id, "candidate", message);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onError() {
		}

		@Override
		public void onAddStream(MediaStream mediaStream) {
			eventSource.notifyListeners(RTCEvents.addStream, null);
			mListener.onAddRemoteStream(mediaStream);
		}

		@Override
		public void onRemoveStream(MediaStream mediaStream) {
			eventSource.notifyListeners(RTCEvents.removeStream, null);
			mListener.onRemoveRemoteStream(mediaStream);
			removePeer(id);
		}

		@Override
		public void onDataChannel(DataChannel dataChannel) {
		}

		public Peer(String id) {
			addDTLSConstraintIfMissing(pcConstraints);
			this.pc = factory.createPeerConnection(iceServers, pcConstraints,
					this);
			this.id = id;
			pc.addStream(localStream, new MediaConstraints());
		}

		@Override
		public void onRenegotiationNeeded() {
			// TODO Auto-generated method stub

		}
	}
}
