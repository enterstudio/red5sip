package org.red5.sip.app;

import java.util.Enumeration;
import java.util.Vector;

import local.media.AudioClipPlayer;
import local.ua.MediaLauncher;

import org.red5.codecs.SIPCodec;
import org.red5.codecs.SIPCodecUtils;
import org.red5.sip.util.SdpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zoolu.sdp.MediaDescriptor;
import org.zoolu.sdp.MediaField;
import org.zoolu.sdp.SessionDescriptor;
import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.call.Call;
import org.zoolu.sip.call.CallListenerAdapter;
import org.zoolu.sip.call.ExtendedCall;
import org.zoolu.sip.header.StatusLine;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.tools.Parser;

//import java.util.Iterator;

public class SIPUserAgent extends CallListenerAdapter {

	protected static Logger log = LoggerFactory.getLogger(SIPUserAgent.class);

	/** UserAgentProfile */
	protected SIPUserAgentProfile userProfile;

	/** SipProvider */
	protected SipProvider sipProvider;

	/** Call */
	// Call call;
	protected ExtendedCall call;

	/** Call transfer */
	protected ExtendedCall callTransfer;

	/** Audio application */
	public SIPAudioLauncher audioApp = null;

	/** Video application */
	protected MediaLauncher videoApp = null;

	/** Local sdp */
	protected String localSession = null;

	/** SIPUserAgent listener */
	protected SIPUserAgentListener listener = null;

	/** Media file path */
	final String MEDIA_PATH = "media/local/ua/";

	/** On wav file */
	final String CLIP_ON = MEDIA_PATH + "on.wav";

	/** Off wav file */
	final String CLIP_OFF = MEDIA_PATH + "off.wav";

	/** Ring wav file */
	final String CLIP_RING = MEDIA_PATH + "ring.wav";

	/** Ring sound */
	AudioClipPlayer clip_ring;

	/** On sound */
	AudioClipPlayer clip_on;

	/** Off sound */
	AudioClipPlayer clip_off;

	private IMediaReceiver mediaReceiver;

	/** Sip codec to be used on audio session */
	private SIPCodec sipAudioCodec = null;

	/** Sip codec to be used on video session */
	private SIPCodec sipVideoCodec = null;

	// *********************** Startup Configuration ***********************

	/** UA_IDLE=0 */
	static final String UA_IDLE = "IDLE";

	/** UA_INCOMING_CALL=1 */
	static final String UA_INCOMING_CALL = "INCOMING_CALL";

	/** UA_OUTGOING_CALL=2 */
	static final String UA_OUTGOING_CALL = "OUTGOING_CALL";

	/** UA_ONCALL=3 */
	static final String UA_ONCALL = "ONCALL";

	/**
	 * Call state
	 * <P>
	 * UA_IDLE=0, <BR>
	 * UA_INCOMING_CALL=1, <BR>
	 * UA_OUTGOING_CALL=2, <BR>
	 * UA_ONCALL=3
	 */
	String call_state = UA_IDLE;

	// *************************** Basic methods ***************************

	/** Changes the call state */
	protected void changeStatus(String state) {

		call_state = state;
		// log.debug("state: "+call_state);
	}

	/** Checks the call state */
	protected boolean statusIs(String state) {

		return call_state.equals(state);
	}

	/** Gets the call state */
	protected String getStatus() {

		return call_state;
	}

	/**
	 * Sets the automatic answer time (default is -1 that means no auto accept mode)
	 */
	public void setAcceptTime(int accept_time) {

		userProfile.acceptTime = accept_time;
	}

	/**
	 * Sets the automatic hangup time (default is 0, that corresponds to manual hangup mode)
	 */
	public void setHangupTime(int time) {

		userProfile.hangupTime = time;
	}

	/** Sets the redirection url (default is null, that is no redircetion) */
	public void setRedirection(String url) {

		userProfile.redirectTo = url;
	}

	/** Sets the no offer mode for the invite (default is false) */
	public void setNoOfferMode(boolean nooffer) {

		userProfile.noOffer = nooffer;
	}

	/** Enables audio */
	public void setAudio(boolean enable) {

		userProfile.audio = enable;
	}

	/** Enables video */
	public void setVideo(boolean enable) {

		userProfile.video = enable;
	}

	/** Sets the receive only mode */
	public void setReceiveOnlyMode(boolean r_only) {

		userProfile.recvOnly = r_only;
	}

	/** Sets the send only mode */
	public void setSendOnlyMode(boolean s_only) {

		userProfile.sendOnly = s_only;
	}

	/** Sets the send tone mode */
	public void setSendToneMode(boolean s_tone) {

		userProfile.sendTone = s_tone;
	}

	/** Sets the send file */
	public void setSendFile(String file_name) {

		userProfile.sendFile = file_name;
	}

	/** Sets the recv file */
	public void setRecvFile(String file_name) {

		userProfile.recvFile = file_name;
	}

	/** Gets the local SDP */
	public String getSessionDescriptor() {

		return localSession;
	}

	/** Sets the local SDP */
	public void setSessionDescriptor(String sdp) {

		localSession = sdp;
	}

	/** Inits the local SDP (no media spec) */
	public void initSessionDescriptor() {

		log.debug("initSessionDescriptor:: Init...");

		SessionDescriptor newSdp = SdpUtils.createInitialSdp(userProfile.username, sipProvider.getViaAddress(),
				userProfile.audioPort, userProfile.videoPort, userProfile.audioCodecsPrecedence);

		localSession = newSdp.toString();

		log.debug("initSessionDescriptor:: localSession = " + localSession);
	}

	// *************************** Public Methods **************************

	/** Costructs a UA with a default media port */
	public SIPUserAgent(SipProvider sip_provider, SIPUserAgentProfile user_profile, SIPUserAgentListener listener,
			IMediaReceiver mediaReceiver) {

		this.sipProvider = sip_provider;
		this.listener = listener;
		this.userProfile = user_profile;
		this.mediaReceiver = mediaReceiver;

		// If no contact_url and/or from_url has been set, create it now.
		user_profile.initContactAddress(sip_provider);

		// Set local sdp.
		initSessionDescriptor();
	}

	public void call(String target_url) {

		log.debug("call:: Init...");

		changeStatus(UA_OUTGOING_CALL);

		if (call != null) {
			log.debug("call:: cancelling old object:" + this.call);
			this.call.cancel();
		}

		call = new ExtendedCall(sipProvider, userProfile.fromUrl, userProfile.contactUrl, userProfile.username,
				userProfile.realm, userProfile.passwd, this);

		// In case of incomplete url (e.g. only 'user' is present), try to
		// complete it.

		target_url = sipProvider.completeNameAddress(target_url).toString();

		if (userProfile.noOffer) {
			call.call(target_url);
		} else {
			call.call(target_url, localSession);
		}
	}

	public void setMedia(IMediaReceiver mediaReceiver) {

		log.debug("setMedia:: Init...");

		this.mediaReceiver = mediaReceiver;
	}

	/** Call Transfer test by Lior */

	public void transfer(String transfer_to) {
		log.debug("REFER/TRANSFER:: Init...");
		try {
			if (call != null && call.isOnCall()) {
				call.transfer(transfer_to);
			}
		} catch (Exception e) {
			log.debug("transfer: ", e);
		}
	}

	/** end of transfer test code */

	/** Waits for an incoming call (acting as UAS). */
	public void listen() {

		log.debug("listen:: Init...");

		changeStatus(UA_IDLE);

		if (call != null) {
			log.debug("listen:: cancelling old object:" + this.call);
			this.call.cancel();
		}

		call = new ExtendedCall(sipProvider, userProfile.fromUrl, userProfile.contactUrl, userProfile.username,
				userProfile.realm, userProfile.passwd, this);

		call.listen();
	}

	/** Closes an ongoing, incoming, or pending call */
	public void hangup() {

		log.debug("hangup:: Init...");

		if (clip_ring != null) {
			clip_ring.stop();
		}

		closeMediaApplication();

		if (call != null) {
			call.hangup();
		}

		changeStatus(UA_IDLE);
	}

	/** Closes an ongoing, incoming, or pending call */
	public void accept() {

		log.debug("accept:: Init...");

		if (clip_ring != null) {
			clip_ring.stop();
		}

		if (call != null) {
			call.accept(localSession);
		}
	}

	/** Redirects an incoming call */
	public void redirect(String redirection) {

		log.debug("redirect:: Init...");

		if (clip_ring != null) {
			clip_ring.stop();
		}

		if (call != null) {
			call.redirect(redirection);
		}
	}

	protected void launchMediaApplication() {

		// Exit if the Media Application is already running.
		if (audioApp != null || videoApp != null) {

			log.debug("launchMediaApplication:: Media application is already running.");
			return;
		}

		SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());

		int localAudioPort = 0;
		int localVideoPort = 0;

		// parse local sdp
		for (Enumeration<MediaDescriptor> e = localSdp.getMediaDescriptors().elements(); e.hasMoreElements();) {
			MediaField media = e.nextElement().getMedia();
			if (media.getMedia().equals("audio")) {
				localAudioPort = media.getPort();
			}
			if (media.getMedia().equals("video")) {
				localVideoPort = media.getPort();
			}
		}

		log.debug("launchMediaApplication:: localAudioPort = " + localAudioPort + ", localVideoPort = "
				+ localVideoPort + ".");

		// Parse remote sdp.
		SessionDescriptor remoteSdp = new SessionDescriptor(call.getRemoteSessionDescriptor());
		String remoteMediaAddress = (new Parser(remoteSdp.getConnection().toString())).skipString().skipString()
				.getString();

		int remoteAudioPort = 0;
		int remoteVideoPort = 0;

		for (Enumeration<MediaDescriptor> e = remoteSdp.getMediaDescriptors().elements(); e.hasMoreElements();) {

			MediaDescriptor descriptor = e.nextElement();
			MediaField media = descriptor.getMedia();

			if (media.getMedia().equals("audio")) {
				remoteAudioPort = media.getPort();
			}

			if (media.getMedia().equals("video")) {
				remoteVideoPort = media.getPort();
			}
		}

		log.debug("launchMediaApplication:: remoteAudioPort = " + remoteAudioPort + ", remoteVideoPort = "
				+ remoteVideoPort + ".");

		log.debug("launchMediaApplication:: user_profile.audio = " + userProfile.audio + ", user_profile.video = "
				+ userProfile.video + ", audio_app = " + audioApp + ", video_app = " + videoApp + ".");

		if (userProfile.audio && localAudioPort != 0 && remoteAudioPort != 0) {

			if (audioApp == null) {

				if (sipAudioCodec != null) {

					audioApp = new SIPAudioLauncher(sipAudioCodec, localAudioPort, remoteMediaAddress, remoteAudioPort,
							mediaReceiver);
				} else {
					log.debug("launchMediaApplication:: SipCodec for audio not initialized.");
				}
			}

			if (audioApp != null) {

				audioApp.startMedia();
			}
		}
		if (userProfile.video && localVideoPort != 0 && remoteVideoPort != 0) {
			if (videoApp == null) {
				if (sipVideoCodec != null) {
					videoApp = new SIPVideoLauncher(localVideoPort, remoteMediaAddress, remoteVideoPort,
							(SIPTransport) listener, mediaReceiver, sipVideoCodec);
				} else {
					log.debug("launchMediaApplication:: SipCodec for video not initialized.");
				}
			}

			if (videoApp != null) {
				videoApp.startMedia();
			}
		}

		if (listener != null) {
			listener.onUaCallConnected(this);
		}
	}

	/** Close the Media Application */
	protected void closeMediaApplication() {

		log.debug("closeMediaApplication:: Init...");

		if (audioApp != null) {

			audioApp.stopMedia();
			audioApp = null;
		}

		if (videoApp != null) {

			videoApp.stopMedia();
			videoApp = null;
		}
	}

	// ********************** Call callback functions **********************

	/**
	 * Callback function called when arriving a new INVITE method (incoming call)
	 */
	public void onCallIncoming(Call call, NameAddress callee, NameAddress caller, String sdp, Message invite) {

		log.debug("onCallIncoming:: Init...");

		if (call != this.call) {
			log.debug("onCallIncoming:: NOT the current call.");
			return;
		}

		log.debug("onCallIncoming:: INCOMING.");
		log.debug("onCallIncoming:: Inside SIPUserAgent.onCallIncoming(): sdp=\n" + sdp);

		changeStatus(UA_INCOMING_CALL);
		call.ring();

		if (sdp != null) {

			SessionDescriptor remoteSdp = new SessionDescriptor(sdp);
			SessionDescriptor localSdp = new SessionDescriptor(localSession);

			log.debug("onCallIncoming:: localSdp = " + localSdp.toString() + ".");
			log.debug("onCallIncoming:: remoteSdp = " + remoteSdp.toString() + ".");

			// First we need to make payloads negotiation so the related
			// attributes can be then matched.
			SessionDescriptor newSdp = SdpUtils.makeMediaPayloadsNegotiation(localSdp, remoteSdp);

			// After we can create the correct audio and video codecs considering
			// audio and video negotiation made above.
			sipAudioCodec = SdpUtils.getNegotiatedAudioCodec(newSdp);
			sipVideoCodec = SdpUtils.getNegotiatedVideoCodec(newSdp);

			// Now we complete the SDP negotiation informing the selected
			// codec, so it can be internally updated during the process.
			SdpUtils.completeSdpNegotiation(newSdp, localSdp, remoteSdp);

			localSession = newSdp.toString();

			log.debug("onCallIncoming:: newSdp = " + localSession + ".");

			// Finally, we use the "newSdp" and "remoteSdp" to initialize
			// the lasting codec informations.
			SIPCodecUtils.initSipAudioCodec(sipAudioCodec, userProfile.audioDefaultPacketization,
					userProfile.audioDefaultPacketization, newSdp, remoteSdp);
		}

		if (listener != null) {

			listener.onUaCallIncoming(this, callee, caller);
		}
	}

	/**
	 * Callback function called when arriving a new Re-INVITE method (re-inviting/call modify)
	 */
	public void onCallModifying(Call call, String sdp, Message invite) {

		log.debug("onCallModifying:: Init...");

		if (call != this.call) {
			log.debug("onCallModifying:: NOT the current call.");
			return;
		}

		log.debug("onCallModifying:: RE-INVITE/MODIFY.");

		// to be implemented.
		// currently it simply accepts the session changes (see method
		// onCallModifying() in CallListenerAdapter)
		super.onCallModifying(call, sdp, invite);
	}

	/**
	 * Callback function that may be overloaded (extended). Called when arriving a 180 Ringing
	 */
	public void onCallRinging(Call call, Message resp) {

		log.debug("onCallRinging:: Init...");

		if (call != this.call && call != callTransfer) {
			log.debug("onCallRinging:: NOT the current call.");
			return;
		}

		log.debug("onCallRinging:: RINGING.");

		// Play "on" sound.
		if (listener != null) {
			listener.onUaCallRinging(this);
		}
	}

	/** Callback function called when arriving a 2xx (call accepted) */
	public void onCallAccepted(Call call, String sdp, Message resp) {

		log.debug("onCallAccepted:: Init...");

		if (call != this.call && call != callTransfer) {
			log.debug("onCallAccepted:: NOT the current call.");
			return;
		}

		log.debug("onCallAccepted:: ACCEPTED/CALL.");

		changeStatus(UA_ONCALL);

		SessionDescriptor remoteSdp = new SessionDescriptor(sdp);
		SessionDescriptor localSdp = new SessionDescriptor(localSession);

		log.debug("onCallAccepted:: localSdp = " + localSdp.toString() + ".");
		log.debug("onCallAccepted:: remoteSdp = " + remoteSdp.toString() + ".");

		// First we need to make payloads negotiation so the related
		// attributes can be then matched.
		SessionDescriptor newSdp = SdpUtils.makeMediaPayloadsNegotiation(localSdp, remoteSdp);

		// After we can create the correct audio and video codecs considering
		// audio and video negotiation made above.
		sipAudioCodec = SdpUtils.getNegotiatedAudioCodec(newSdp);
		sipVideoCodec = SdpUtils.getNegotiatedVideoCodec(newSdp);

		// Now we complete the SDP negotiation informing the selected
		// codec, so it can be internally updated during the process.
		SdpUtils.completeSdpNegotiation(newSdp, localSdp, remoteSdp);

		localSession = newSdp.toString();

		log.debug("onCallAccepted:: newSdp = " + localSession + ".");

		// Finally, we use the "newSdp" and "remoteSdp" to initialize
		// the lasting codec informations.
		SIPCodecUtils.initSipAudioCodec(sipAudioCodec, userProfile.audioDefaultPacketization,
				userProfile.audioDefaultPacketization, newSdp, remoteSdp);

		if (userProfile.noOffer) {

			// Answer with the local sdp.
			call.ackWithAnswer(localSession);
		}

		launchMediaApplication();

		if (call == callTransfer) {
			StatusLine statusLine = resp.getStatusLine();
			int code = statusLine.getCode();
			String reason = statusLine.getReason();
			this.call.notify(code, reason);
		}

		if (listener != null) {
			listener.onUaCallAccepted(this);
		}
	}

	/** Callback function called when arriving an ACK method (call confirmed) */
	public void onCallConfirmed(Call call, String sdp, Message ack) {

		log.debug("onCallConfirmed:: Init...");

		if (call != this.call) {
			log.debug("onCallConfirmed:: NOT the current call.");
			return;
		}

		log.debug("onCallConfirmed:: CONFIRMED/CALL.");

		changeStatus(UA_ONCALL);

		// Play "on" sound.
		if (clip_on != null) {
			clip_on.replay();
		}

		launchMediaApplication();
	}

	/** Callback function called when arriving a 2xx (re-invite/modify accepted) */
	public void onCallReInviteAccepted(Call call, String sdp, Message resp) {

		log.debug("onCallReInviteAccepted:: Init...");

		if (call != this.call) {
			log.debug("onCallReInviteAccepted:: NOT the current call.");
			return;
		}

		log.debug("onCallReInviteAccepted:: RE-INVITE-ACCEPTED/CALL.");
	}

	/** Callback function called when arriving a 4xx (re-invite/modify failure) */
	public void onCallReInviteRefused(Call call, String reason, Message resp) {

		log.debug("onCallReInviteRefused:: Init...");

		if (call != this.call) {
			log.debug("onCallReInviteRefused:: NOT the current call");
			return;
		}

		log.debug("onCallReInviteRefused:: RE-INVITE-REFUSED (" + reason + ")/CALL.");

		if (listener != null) {
			listener.onUaCallFailed(this);
		}
	}

	/** Callback function called when arriving a 4xx (call failure) */
	public void onCallRefused(Call call, String reason, Message resp) {

		log.debug("onCallRefused:: Init...");

		if (call != this.call) {
			log.debug("onCallRefused:: NOT the current call.");
			return;
		}

		log.debug("onCallRefused:: REFUSED (" + reason + ").");

		changeStatus(UA_IDLE);

		if (call == callTransfer) {
			StatusLine status_line = resp.getStatusLine();
			int code = status_line.getCode();
			// String reason=status_line.getReason();
			this.call.notify(code, reason);
			callTransfer = null;
		}

		if (listener != null) {
			listener.onUaCallFailed(this);
		}
	}

	/** Callback function called when arriving a 3xx (call redirection) */
	public void onCallRedirection(Call call, String reason, Vector<String> contact_list, Message resp) {

		log.debug("onCallRedirection:: Init...");

		if (call != this.call) {
			log.debug("onCallRedirection:: NOT the current call.");
			return;
		}

		log.debug("onCallRedirection:: REDIRECTION (" + reason + ").");

		call.call(((String) contact_list.elementAt(0)));
	}

	/**
	 * Callback function that may be overloaded (extended). Called when arriving a CANCEL request
	 */
	public void onCallCanceling(Call call, Message cancel) {

		log.debug("onCallCanceling:: Init...");

		if (call != this.call) {
			log.debug("onCallCanceling:: NOT the current call.");
			return;
		}

		log.debug("onCallCanceling:: CANCEL.");

		changeStatus(UA_IDLE);

		if (listener != null) {
			listener.onUaCallCancelled(this);
		}
	}

	/** Callback function called when arriving a BYE request */
	public void onCallClosing(Call call, Message bye) {

		log.debug("onCallClosing:: Init...");

		if (call != this.call && call != callTransfer) {
			log.debug("onCallClosing:: NOT the current call.");
			return;
		}

		if (call != callTransfer && callTransfer != null) {
			log.debug("onCallClosing:: CLOSE PREVIOUS CALL.");
			this.call = callTransfer;
			callTransfer = null;
			return;
		}

		log.debug("onCallClosing:: CLOSE.");

		closeMediaApplication();

		// Play "off" sound.
		if (clip_off != null) {
			clip_off.replay();
		}

		if (listener != null) {
			listener.onUaCallClosing(this);
		}

		changeStatus(UA_IDLE);

		// Rest local sdp for next call.
		initSessionDescriptor();
	}

	/**
	 * Callback function called when arriving a response after a BYE request (call closed)
	 */
	public void onCallClosed(Call call, Message resp) {

		log.debug("onCallClosed:: Init...");

		if (call != this.call) {
			log.debug("onCallClosed:: NOT the current call.");
			return;
		}

		log.debug("onCallClosed:: CLOSE/OK.");

		if (listener != null) {
			listener.onUaCallClosed(this);
		}

		changeStatus(UA_IDLE);
	}

	/** Callback function called when the invite expires */
	public void onCallTimeout(Call call) {

		log.debug("onCallTimeout:: Init...");

		if (call != this.call) {
			log.debug("onCallTimeout:: NOT the current call.");
			return;
		}

		log.debug("onCallTimeout:: NOT FOUND/TIMEOUT.");

		changeStatus(UA_IDLE);

		if (call == callTransfer) {
			int code = 408;
			String reason = "Request Timeout";
			this.call.notify(code, reason);
			callTransfer = null;
		}

		// Play "off" sound.
		if (clip_off != null) {
			clip_off.replay();
		}

		if (listener != null) {
			listener.onUaCallFailed(this);
		}
	}

	// ****************** ExtendedCall callback functions ******************

	/**
	 * Callback function called when arriving a new REFER method (transfer request)
	 */
	public void onCallTransfer(ExtendedCall call, NameAddress refer_to, NameAddress refered_by, Message refer) {

		log.debug("onCallTransfer:: Init...");

		if (call != this.call) {
			log.debug("onCallTransfer:: NOT the current call.");
			return;
		}

		log.debug("onCallTransfer:: Transfer to " + refer_to.toString() + ".");

		call.acceptTransfer();

		callTransfer = new ExtendedCall(sipProvider, userProfile.fromUrl, userProfile.contactUrl, this);
		callTransfer.call(refer_to.toString(), localSession);
	}

	/** Callback function called when a call transfer is accepted. */
	public void onCallTransferAccepted(ExtendedCall call, Message resp) {

		log.debug("onCallTransferAccepted:: Init...");

		if (call != this.call) {
			log.debug("onCallTransferAccepted:: NOT the current call.");
			return;
		}

		log.debug("onCallTransferAccepted:: Transfer accepted.");
	}

	/** Callback function called when a call transfer is refused. */
	public void onCallTransferRefused(ExtendedCall call, String reason, Message resp) {

		log.debug("onCallTransferRefused:: Init...");

		if (call != this.call) {
			log.debug("onCallTransferRefused:: NOT the current call.");
			return;
		}

		log.debug("onCallTransferRefused:: Transfer refused.");
	}

	/** Callback function called when a call transfer is successfully completed */
	public void onCallTransferSuccess(ExtendedCall call, Message notify) {

		log.debug("onCallTransferSuccess:: Init...");

		if (call != this.call) {
			log.debug("onCallTransferSuccess:: NOT the current call.");
			return;
		}

		log.debug("onCallTransferSuccess:: Transfer successed.");

		call.hangup();

		if (listener != null) {
			listener.onUaCallTrasferred(this);
		}
	}

	/**
	 * Callback function called when a call transfer is NOT sucessfully completed
	 */
	public void onCallTransferFailure(ExtendedCall call, String reason, Message notify) {

		log.debug("onCallTransferFailure:: Init...");

		if (call != this.call) {
			log.debug("onCallTransferFailure:: NOT the current call.");
			return;
		}

		log.debug("onCallTransferFailure:: Transfer failed.");
	}
}
