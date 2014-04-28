/**
 * 
 */
package org.jocean.jmx.relay.message;

import javax.management.remote.message.Message;

/**
 * @author isdom
 *
 */
public class RelayMessage implements Message {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1215970899346271921L;
	
	public RelayMessage(String url) {
		this.relayURL = url;
	}
	
	public String getRelayURL() {
		return	this.relayURL;
	}
	
    private final String relayURL;
}
