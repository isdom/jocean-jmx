/*
 * @(#)file      ServerIntermediary.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   1.79
 * @(#)lastedit  07/03/08
 * @(#)build     @BUILD_TAG_PLACEHOLDER@
 *
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 2007 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU General
 * Public License Version 2 only ("GPL") or the Common Development and
 * Distribution License("CDDL")(collectively, the "License"). You may not use
 * this file except in compliance with the License. You can obtain a copy of the
 * License at http://opendmk.dev.java.net/legal_notices/licenses.txt or in the 
 * LEGAL_NOTICES folder that accompanied this code. See the License for the 
 * specific language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file found at
 *     http://opendmk.dev.java.net/legal_notices/licenses.txt
 * or in the LEGAL_NOTICES folder that accompanied this code.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.
 * 
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * 
 *       "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding
 * 
 *       "[Contributor] elects to include this software in this distribution
 *        under the [CDDL or GPL Version 2] license."
 * 
 * If you don't indicate a single choice of license, a recipient has the option
 * to distribute your version of this file under either the CDDL or the GPL
 * Version 2, or to extend the choice of license to its licensees as provided
 * above. However, if you add GPL Version 2 code and therefore, elected the
 * GPL Version 2 license, then the option applies only if the new code is made
 * subject to such option by the copyright holder.
 * 
 */

package org.jocean.jmx.relay.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.net.InetAddress;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServerErrorException;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;
import javax.management.remote.generic.ConnectionClosedException;
import javax.management.remote.generic.MessageConnection;
import javax.management.remote.generic.ObjectWrapping;
import javax.management.remote.message.CloseMessage;
import javax.management.remote.message.MBeanServerRequestMessage;
import javax.management.remote.message.MBeanServerResponseMessage;
import javax.management.remote.message.Message;
import javax.management.remote.message.NotificationRequestMessage;
import javax.management.remote.message.NotificationResponseMessage;
import javax.security.auth.Subject;

import org.jocean.jmx.relay.message.RelayMessage;
import org.jocean.jmx.relay.message.RelayResultMessage;

import com.sun.jmx.remote.generic.ServerSynchroMessageConnection;
import com.sun.jmx.remote.generic.ServerSynchroMessageConnectionImpl;
import com.sun.jmx.remote.generic.SynchroCallback;
import com.sun.jmx.remote.opt.internal.NotificationBuffer;
import com.sun.jmx.remote.opt.internal.ServerCommunicatorAdmin;
import com.sun.jmx.remote.opt.security.JMXSubjectDomainCombiner;
import com.sun.jmx.remote.opt.security.SubjectDelegator;
import com.sun.jmx.remote.opt.util.ClassLoaderWithRepository;
import com.sun.jmx.remote.opt.util.ClassLogger;
import com.sun.jmx.remote.opt.util.EnvHelp;
import com.sun.jmx.remote.opt.util.OrderClassLoaders;
import com.sun.jmx.remote.socket.SocketConnectionIf;

class ServerIntermediary {

	//private static Logger LOG = Logger.getLogger(ServerIntermediary.class.getCanonicalName());
	
	private void initCtxAttrs(ServerSynchroMessageConnection connection) {
//		public interface JMXConnectionContext {
//
//			public String getProtocol();
//			
//			public String getConnectionId();
//			
//			public String getPeerIp();
//			
//			public int getPeerPort();
//		}
		//	因为在 JMXUnitsHelper 直接根据方法名，获取属性 所以目前需要设置 
		//	"getProtocol" & "getConnectionId" 两个key
		_ctxAttrs.put("getProtocol", "relay");
		_ctxAttrs.put("getConnectionId", connection.getConnectionId());
		
		String peerIp = "";
		int	peerPort = 0;
		
		if ( connection instanceof ServerSynchroMessageConnectionImpl ) {
			MessageConnection mc =
				((ServerSynchroMessageConnectionImpl)connection).getAsynchroConnection();
			if ( mc instanceof SocketConnectionIf) {
				final InetAddress addr = 
						((SocketConnectionIf)mc).getSocket().getInetAddress();
				if ( null != addr ) {
					peerIp = addr.getHostAddress();
					peerPort = ((SocketConnectionIf)mc).getSocket().getPort();
				}
			}
		}
		//	新增 两个  "getPeerIp" & "getPeerPort" 两个key
		_ctxAttrs.put("getPeerIp", peerIp);
		_ctxAttrs.put("getPeerPort", peerPort);
	}
	
    // Note: it is necessary to pass the defaultClassLoader - because
    // the defaultClassLoader is determined from the Map + context
    // class loader of the thread that calls CommonConnectorServer.start()
    public ServerIntermediary(MBeanServer mbeanServer,
			      CommonConnectorServer myServer,
			      ServerSynchroMessageConnection connection,
			      ObjectWrapping wrapper,
			      Subject subject,
			      ClassLoader defaultClassLoader,
			      Map env) {

	if (logger.traceOn()) {
	    logger.trace("constructor", "Create a ServerIntermediary object.");
	}

	if (mbeanServer == null) {
	    throw new NullPointerException("Null mbean server.");
	}

	if (connection == null) {
	    throw new NullPointerException("Null connection.");
	}

	this.mbeanServer = mbeanServer;
	this.myServer = myServer;
	this.connection = connection;

	this.clientId = connection.getConnectionId();
	this.serialization = wrapper;

	this.subjectDelegator = new SubjectDelegator();
	this.subject = subject;
	if (subject == null) {
	    this.acc = null;
	} else {
	    this.acc =
		new AccessControlContext(AccessController.getContext(),
				       new JMXSubjectDomainCombiner(subject));
	}

	this.defaultClassLoader = defaultClassLoader;

	final ClassLoader dcl = defaultClassLoader;
	this.clr = (ClassLoaderWithRepository)
	    AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			return new ClassLoaderWithRepository(
					      getClassLoaderRepository(),
					      dcl);
		    }
		});

	this.env = env;

	final long timeout = EnvHelp.getServerConnectionTimeout(this.env);

	// compatible to RI 1.0: bug 4948444
        String s = (String)this.env.get("com.sun.jmx.remote.bug.compatible");
	if (s == null) {
	    s = (String)AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			return System.getProperty("com.sun.jmx.remote.bug.compatible");
		    }
		});
	}
	isRI10 = "RI1.0.0".equals(s);

	serverCommunicatorAdmin = new GenericServerCommunicatorAdmin(timeout);
	
	//	add by maming 2012.10.08 for impl JMXUtils.currentJMXConnectionContext
	initCtxAttrs(connection);
    }

    private synchronized ServerNotifForwarder getServerNotifFwd() {
	// Lazily created when first use. Mainly when
	// addNotificationListener is first called.
	if(serverNotifForwarder == null)
		if ( null != this.relayMBeanServerConnection ) {
			relayNotifBuffer = RelayArrayNotificationBuffer.getNotificationBuffer(
					 relayMBeanServerConnection,
				      env);
			
		    serverNotifForwarder =
		    		new ServerNotifForwarder(relayMBeanServerConnection,
		    					 env, relayNotifBuffer);
		}
		else {
		    serverNotifForwarder =
		    		new ServerNotifForwarder(mbeanServer,
		    					 env, myServer.getNotifBuffer());
		}

	return serverNotifForwarder;
    }

    public Object handleRequest(MBeanServerRequestMessage req)
	throws Exception {

	if (logger.traceOn()) {
	    logger.trace("handleRequest", "Handle a request: " + req);
	}

//    System.out.println( "thread:" + Thread.currentThread().getId() 
//    		+ ", handleRequest: Handle a request: " + req.getMethodId());
    
	if (req == null) {
	    return null;
	}

	final Object[] params = req.getParams();
	switch (req.getMethodId()) {
	case MBeanServerRequestMessage.CREATE_MBEAN:

	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a CREATE_MBEAN request.");
	    }

	    return getMBeanServerConnection().createMBean((String)params[0],
					   (ObjectName)params[1]);

	case MBeanServerRequestMessage.CREATE_MBEAN_LOADER:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a CREATE_MBEAN_LOADER request.");
	    }

	    return getMBeanServerConnection().createMBean((String)params[0],
					   (ObjectName)params[1],
					   (ObjectName)params[2]);

	case MBeanServerRequestMessage.CREATE_MBEAN_PARAMS:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a CREATE_MBEAN_PARAMS request.");
	    }

	    return getMBeanServerConnection().createMBean(
			       (String)params[0],
			       (ObjectName)params[1],
			       (Object[])serialization.unwrap(params[2], clr),
			       (String[])params[3]);

	case MBeanServerRequestMessage.CREATE_MBEAN_LOADER_PARAMS:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a CREATE_MBEAN_LOADER_PARAMS request.");
	    }

	    return getMBeanServerConnection().createMBean(
		(String)params[0],
		(ObjectName)params[1],
		(ObjectName)params[2],
		(Object[])unwrapWithDefault(
				    params[3],
				    getClassLoader((ObjectName)params[2])),
		(String[])params[4]);

	case MBeanServerRequestMessage.GET_ATTRIBUTE:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_ATTRIBUTE request.");
	    }

	    RelayJMXUtils.setJMXConnectionContextImpl(_ctxAttrs);
	    
	    try {
		    return getMBeanServerConnection().getAttribute((ObjectName)params[0],
						    (String)params[1]);
	    }
	    finally {
		    RelayJMXUtils.unsetJMXConnectionContextImpl();
	    }
	    
	case MBeanServerRequestMessage.GET_ATTRIBUTES:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_ATTRIBUTES request.");
	    }

	    RelayJMXUtils.setJMXConnectionContextImpl(_ctxAttrs);
	    
	    try {
		    return getMBeanServerConnection().getAttributes((ObjectName)params[0],
				     (String[])params[1]);
	    }
	    finally {
		    RelayJMXUtils.unsetJMXConnectionContextImpl();
	    }

	case MBeanServerRequestMessage.GET_DEFAULT_DOMAIN:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_DEFAULT_DOMAIN request.");
	    }

	    return getMBeanServerConnection().getDefaultDomain();

	case MBeanServerRequestMessage.GET_DOMAINS:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_DOMAINS request.");
	    }

	    return getMBeanServerConnection().getDomains();

	case MBeanServerRequestMessage.GET_MBEAN_COUNT:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_MBEAN_COUNT request.");
	    }

	    return getMBeanServerConnection().getMBeanCount();

	case MBeanServerRequestMessage.GET_MBEAN_INFO:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_MBEAN_INFO request.");
	    }

	    return getMBeanServerConnection().getMBeanInfo((ObjectName)params[0]);

	case MBeanServerRequestMessage.GET_OBJECT_INSTANCE:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a GET_OBJECT_INSTANCE request.");
	    }

	    return getMBeanServerConnection().getObjectInstance((ObjectName)params[0]);

	case MBeanServerRequestMessage.INVOKE:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a INVOKE request.");
	    }

	    RelayJMXUtils.setJMXConnectionContextImpl(_ctxAttrs);
	    
	    try {
		    return getMBeanServerConnection().invoke(
		    		(ObjectName)params[0],
		    		(String)params[1],
		    		(Object[])unwrapWithDefault(
		    				    params[2],
		    				    getClassLoaderFor((ObjectName)params[0])),
		    		(String[])params[3]);
	    }
	    finally {
		    RelayJMXUtils.unsetJMXConnectionContextImpl();
	    }

	case MBeanServerRequestMessage.IS_INSTANCE_OF:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a IS_INSTANCE_OF request.");
	    }

	    return getMBeanServerConnection().isInstanceOf((ObjectName)params[0],
					    (String)params[1])
		? Boolean.TRUE : Boolean.FALSE;

	case MBeanServerRequestMessage.IS_REGISTERED:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a IS_REGISTERED request.");
	    }

	    return getMBeanServerConnection().isRegistered((ObjectName)params[0])
		? Boolean.TRUE : Boolean.FALSE;

	case MBeanServerRequestMessage.QUERY_MBEANS:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a QUERY_MBEANS request.");
	    }

	    return getMBeanServerConnection().queryMBeans(
		(ObjectName)params[0],
		(QueryExp)serialization.unwrap(params[1], defaultClassLoader));

	case MBeanServerRequestMessage.QUERY_NAMES:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a QUERY_NAMES request.");
	    }

	    return getMBeanServerConnection().queryNames(
		(ObjectName)params[0],
		(QueryExp)serialization.unwrap(params[1], defaultClassLoader));

	case MBeanServerRequestMessage.SET_ATTRIBUTE:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a SET_ATTRIBUTE request.");
	    }

	    RelayJMXUtils.setJMXConnectionContextImpl(_ctxAttrs);
	    
	    try {
		    getMBeanServerConnection().setAttribute(
		    		(ObjectName)params[0],
		    		(Attribute)unwrapWithDefault(
		    		      params[1],getClassLoaderFor((ObjectName)params[0])));
    	    return null;
	    }
	    finally {
		    RelayJMXUtils.unsetJMXConnectionContextImpl();
	    }

	case MBeanServerRequestMessage.SET_ATTRIBUTES:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a SET_ATTRIBUTES request.");
	    }

	    RelayJMXUtils.setJMXConnectionContextImpl(_ctxAttrs);
	    
	    try {
		    return getMBeanServerConnection().setAttributes(
		    		(ObjectName)params[0],
		    		(AttributeList)unwrapWithDefault(
		    		      params[1],getClassLoaderFor((ObjectName)params[0])));
	    }
	    finally {
		    RelayJMXUtils.unsetJMXConnectionContextImpl();
	    }

	case MBeanServerRequestMessage.UNREGISTER_MBEAN:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			     "Handle a UNREGISTER_MBEAN request.");
	    }

	    getMBeanServerConnection().unregisterMBean((ObjectName)params[0]);
	    return null;

	case MBeanServerRequestMessage.ADD_NOTIFICATION_LISTENER_OBJECTNAME:
	    if (logger.traceOn()) {
		final String reqname =
		    "ADD_NOTIFICATION_LISTENER_OBJECTNAME";
		logger.trace("handleRequest",
			     "Handle a " + reqname +" request.");
	    }

	    final ClassLoader cl1 = getClassLoaderFor((ObjectName)params[0]);
	    getMBeanServerConnection().addNotificationListener(
		(ObjectName)params[0],
		(ObjectName)params[1],
		(NotificationFilter)unwrapWithDefault(params[2], cl1),
		(Object)unwrapWithDefault(params[3], cl1));
	    return null;

	case MBeanServerRequestMessage.
	    REMOVE_NOTIFICATION_LISTENER_OBJECTNAME:
	    if (logger.traceOn()) {
		final String reqname =
		    "REMOVE_NOTIFICATION_LISTENER_OBJECTNAME";
		logger.trace("handleRequest",
			     "Handle a " + reqname +" request.");
	    }

	    getMBeanServerConnection().removeNotificationListener((ObjectName)params[0],
						   (ObjectName)params[1]);
	    return null;

	case MBeanServerRequestMessage.
	    REMOVE_NOTIFICATION_LISTENER_OBJECTNAME_FILTER_HANDBACK:
	    if (logger.traceOn()) {
		final String reqname =
		    "REMOVE_NOTIFICATION_LISTENER_OBJECTNAME_FILTER_HANDBACK";
		logger.trace("handleRequest",
			     "Handle a " + reqname + " request.");
	    }

	    final ClassLoader cl2 = getClassLoaderFor((ObjectName)params[0]);
	    getMBeanServerConnection().removeNotificationListener(
		(ObjectName)params[0],
		(ObjectName)params[1],
		(NotificationFilter)unwrapWithDefault(params[2], cl2),
		(Object)unwrapWithDefault(params[3], cl2));
	    return null;

	case MBeanServerRequestMessage.ADD_NOTIFICATION_LISTENERS:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a ADD_NOTIFICATION_LISTENERS request.");
	    }

	    if (isRI10) { // compatible to RI 10: bug 4948444
		final ObjectName name = ((ObjectName[])params[0])[0];
		final ClassLoader cl3 = getClassLoaderFor(name);
		final Object wrappedFilter = ((Object[])params[1])[0];
		return getServerNotifFwd().addNotificationListener(name,
			       (NotificationFilter)unwrapWithDefault(wrappedFilter, cl3));
	    }

	    if (params[0] == null || params[1] == null) {
		throw new IllegalArgumentException("Got null arguments.");
	    }

	    ObjectName[] names = (ObjectName[])params[0];
	    Object[] wrappedFilters = (Object[])params[1];

	    if (names.length != wrappedFilters.length) {

		throw new IllegalArgumentException(
			  "The value lengths of 2 parameters are not same.");
	    }

	    for (int i=0; i<names.length; i++) {
		if (names[i] == null) {
		    throw new IllegalArgumentException("Null Object name.");
		}
	    }

	    int i=0;
	    Integer[] ids = new Integer[names.length];
	    final boolean debug=logger.debugOn();
 
	    try {
		for (; i<names.length; i++) {
		    ClassLoader targetCl = getClassLoaderFor(names[i]);

		    if (debug) logger.debug("addNotificationListener"+
					"(ObjectName,NotificationFilter)",
					"connectionId=" + clientId +
			      " unwrapping filter with target extended ClassLoader.");

		    NotificationFilter filterValue = (NotificationFilter)unwrapWithDefault(
							      wrappedFilters[i], 
							      targetCl);

		    if (debug) logger.debug("addNotificationListener"+
					"(ObjectName,NotificationFilter)",
					"connectionId=" + clientId
					+", name=" + names[i]
					+", filter=" + filterValue);

		    ids[i] = getServerNotifFwd().addNotificationListener(
							           names[i],
								   filterValue);
		}
		
		return ids;
	    } catch (Exception e) {
		// remove all registered listeners
		for (int j=0; j<i; j++) {
		    try {
			getServerNotifFwd().removeNotificationListener(names[j], 
								       ids[j]);
		    } catch (Exception eee) {
			logger.warning("handleRequest-addNotificationListener",
				       "Failed to remove a listener from the MBean "
				       +names[j]+". "+eee.toString());
		    }
		}
		
		if (e instanceof PrivilegedActionException) {
		    e = extractException(e);
		}

		throw e;
	    }
	

	case MBeanServerRequestMessage.
	    REMOVE_NOTIFICATION_LISTENER_FILTER_HANDBACK:
	    if (logger.traceOn()) {
		final String reqname =
		    "REMOVE_NOTIFICATION_LISTENER_FILTER_HANDBACK";
		logger.trace("handleRequest",
			     "Handle a " + reqname + " request.");
	    }

	    getServerNotifFwd().removeNotificationListener(
		       (ObjectName)params[0],
		       (Integer[]) new Integer[] {(Integer)params[1]});
	    return null;

	case MBeanServerRequestMessage.REMOVE_NOTIFICATION_LISTENER:
	    if (logger.traceOn()) {
		logger.trace("handleRequest",
			   "Handle a REMOVE_NOTIFICATION_LISTENER request.");
	    }

	    getServerNotifFwd().removeNotificationListener(
						   (ObjectName)params[0],
						   (Integer[])params[1]);
	    return null;

	default:
	    // trace - log this as an info...
	    logger.info("handleRequest",
		       "Unknown request id: " + req.getMethodId());

	    throw new IllegalArgumentException("The specified method is not " +
					       "found [MethodId=" +
					       req.getMethodId() + "]");
	}
    }

    public void terminate() {
	terminate(false, "The server is stopped.");
    }

    public void terminate(boolean local, String msg) {
	if (logger.traceOn()) {
	    logger.trace("terminate", "Terminating....");
	}

	synchronized(stateLock) {
	    if (state == TERMINATED) {
		return;
	    }

	    state = TERMINATED;
	}

	// inform the remote side of termination
	//
	if (!local) {
	    if (logger.traceOn()) {
		logger.trace("terminate",
			     "Send a CloseMessage to the client.");
	    }

	    try {
		synchronized(connection) {
		    connection.sendOneWay(new CloseMessage(msg));
		}

	    } catch (UnsupportedOperationException uoe) {
		if (logger.traceOn()) {
		    logger.trace("terminate",
				 "The transport level does not support " +
				 "the method sendOneWay: " + uoe);
		}
	    } catch (IOException ioe) {
		// We haven't been able to inform the client that we're
		// closing.
		//
		logger.warning("terminate",
			       "Failed to inform the client: " + ioe);
		logger.debug("terminate",ioe);
	    }
	}

	// stopping listening
	//
	if (serverNotifForwarder != null) {
	    serverNotifForwarder.terminate();
	}

	// close the transport protocol
	//
	try {
	    connection.close();
	} catch (Exception ce) {
	    // OK.
	    // We are closing, so ignore it.
	}

	if (serverCommunicatorAdmin != null) {
	    serverCommunicatorAdmin.terminate();
	}

	//	add by maming 2012.10.02
	//	try to release notif buffer's resource
	//	before remote jmxc close
	if ( relayNotifBuffer != null ) {
		relayNotifBuffer.dispose();
	}
	
	//	add by maming 2012.08.23
	//	when in relay mode, try to close relay jmxc
	if ( relayJMXConnector != null ) {
		try {
			relayJMXConnector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// inform the server of termination
	//
	myServer.clientClosing(this, clientId,
			       "The method terminate is called.", null);

	if (logger.traceOn()) {
	    logger.trace("terminate", "Terminated.");
	}
    }

    ServerSynchroMessageConnection getTransport() {
	return connection;
    }

    // ---------------------------------------
    // private classes
    // ---------------------------------------

    /**
     * Read a message and distribute to a thread for execution
     */
    private class RequestHandler implements SynchroCallback {
	public Message execute(final Message msg) {
	    final boolean terminated = 
		serverCommunicatorAdmin.reqIncoming();
	    try {
		if (terminated)
		    throw new ConnectionClosedException(
			"The connection is being terminated by the server");

		if (logger.traceOn()) {
		    logger.trace("RequestHandler-execute",
				 "Execute the request: " + msg);
		}
		if (msg instanceof CloseMessage)
		    return handleCloseMessage((CloseMessage) msg);
		else if (msg instanceof NotificationRequestMessage)
		    return handleNotifReqMessage((NotificationRequestMessage)
						 msg);
		else if (msg instanceof MBeanServerRequestMessage)
		    return handleMBSReqMessage((MBeanServerRequestMessage) 
					       msg);
		// add by maming impl relay
		else if (msg instanceof RelayMessage)
		    return handleRelayMessage((RelayMessage) msg);
		else {
		    // We can recover from this, so this should rather
		    // be a warning...
		    //
		    logger.warning("RequestHandler-execute",
				   "Got unknown message: " + msg);
		    
		    // send a notif
		    //
		    myServer.failedConnectionNotif(clientId,
						   "Got unknown message: " +
						   msg,
						   msg);
		    
		    ServerIntermediary.this.
			terminate(false,"Got unknown message: "+msg);
		}
	    } catch (IOException cce) {
		logger.trace("RequestHandler.execute", cce);
		// Do nothing here, the connection should be closed soon.
	    } finally {
		serverCommunicatorAdmin.rspOutgoing();
	    }

	    return null;
	}

	private Message handleRelayMessage(RelayMessage msg) {
		System.out.println("thread:" + Thread.currentThread().getId() + ", begin handle relay msg");
				
		RelayResultMessage result = null;
		String	relayFailedReason = null;
		String url = msg.getRelayURL();
		//	属性 "jmx.remote.x.client.connection.check.period"
		//		用以标识 检查连接是否存在的时间间隔 毫秒
		Map<String, Object> relayEnv = new HashMap<String, Object>();
		relayEnv.put("jmx.remote.x.client.connection.check.period", 1000);
		
		try {
			//	判断url 是否指向本地
			//	local:  表示指向本地
			//	否则 继续传递
			if ( !url.startsWith("local:")) {
				relayJMXConnector = JMXConnectorFactory.connect(
						new JMXServiceURL("service:jmx:" + url), relayEnv);
				relayMBeanServerConnection = 
					relayJMXConnector.getMBeanServerConnection();
			}
			else {
				//	deal as local node
				//	获取本地MBeanServer
				//	JMXConnector jmxc = lvm.genJMXConnector(env);
				//	..../local:name=<path of program>,*
				final String localName = url.substring(6);	// local:
				final ObjectName[] ons = 
						mbeanServer.queryNames( ObjectName.getInstance( 
								"org.jocean:type=localnode," + localName ), null).toArray(new ObjectName[0]);
				if ( ons.length == 0 ) {
					relayFailedReason = "Can't found any localnode matched [" + localName + "].";
				}
				else if ( ons.length > 1 ) {
					relayFailedReason = "Found " + ons.length +" localnode matched [" + localName + "]:"
							+ Arrays.asList( ons ).toString();
				}
				else {
					relayJMXConnector = (JMXConnector)mbeanServer.invoke(
							ons[0],  "genJMXConnector", 
							new Object[]{relayEnv},  new String[]{"java.util.Map"});
					if ( null != relayJMXConnector ) {
						//	如果  lvm 对应的进程已经结束
						//	则 genJMXConnector 返回为null
						relayMBeanServerConnection = 
							relayJMXConnector.getMBeanServerConnection();
					}
					else {
						relayFailedReason = ons[0] + " is dead.";
					}
				}
			}
			if ( null != relayJMXConnector ) {
				relayJMXConnector.addConnectionNotificationListener(
						new NotificationListener() {
	
							@Override
							public void handleNotification(Notification notification,
									Object handback) {
								if ( notification instanceof JMXConnectionNotification) {
									JMXConnectionNotification jmxnotif = 
										(JMXConnectionNotification)notification;
									if ( jmxnotif.getType().equals(JMXConnectionNotification.CLOSED)
										|| jmxnotif.getType().equals(JMXConnectionNotification.FAILED) ) {
										ServerIntermediary.this.terminate(false, "relay jmxc closed or failed.");
									}
								}
							}}, null, null);
				System.out.println("thread:" + Thread.currentThread().getId() + 
						", now redirect mbsc is " + relayMBeanServerConnection 
						+ ", replace previous");
			}
			
		} catch (InstanceNotFoundException e) {
			e.printStackTrace();
			relayFailedReason = "InstanceNotFoundException:" + e.getMessage();
		} catch (MalformedObjectNameException e) {
			e.printStackTrace();
			relayFailedReason = "MalformedObjectNameException:" + e.getMessage();
		} catch (ReflectionException e) {
			e.printStackTrace();
			relayFailedReason = e.getMessage();
		} catch (MBeanException e) {
			e.printStackTrace();
			relayFailedReason = e.getMessage();
		} catch (NullPointerException e) {
			e.printStackTrace();
			relayFailedReason = e.getMessage();
		} catch (IOException e) {
			e.printStackTrace();
			relayFailedReason = e.getMessage();
		} catch (Exception e) {
			e.printStackTrace();
			relayFailedReason = e.getMessage();
		}
		finally {
			result = new RelayResultMessage( 
					null != relayMBeanServerConnection ? 0 : -1,
					relayFailedReason);
		}
		
		return result;
	}

	private Message handleCloseMessage(CloseMessage msg) {
	    if (logger.traceOn()) {
		logger.trace("RequestHandler-execute",
			     "Receive a CloseMessage.");
	    }

	    ServerIntermediary.this.terminate(true, null);

	    return null;
	}

	private Message handleNotifReqMessage(NotificationRequestMessage nr)
		throws IOException {
	    if (logger.traceOn()) {
		logger.trace("RequestHandler-execute",
			   "Receive a NotificationRequestMessage.");
	    }

	    long start = nr.getClientSequenceNumber();
	    long timeout = nr.getTimeout();
	    int max = nr.getMaxNotifications();
	    NotificationResult result =
		getServerNotifFwd().fetchNotifs(start, timeout, max);
	    Object wrapped;
	    try {
		wrapped = serialization.wrap(result);
	    } catch (NotSerializableException e) {
		/* This presumably means that at least one of the
		   notifications is unserializable, for example
		   because it contains an unserializable object in
		   its userData.  This should not happen often
		   (Notification itself is serializable).  */
		result = purgeUnserializable(result);
		wrapped = serialization.wrap(result);
		/* If even this call to serialization.wrap gets an
		   exception, then the ObjectWrapping is broken
		   and we can't reasonably recover.  So propagate
		   the exception.  We are inside a
		   catch(IOException) that will swallow the
		   IOException, but at least it will trace it if
		   asked.  */
	    }
	    return new NotificationResponseMessage(wrapped);
	}

	private Message handleMBSReqMessage(MBeanServerRequestMessage req)
		throws IOException {
	    if (logger.traceOn()) {
		logger.trace("RequestHandler-execute",
			     "Receive a MBeanServerRequestMessage.");
	    }
	    try {
		final AccessControlContext reqACC;
		final Subject delegationSubject = req.getDelegationSubject();
		if (delegationSubject == null)
		    reqACC = acc;
		else {
		    if (subject == null) {
			final String msg =
			    "Subject delegation cannot be enabled " +
			    "unless an authenticated subject " +
			    "is put in place";
			throw new SecurityException(msg);
		    }
		    reqACC =
			subjectDelegator.delegatedContext(acc,
							  delegationSubject);
		}

		Object result =
		    AccessController.doPrivileged(new PrivilegedRequestJob(req),
						  reqACC);
		return new MBeanServerResponseMessage(
				     req.getMessageId(),
				     result,
				     false);
	    } catch (Exception e) {
		e = extractException(e);
		if (logger.traceOn()) {
		    logger.trace("RequestHandler-execute",
				 "Got an exception: " + e,
				 e);
		}
		return new MBeanServerResponseMessage(
				      req.getMessageId(),
				      wrapException(e),
				      true);
	    } catch (Error r) {
		if (logger.traceOn()) {
		    logger.trace("RequestHandler-execute",
				 "Got an error: " + r,
				 r);
		}
		final JMXServerErrorException see =
		    new JMXServerErrorException(r.toString(),r);
		return new MBeanServerResponseMessage(
				      req.getMessageId(),
				      wrapException(see),
				      true);
	    }
	}

	/* Handle the case where the exception to be returned to the
	 * client is not serializable.  This is pretty unlikely, but
	 * if it does happen then we'll end up returning nothing at
	 * all to the client so it will get out of sync in the message
	 * exchange.
	 *
	 * The ObjectWrapping had better be able to serialize a
	 * NotSerializableException.
	 */
	private Object wrapException(Exception e) throws IOException {
	    try {
		return serialization.wrap(e);
	    } catch (NotSerializableException nse) {
		return serialization.wrap(nse);
	    }
	}

	public void connectionException(Exception e) {
	    synchronized(stateLock) {
		if (state == RUNNING) {
		    state = FAILED;
		} else {
		    return;
		}
	    }

	    if (e instanceof EOFException) {
		/* This is the usual case when the client exits abruptly
		   or forgets to close the connection before exiting, so
		   log a more comprehensible warning.  (5003051)  */
		logger.warning("RequestHandler-connectionException",
			       "JMX connector client exited without " +
			       "closing connection");
	    } else {
		logger.warning("RequestHandler-connectionException",
			       "JMX connector transport got exception " +
			       "when reading input message: " + e);
	    }

	    logger.finer("RequestHandler-connectionException", e);

	    // send a notif
	    //
	    String msg = null;
	    if (e instanceof ClassNotFoundException) {
		msg = "The client " + clientId +
		    "got an unknown message: " + e;
	    } else {
		msg = "The client " + clientId + " has failed: " + e;
	    }

	    myServer.failedConnectionNotif(clientId, msg, e);

	    ServerIntermediary.this.terminate(true, null);
	}
    }

    /* The following stuff handles notifications that are not
       serializable.  This can happen, for example, if the userData of
       a notification is an unserializable object.  This is probably
       uncommon as a developer error, but when it happens we don't
       want the whole notification subsystem to collapse.  We also
       don't want to lose other notifications that are in the same
       NotificationResult and that are serializable.

       Ideally we would have some way to signal unambiguously to the
       remote client that unserializable notifications have been lost.
       However, that turns out to be difficult.  Here are some
       possible approaches:

       - Replace the unserializable notifications by instances of a
       class that is known only to a private class loader.  Then the
       client will see them as instances of an unknown class, and the
       logic it should already have for dealing with that should flag
       lost notifications (though it won't indicate why they were
       lost).  The problem is that we can't expect an arbitrary
       ObjectWrapping to be able to serialize this private class.

       - A special Notification subclass that indicates there were
       unserializable notifications.  Unfortunately the spec doesn't
       define one.  If we invent an implementation-specific one, then
       we again can't expect an arbitrary ObjectWrapping to be able to
       handle it.

       - Tweak the returned NotificationResult so that notifications
       are seen to have been lost.  If we had specified a way to do
       this explicitly (e.g., if elements of the
       TargetedNotification[] are null or are of a special class) then
       this would have been straightforward.  Since we did not, the
       only possibility would be to return an earliestSequenceNumber
       that is greater than the starting sequence number given by the
       client.  This would mean that the earliestSequenceNumber would
       subsequently go backwards.  We don't know what the client does
       with earliestSequenceNumber so even though our implementation
       would not be adversely affected it seems dangerous to impose
       this on other clients.

       What we do in fact is to return a
       JMXConnectionNotification.NOTIFS_LOST in place of each
       unserializable notification.  This is ambiguous (there could
       have been a real notification of this kind) but we are making a
       best effort to recover from a programming error in user code,
       so it is acceptable.  It is still not guaranteed that an
       arbitrary ObjectWrapping can serialize this notification (which
       is not usually sent by user MBeans) so if serialization of the
       NotificationResult still fails then we simply drop the
       offending notification.
     */

    /* Replace unserializable notifications in the NotificationResult by
       JMXConnectionNotification.NOTIFS_LOST.  */
    private NotificationResult purgeUnserializable(NotificationResult nr) {
	List tnList = new ArrayList();
	TargetedNotification[] tns = nr.getTargetedNotifications();

	/* For each TargetedNotification (TN), try putting it on its
	   own inside a NotificationResult (NR) and see if we can
	   serialize.  If so, assume the TN can also be serialized
	   when combined with other TNs.  If not, replace it with a
	   NOTIFS_LOST.  We serialize a whole NR rather than just a
	   single TN, because an arbitrary serializer might not know
	   how to serialize a single TN (for example, it's an XML
	   serializer that can serialize an NR containing a TN but not
	   a TN on its own).  */
	for (int i = 0; i < tns.length; i++) {
	    TargetedNotification tn = tns[i];
	    NotificationResult trialnr =
		new NotificationResult(0, 0, new TargetedNotification[] {tn});
	    try {
		serialization.wrap(trialnr);
		tnList.add(tn);
	    } catch (IOException e) {
		logger.warning("purgeUnserializable",
			       "cannot serialize notif: " + tn);
		logger.fine("purgeUnserializable", e);
		final Integer listenerID = tn.getListenerID();
		final Notification badNotif = tn.getNotification();
		final String notifType = JMXConnectionNotification.NOTIFS_LOST;
		final String notifMessage = "Not serializable: " + badNotif;
		Notification goodNotif =
		    new JMXConnectionNotification(notifType,
						  badNotif.getSource(),
						  clientId,
						  badNotif.getSequenceNumber(),
						  notifMessage,
						  ONE_LONG);
		/* Our implementation has the convention that a NOTIFS_LOST
		   has a userData that says how many notifs were lost.  */
		tn = new TargetedNotification(goodNotif, listenerID);
		trialnr =
		    new NotificationResult(0, 0,
					   new TargetedNotification[] {tn});
		try {
		    serialization.wrap(trialnr);
		    tnList.add(tn);
		} catch (IOException e1) {
		    // OK.
		    // too bad, at least we have logged it
		}
	    }
	}

	tns = (TargetedNotification[])
	    tnList.toArray(new TargetedNotification[0]);

	return new NotificationResult(nr.getEarliestSequenceNumber(),
				      nr.getNextSequenceNumber(),
				      tns);
    }

    private static final Long ONE_LONG = new Long(1);

    private class GenericServerCommunicatorAdmin
	extends ServerCommunicatorAdmin {
	public GenericServerCommunicatorAdmin(long timeout) {
	    super(timeout);
	}

	protected void doStop() {
	    ServerIntermediary.this.terminate();
	}
	
    }


    private class PrivilegedRequestJob implements PrivilegedExceptionAction {
	public PrivilegedRequestJob(MBeanServerRequestMessage request) {
	    this.request = request;
	}

	public Object run() throws Exception {
	    return serialization.wrap(handleRequest(request));
	}

	private MBeanServerRequestMessage request;
    }


    // ---------------------------------------
    // private methods
    // ---------------------------------------
    // called by GenericConnectorServer, make sure that this object
    // will be started only when we finish its creation.
    void start() {
	this.connection.setCallback(requestHandler);
    }

    private ClassLoaderRepository getClassLoaderRepository() {
	return (ClassLoaderRepository)
	    AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			return mbeanServer.getClassLoaderRepository();
		    }
		});
    }

    private ClassLoader getClassLoader(final ObjectName name)
	throws InstanceNotFoundException {
	try {
	    return (ClassLoader)
		AccessController.doPrivileged(new PrivilegedExceptionAction() {
			public Object run() throws InstanceNotFoundException {
				//	add by maming 2012.10.01 
				//	如果是通过mbsc 转发的，则无法直接 获取 以 objectName注册在远端MBeanServer上的 指定ClassLoader
				//	则 按如下思路实现：直接翻或defaultClassLoader
				if ( null != relayMBeanServerConnection ) {
					return	defaultClassLoader;
				}
			    return mbeanServer.getClassLoader(name);
			}
		    });
	} catch (PrivilegedActionException pe) {
	    throw (InstanceNotFoundException) extractException(pe);
	}
    }

    private ClassLoader getClassLoaderFor(final ObjectName name)
	throws InstanceNotFoundException {
	try {
	    return (ClassLoader)
		AccessController.doPrivileged(new PrivilegedExceptionAction() {
			public Object run() throws InstanceNotFoundException {
				//	add by maming 2012.10.01 
				//	如果是通过mbsc 转发的，则无法直接通过objectName 获取其在远端的ClassLoader
				//	则 按如下思路实现：
				//	通过 获取到 objectName 在远端的ClassName，在代理端尝试获取其ClassLoader
				if ( null != relayMBeanServerConnection ) {
					try {
						final MBeanInfo info = relayMBeanServerConnection.getMBeanInfo(name);
						if ( null == info ) {
							throw new InstanceNotFoundException(name.toString());
						}
						return Class.forName( info.getClassName() ).getClassLoader();
					} catch (Exception e) {
						// throw new RuntimeException(e);
					}
					return	defaultClassLoader;
				}
				//	add by maming 2012.10.01 end
			    return mbeanServer.getClassLoaderFor(name);
			}
		    });
	} catch (PrivilegedActionException pe) {
	    throw (InstanceNotFoundException) extractException(pe);
	}
    }

    /**
     * Iterate until we extract the real exception
     * from a stack of PrivilegedActionExceptions.
     */
    private Exception extractException(Exception e) {
	while (e instanceof PrivilegedActionException) {
	    e = ((PrivilegedActionException)e).getException();
	}
	return e;
    }

    private Object unwrapWithDefault(final Object obj, final ClassLoader cl)
	throws IOException, ClassNotFoundException {
	try {
	    return AccessController.doPrivileged(
		   new PrivilegedExceptionAction() {
		       public Object run()
			   throws IOException, ClassNotFoundException {
			   return serialization.unwrap(
			       obj,
			       new OrderClassLoaders(cl, defaultClassLoader));
		       }
		   });
	} catch (PrivilegedActionException pe) {
	    Exception e = extractException(pe);
	    if (e instanceof IOException) {
		throw (IOException) e;
	    }
	    if (e instanceof ClassNotFoundException) {
		throw (ClassNotFoundException) e;
	    }
	}
	return null;
    }

    private MBeanServerConnection getMBeanServerConnection() {
    	if ( null != relayMBeanServerConnection ) {
//    		System.out.println("getMBeanServerConnection: using remote relayMBeanServerConnection");
    		return	relayMBeanServerConnection;
    	}
    	else {
//    		System.out.println("getMBeanServerConnection: using local mbeanServer");
    		return	mbeanServer;
    	}
    }
    
    // ---------------------------------------
    // private variables
    // ---------------------------------------

    private final MBeanServer mbeanServer;
    private JMXConnector relayJMXConnector = null;
    private MBeanServerConnection relayMBeanServerConnection = null;
    
    private final CommonConnectorServer myServer;
    private final ServerSynchroMessageConnection connection;
    private final String clientId;

    private final RequestHandler requestHandler = new RequestHandler();

    private final ObjectWrapping serialization;
    private final AccessControlContext acc;
    private final Subject subject;
    private final SubjectDelegator subjectDelegator;
    private final ClassLoader defaultClassLoader;
    private final ClassLoaderWithRepository clr;

    private NotificationBuffer	relayNotifBuffer = null;
    private ServerNotifForwarder serverNotifForwarder;
    private Map env;

    private GenericServerCommunicatorAdmin serverCommunicatorAdmin;

    private static final ClassLogger logger =
	new ClassLogger("org.jocean.jmx.relay.server",
			"ServerIntermediary");

    private static final int RUNNING = 0;
    private static final int FAILED = 1;
    private static final int TERMINATED = 2;
    private int state = RUNNING;

    private final int[] stateLock = new int[0];

    // compatible to RI 10: bug 4948444
    private final boolean isRI10;
    
    //	add by maming 2012.10.08 for impl JMXUtils.currentJMXConnectionContext
    private final Map<String, Object> _ctxAttrs = new HashMap<String, Object>();
}
