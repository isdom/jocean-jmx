/**
 * 
 */
package org.jocean.jmx.relay.message;

import javax.management.remote.message.Message;

/**
 * @author isdom
 *
 */
public class RelayResultMessage implements Message {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -690264898755374716L;

	public RelayResultMessage(int result, String reason) {
		this.relayResult = result;
		this.failedReason = reason;
	}
	
	public int getRelayResult() {
		return relayResult;
	}
	
	public boolean isRelaySucceed() {
		return	0 == this.relayResult;
	}

	public String getFailedReason() {
		return failedReason;
	}
	
    private final int relayResult;
    private	final String	failedReason;
}
