/**
 * 
 */
package org.jocean.jmx.relay.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

/**
 * @author isdom
 *
 */
/**
 * <p>A JMX API connector server that creates connections using the JMX
 * Messaging Protocol over TCP.
 * Rather than instantiate this class directly, it is recommended to
 * use the {@link JMXConnectorServerFactory} with a {@link JMXServiceURL}
 * that has protocol type <code>relay</code>.</p>
 *
 * <p>The address that the connector server should listen for
 * connections on is specified with a {@link JMXServiceURL} provided
 * to the constructor.  If the host is omitted from this address, the
 * local host is understood.  If the port is omitted from this
 * address, the connector server will listen on an unspecified
 * available port.  The {@link #getAddress() getAddress} method can be
 * used to discover which port it is.</p>
 *
 * <p>If the local machine has more than one network interface, it is
 * possible to listen for connections on all of the interfaces, or on
 * just one interface.  By default, a connector server listens on all
 * of the interfaces.  To listen on just one, specify the address of
 * the interface in the <code>JMXServiceURL</code> and supply the
 * attribute {@link #SERVER_ADDRESS_WILDCARD} with the value
 * <code>"false"</code> when constructing the connector server.</p>
 *
 * <p>In addition to any protocol-specific authentication, this
 * connector server can verify new connections and/or modify the
 * authenticated {@link Subject} by supplying a {@link
 * JMXAuthenticator} using the attribute {@link #AUTHENTICATOR}.  The
 * {@link JMXAuthenticator#authenticate authenticate} method will be
 * called with a two-element <code>Object[]</code>.  The first element
 * is a String that is the connection id of the new connection.  The
 * second element is either null or the authenticated {@link Subject}.
 * If the connection is accepted, the method returns the
 * <code>Subject</code> to use for received requests, or null if there
 * is none.  If the connection is rejected, the method throws an
 * exception, typically {@link SecurityException}.</p>
 */
public class JMXRelayConnectorServer extends CommonConnectorServer {
    /**
     * <p>Name of the attribute that specifies whether the connector
     * server should listen for connections on all connected network
     * interfaces, or just on the interface whose address is specified
     * in the {@link JMXServiceURL}.  The value associated with this
     * attribute, if any, should be a string that is equal, ignoring
     * case, to <code>"true"</code> or <code>"false"</code>.  The
     * default value is true.</p>
     */
    public static final String SERVER_ADDRESS_WILDCARD =
	"jmx.remote.server.address.wildcard";

    /**
     * <p>Creates a connector server that listens for connection
     * requests on an unspecified port.  A connector server created in
     * this way must be registered as an MBean in the MBean server
     * that is being made available remotely.</p>
     *
     * <p>This constructor is equivalent to {@link
     * #JMXMPConnectorServer(JMXServiceURL,Map,MBeanServer)
     * JMXMPConnectorServer(null,null,null)}.</p>
     *
     * @exception IOException if the connector server cannot be created
     * for some reason.
     */
    public JMXRelayConnectorServer() throws IOException {
	this((MBeanServer)null);
    }

    /**
     * <p>Creates a connector server that listens for connection
     * requests on an unspecified port.  The MBean server that is
     * being made available remotely is specified by the
     * <code>mbs</code> parameter.</p>
     *
     * <p>This constructor is equivalent to {@link
     * #JMXMPConnectorServer(JMXServiceURL,Map,MBeanServer)
     * JMXMPConnectorServer(null,null,mbs)}.</p>
     *
     * @param mbs the MBean server that is being made available
     * remotely.
     *
     * @exception IOException if the connector server cannot be created
     * for some reason.
     */
    public JMXRelayConnectorServer(MBeanServer mbs) throws IOException {
	this(null, null, mbs);
    }

    /**
     * <p>Creates a connector server that listens for connection
     * requests on the given address with the given parameters.  A
     * connector server created in this way must be registered as an
     * MBean in the MBean server that is being made available
     * remotely.</p>
     *
     * <p>This constructor is equivalent to {@link
     * #JMXMPConnectorServer(JMXServiceURL,Map,MBeanServer)
     * JMXMPConnectorServer(address,env,null)}.</p>
     *
     * @param address the address that the connector server will listen
     * for connections on.  If null, the connector server will listen
     * for connections on an unspecified port of the local host name.
     *
     * @param env the properties of the connector server.  This
     * parameter can be null, which is equivalent to an empty Map.
     * The supplied Map is not modified.
     *
     * @exception IllegalArgumentException if <var>env</var> contains
     *            some invalid values.
     *
     * @exception IOException if the connector server cannot be created
     * for some reason.
     */
    public JMXRelayConnectorServer(JMXServiceURL address, Map env)
	    throws IOException {
	this(address, env, (MBeanServer)null);
    }

    /**
     * <p>Creates a connector server that listens for connection
     * requests on the given address with the given parameters.  The
     * MBean server that is being made available remotely is specified
     * by the <code>mbs</code> parameter.</p>
     *
     * @param address the address that the connector server will listen
     * for connections on.  If null, the connector server will listen
     * for connections on an unspecified port of the local host name.
     *
     * @param env the properties of the connector server.  This
     * parameter can be null, which is equivalent to an empty Map.
     * The supplied Map is not modified.
     *
     * @param mbs the MBean server that this connector server is
     * making available remotely.  Null if this connector server will
     * be registered as an MBean in the MBean server to be made
     * available.
     *
     * @exception IllegalArgumentException if <var>env</var> contains
     *            some invalid values.
     *
     * @exception IOException if the connector server cannot be created
     * for some reason.
     */
    public JMXRelayConnectorServer(JMXServiceURL address, Map env,
				MBeanServer mbs) throws IOException {
	super(completeEnv(address, env), mbs);
    }

    private static Map completeEnv(JMXServiceURL address, Map env)
	    throws IOException {
	if (env != null && env.containsKey(MESSAGE_CONNECTION_SERVER))
	    return env;
	if (address == null)
	    address = new JMXServiceURL("relay", null, 0);
	Map newEnv = (env == null) ? new HashMap() : new HashMap(env);
	newEnv.put(MESSAGE_CONNECTION_SERVER,
		   new SocketConnectionServer(address, newEnv));
	return newEnv;
    }
}
