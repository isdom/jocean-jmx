/**
 * 
 */
package org.jocean.jmx.relay;

/**
 * @author isdom
 *
 */
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServerProvider;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;

import org.jocean.jmx.relay.server.JMXRelayConnectorServer;

public class ServerProvider implements JMXConnectorServerProvider {

    public JMXConnectorServer newJMXConnectorServer(JMXServiceURL serviceURL,
						    Map environment,
						    MBeanServer mbeanServer) 
	    throws IOException {
   	
	if (!serviceURL.getProtocol().equals("relay")) {
	    throw new MalformedURLException("Protocol not relay: " +
					    serviceURL.getProtocol());
	}
        return new JMXRelayConnectorServer(serviceURL, environment, mbeanServer);
    }
}
