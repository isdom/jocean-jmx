/**
 * 
 */
package org.jocean.jmx.relay.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author isdom
 * 在 jocean-jmx 中不能直接依赖 jocean-util 
 * 所以 使用 RelayJMXUtils 以反射方式辅助实现 jocean-util 中的JMXConnectionContextProvider 接口，
 * 并同样以 代理方式实现 JMXConnectionContext 方法
 */
public class RelayJMXUtils {
	
	@SuppressWarnings("unchecked")
	public static <T> T newContextProvider() {
		if ( null != _jmxConnectionContextProviderCls ) {
			return (T)Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), 
					new Class<?>[]{_jmxConnectionContextProviderCls}, 
					new InvocationHandler() {

						@Override
						public Object invoke(Object proxy, Method method,
								Object[] args) throws Throwable {
							if ( (null == args || args.length == 0) 
									&& method.getName().equals("currentJMXConnectionContext") ) {
								return _ctxs.get(Thread.currentThread().getId());
							}
							throw new UnsupportedOperationException(method.getName());
						}});			
		}
		else {
			return	null;
		}
	}
	
	private static class CTXInvocationHandler implements InvocationHandler {
		CTXInvocationHandler(Map<String, Object> attrs) {
			this._attrs = attrs;
		}
		
		final Map<String, Object> _attrs;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			if ( null == args || args.length == 0 ) {
				return	_attrs.get(method.getName());
			}
			
			throw new UnsupportedOperationException(method.getName());
		}
	}
	
	public static void setJMXConnectionContextImpl(final Map<String, Object> ctxImpl) {
		if ( null != _jmxConnectionContextCls ) {
			_ctxs.put(Thread.currentThread().getId(), 
					Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), 
							new Class<?>[]{_jmxConnectionContextCls}, 
							new CTXInvocationHandler(ctxImpl)));
		}
	}
	
	public static void unsetJMXConnectionContextImpl() {
		_ctxs.remove(Thread.currentThread().getId());
	}
	
	private static final Class<?> _jmxConnectionContextCls;
	private static final Class<?> _jmxConnectionContextProviderCls;
	private static final Map<Long, Object>	_ctxs = new ConcurrentHashMap<Long, Object>();
	
	static {
		Class<?> cls = null, providercls = null;
		
		try {
			cls = Class.forName("org.jocean.util.JMXConnectionContext");
			providercls = Class.forName("org.jocean.util.JMXConnectionContextProvider");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		_jmxConnectionContextCls = cls;
		_jmxConnectionContextProviderCls = providercls;
	}
}
