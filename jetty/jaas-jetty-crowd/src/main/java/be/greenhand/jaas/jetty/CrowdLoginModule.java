/*
 * JAAS Jetty Crowd
 * Copyright (C) 2014 Issa Gorissen
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package be.greenhand.jaas.jetty;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.ws.rs.core.MediaType;

import org.eclipse.jetty.jaas.JAASPrincipal;
import org.eclipse.jetty.jaas.JAASRole;
import org.eclipse.jetty.jaas.callback.ObjectCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;

import be.greenhand.jaas.jetty.jaxb.AuthenticatePost;
import be.greenhand.jaas.jetty.jaxb.GroupResponse;
import be.greenhand.jaas.jetty.jaxb.GroupsResponse;
import be.greenhand.jaas.jetty.jaxb.UserResponse;

public class CrowdLoginModule implements LoginModule {
	private static final Logger LOG = LoggerFactory.getLogger(CrowdLoginModule.class);
	
	private static final String APP_NAME = "applicationName";
	private static final String APP_PASS = "applicationPassword";
	private static final String CROWD_SERVER_URL = "crowdServerUrl";
	
	/* in secs - default 5 */
	private static final String HTTP_PROXY_HOST = "httpProxyHost";
	private static final String HTTP_PROXY_PORT = "httpProxyPort";
	private static final String HTTP_PROXY_USER = "httpProxyUsername";
	private static final String HTTP_PROXY_PASS = "httpProxyPassword";
	
	/* default 20 */
	private static final String HTTP_MAX_CONNECTIONS = "httpMaxConnections";
	
	/* in millisecs - default 5000 */
	private static final String HTTP_TIMEOUT = "httpTimeout";
	
	
	
	private Subject subject;
	private CallbackHandler callbackHandler;
	private Map<String, ?> options;

	/* REST client */
	private Client client;
	private URI crowdServer;

	/* state machine */
	private boolean authenticated = false;
	private boolean commited = false;
	private UserResponse currentUser = null;
	private Principal userPrincipal = null;
	private Set<JAASRole> rolePrincipals = null;
	
	public CrowdLoginModule() {
	  LOG.info("Loaded Crowd client for JAAS");
	}

	/**
	 * @see javax.security.auth.spi.LoginModule#initialize(javax.security.auth.Subject, javax.security.auth.callback.CallbackHandler, java.util.Map, java.util.Map)
	 */
	public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
		this.subject = subject;
		this.callbackHandler = callbackHandler;
		this.options = options;
		
		try {
			restClientInit();
		} catch (URISyntaxException ue) {
			throw new RuntimeException("Problem with JAAS config for Crowd", ue);
		}
	}

	/**
	 * @see javax.security.auth.spi.LoginModule#login()
	 */
	public boolean login() throws LoginException {
		try {
			if (callbackHandler == null) {
				throw new LoginException("No callback handler");
			}
			
			Callback[] callbacks = configureCallbacks();
			callbackHandler.handle(callbacks);
			
			String username = ((NameCallback) callbacks[0]).getName();
			String password = (String) ((ObjectCallback) callbacks[1]).getObject();
			if (password == null) {
				char[] c = ((PasswordCallback) callbacks[2]).getPassword();
				if ( c != null) {
					password = new String(c);
				}
			}
			
			if (username == null || password == null) {
				authenticated = false;
			}
			
			authenticate(username, password);
			authenticated = true;
		} catch (Exception e) {
			LOG.error("login()", e);
			throw new FailedLoginException(e.getMessage());
		}

		return authenticated;
	}

	/**
	 * @see javax.security.auth.spi.LoginModule#commit()
	 */
	public boolean commit() throws LoginException {
		if (!authenticated) {
			resetStateData();
			return false;
		}
		
		try {
			// create Jetty JAASPrincipal
			userPrincipal = new JAASPrincipal(currentUser.name);
			
			// create Jetty JAASRole
			rolePrincipals = getUserGroups(currentUser.name);
			rolePrincipals.add(new JAASRole("user"));
			
			// update Subject
			subject.getPrincipals().add(userPrincipal);
			subject.getPrincipals().addAll(rolePrincipals);
			
			if (LOG.isDebugEnabled()) {
				LOG.debug(subject.toString());
			}
			
			commited = true;
			return commited;
		} catch (Exception e) {
			LOG.error("JAAS commit() failure", e);
			resetStateData();
			throw new LoginException(e.getMessage());
		}
	}

	/**
	 * @see javax.security.auth.spi.LoginModule#abort()
	 */
	public boolean abort() throws LoginException {
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("abort called - previous login state: [%b]", authenticated));
		}
		
		try {
			if (!authenticated) {
				return false;
			}

			resetStateData();
			
			return true;
		} catch (Exception e) {
			LOG.error("JAAS abort() failure", e);
			throw new LoginException(e.getMessage());
		}
	}

	/**
	 * @see javax.security.auth.spi.LoginModule#logout()
	 */
	public boolean logout() throws LoginException {
		// remove JAASRole from subject
		subject.getPrincipals().remove(userPrincipal);
		
		// remove JAASPrincipal from subject
		subject.getPrincipals().removeAll(rolePrincipals);
		
		// reset state variables
		resetStateData();
		
		return true;
	}
	
	/**
	 * Copied from https://github.com/eclipse/jetty.project/blob/jetty-9.4.11.v20180605/jetty-jaas/src/main/java/org/eclipse/jetty/jaas/spi/AbstractLoginModule.java
	 * @return
	 */
	private Callback[] configureCallbacks() {
		Callback[] callbacks = new Callback[3];
		callbacks[0] = new NameCallback("Enter user name");
		callbacks[1] = new ObjectCallback();
		callbacks[2] = new PasswordCallback("Enter password", false); //only used if framework does not support the ObjectCallback
		return callbacks;
	}
	
	private void resetStateData() {
		authenticated = false;
		commited = false;
		currentUser = null;
		userPrincipal = null;
		rolePrincipals = null;
	}
	
	
	/**
	 * Prepares the REST client
	 * @throws URISyntaxException 
	 */
	private void restClientInit() throws URISyntaxException {
		LOG.info("Attempting Crowd REST client config...");

		try {
			ClientConfig clientConfig = new DefaultClientConfig();
			clientConfig.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, getHttpTimeout());
			clientConfig.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, getHttpTimeout());
			clientConfig.getProperties().put(ClientConfig.PROPERTY_THREADPOOL_SIZE, getHttpMaxConnections());

			crowdServer = new URI(getCrowdServerUrl()).resolve("rest/usermanagement/1/");

			client = Client.create(clientConfig);

			client.addFilter(new ClientFilter() {
				/**
				 * Add Basic Auth for request sent to Crowd
				 */
				@Override
				public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
					cr.getHeaders().add("Authorization", "Basic " + base64Encode(getApplicationName(), getApplicationPassword()));

					// Call the next client handler in the filter chain
					ClientResponse resp = getNext().handle(cr);

					return resp;
				}
			});

			if (getHttpProxyHost().trim().length() > 0 && getHttpProxyPort() > 0) {
				System.setProperty("http.proxyHost", getHttpProxyHost());
				System.setProperty("http.proxyPort", Integer.toString(getHttpProxyPort()));
				System.setProperty("https.proxyHost", getHttpProxyHost());
				System.setProperty("https.proxyPort", Integer.toString(getHttpProxyPort()));

				if (getHttpProxyUsername() != null && getHttpProxyPassword() != null) {
					System.setProperty("http.proxyUser", getHttpProxyUsername());
					System.setProperty("http.proxyPassword", getHttpProxyPassword());
					System.setProperty("https.proxyUser", getHttpProxyUsername());
					System.setProperty("https.proxyPassword", getHttpProxyPassword());
				}
			}

			if (LOG.isDebugEnabled()) {
				LOG.debug("HTTP Client config");
				LOG.debug(getCrowdServerUrl());
				LOG.debug(crowdServer.toString());
				LOG.debug("PROPERTY_THREADPOOL_SIZE:" + clientConfig.getProperty(ClientConfig.PROPERTY_THREADPOOL_SIZE));
				LOG.debug("PROPERTY_READ_TIMEOUT:" + clientConfig.getProperty(ClientConfig.PROPERTY_READ_TIMEOUT));
				LOG.debug("PROPERTY_CONNECT_TIMEOUT:" + clientConfig.getProperty(ClientConfig.PROPERTY_CONNECT_TIMEOUT));
				LOG.debug("Crowd application name:" + getApplicationName());
			}


		} catch (Exception e) {
			LOG.error("Error occured during Crowd JAAS module init.", e);
			throw new RuntimeException(e);
		}
	}

	private String base64Encode(String user, String pass) {
		try {
			return Base64.getEncoder().encodeToString(new String(user + ":" + pass).getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Makes REST call toward Crowd to authenticate user
	 */
	private void authenticate(String username, String pass) throws UnsupportedEncodingException {
		if (LOG.isDebugEnabled()) LOG.debug("authentication attempt for '" + String.valueOf(username) + "'");
		
		WebResource r = client.resource(crowdServer.resolve("authentication?username=" + URLEncoder.encode(username, "UTF-8")));
		
		AuthenticatePost rBody = new AuthenticatePost();
		rBody.value = pass;
		UserResponse response = r.accept(MediaType.APPLICATION_XML_TYPE).post(UserResponse.class, rBody);
		
		if (LOG.isDebugEnabled()) LOG.debug(response.toString());
		
		LOG.info("authentication made for '" + String.valueOf(username) + "'");
		
		currentUser = response;
	}
	
	/**
	 * Makes REST call to Crowd to get user's groups
	 * @param username
	 * @return Set<JAASRole>
	 * @throws RemoteException
	 * @throws UnsupportedEncodingException
	 */
	private Set<JAASRole> getUserGroups(String username) throws RemoteException, UnsupportedEncodingException {
		if (LOG.isDebugEnabled()) LOG.debug("get groups for '" + String.valueOf(username) + "'");
		
		WebResource r = client.resource(crowdServer.resolve("user/group/nested?username=" + URLEncoder.encode(username, "UTF-8")));
		
		GroupsResponse response = r.get(GroupsResponse.class);
		if (LOG.isDebugEnabled()) LOG.debug(response.toString());
		
		Set<JAASRole> results = new HashSet<JAASRole>();
		for (GroupResponse group : response.group) {
			// check if group is active
			r = client.resource(crowdServer.resolve("group?groupname=" + URLEncoder.encode(group.name, "UTF-8")));
			GroupResponse groupResponse = r.get(GroupResponse.class);
			
			if (groupResponse.active) {
				results.add(new JAASRole(group.name));
			}
		}
		
		return results;
	}

	private Integer getHttpTimeout() {
		int defaultVal = 5000;
		Object object = options.get(HTTP_TIMEOUT);
		if (object != null) {
			String value = (String) object;
			try {
				return Integer.valueOf(value);
			} catch (NumberFormatException nfe) {
				LOG.warn("JAAS config for Crowd http timeout must be a number in millisecs");
				
				return new Integer(defaultVal);
			}
		}

		// default - 5000 msecs
		return new Integer(defaultVal);
	}
	
	private Integer getHttpMaxConnections() {
		int defaultVal = 20;
		
		Object object = options.get(HTTP_MAX_CONNECTIONS);
		if (object != null) {
			String value = (String) object;
			try {
				return Integer.valueOf(value);
			} catch (NumberFormatException nfe) {
				LOG.warn("JAAS config for Crowd http max connections must be a number");
				
				// default - 20
				return new Integer(defaultVal);
			}
		}

		// default - 20
		return new Integer(defaultVal);
	}
	
	private String getCrowdServerUrl() {
		Object object = options.get(CROWD_SERVER_URL);
		if (object != null) {
		    String baseUrl = (String) object;
		    if (!baseUrl.endsWith("/")) {
		        baseUrl += "/";
		    }
			return baseUrl;
			
		}

		throw new RuntimeException("JAAS config for Crowd is missing the crowd url which should look like https://a.domain.com/crowd/");
	}
	
	private String getApplicationName() {
		Object object = options.get(APP_NAME);
		if (object != null) {
			return (String) object;
			
		}

		throw new RuntimeException("JAAS config for Crowd is missing the crowd application name (app username in crowd)");
	}
	
	private String getApplicationPassword() {
		Object object = options.get(APP_PASS);
		if (object != null) {
			return (String) object;
			
		}

		throw new RuntimeException("JAAS config for Crowd is missing the crowd application password (app password in crowd)");
	}
	
	private String getHttpProxyHost() {
		Object object = options.get(HTTP_PROXY_HOST);
		if (object != null) {
			return (String) object;
			
		}

		return "";
	}
	
	private String getHttpProxyUsername() {
		return (String) options.get(HTTP_PROXY_USER);
	}
	
	private String getHttpProxyPassword() {
		return (String) options.get(HTTP_PROXY_PASS);
	}
	
	private int getHttpProxyPort() {
		Object object = options.get(HTTP_PROXY_PORT);
		if (object != null) {
			String value = (String) object;
			try {
				return Integer.valueOf(value).intValue();
			} catch (NumberFormatException nfe) {
				throw new RuntimeException("JAAS config for Crowd http proxy port must be a number", nfe);
			}
		}

		return -1;
	}
}
