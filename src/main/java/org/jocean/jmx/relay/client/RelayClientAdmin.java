/**
 * 
 */
package org.jocean.jmx.relay.client;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.management.remote.JMXServiceURL;
import javax.management.remote.generic.MessageConnection;
import javax.management.remote.message.Message;

import org.jocean.jmx.relay.message.RelayMessage;
import org.jocean.jmx.relay.message.RelayResultMessage;

import com.sun.jmx.remote.generic.ClientAdmin;

/**
 * @author isdom
 *
 */
public class RelayClientAdmin implements ClientAdmin {

	private ClientAdmin impl;
    private Map env = null;
	
    public RelayClientAdmin(Map env, ClientAdmin impl ) {
        this.env = (env != null) ? env : Collections.EMPTY_MAP;
        this.impl = impl;
        
        //	TODO, to fix impl is null
    }
	
	@Override
	public void connectionClosed(MessageConnection mc) {
		impl.connectionClosed(mc);
	}

	@Override
	public MessageConnection connectionOpen(MessageConnection mc)
			throws IOException {
		MessageConnection retmc = impl.connectionOpen(mc);
		
	    //	TODO send relay info BY maming, start
	    if ( env.containsKey(RelayConnector.CONNECTION_ADDRESS) ) {
	    	JMXServiceURL address = (JMXServiceURL)env.get(RelayConnector.CONNECTION_ADDRESS);
	    	if ( address.getURLPath() != null && address.getURLPath().length() > 1) {
		    	String relayURL = address.getURLPath().substring(1);
		    	if ( relayURL != null && relayURL.length() > 0 ) {
		    		//
		    		//	有效 relay 信息, 在impl.connectionOpen 完成握手后
		    		//	即进行 relay 信息的传输  - writeMessage，
		    		//	并确保接收到 relay 返回消息 - readMessage
		    		//
//			    	System.out.println( "RelayClientAdmin's connectionOpen:" 
//			    			+ relayURL );
			    	retmc.writeMessage( new RelayMessage(relayURL) );
		            try {
						Message msg = retmc.readMessage();
						if ( msg instanceof RelayResultMessage ) {
							RelayResultMessage result = (RelayResultMessage)msg;
							if ( !result.isRelaySucceed() ) {
				                throw new IOException("relay JMX failed: " +
	                                      result.getFailedReason());
							}
						}
						else {
			                throw new IOException("unable read RelayResultMessage.");
						}
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
		                throw new IOException("unable read RelayResultMessage.");
					}
		    	}
	    	}
	    }
	    //	TODO end.
	    
		return retmc;
	}

	@Override
	public String getConnectionId() {
		return impl.getConnectionId();
	}

}
