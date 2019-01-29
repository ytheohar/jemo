/*
********************************************************************************
* Copyright (c) 9th November 2018 Cloudreach Limited Europe
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* This Source Code may also be made available under the following Secondary
* Licenses when the conditions for such availability set forth in the Eclipse
* Public License, v. 2.0 are satisfied: GNU General Public License, version 2
* with the GNU Classpath Exception which is
* available at https://www.gnu.org/software/classpath/license.html.
*
* SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
********************************************************************************/
package com.cloudreach.connect.x2.sys;

import com.cloudreach.connect.x2.CC;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.internal.model.CloudProvider;
import static com.cloudreach.connect.x2.sys.CCPluginManager.VHOST_KEY;
import com.cloudreach.connect.x2.sys.auth.CCUser;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class CCVirtualHostManager {
	
	public static final String EVENT_RELOAD = "VHOST_RELOAD";
	
	private static class CCVirtualHost {
		private String modulePath = null;
		private String virtualHost = null;

		@JsonProperty(value = "modulePath")
		public String getModulePath() {
			return modulePath;
		}

		@JsonProperty(value = "modulePath")
		public void setModulePath(String modulePath) {
			this.modulePath = modulePath;
		}

		@JsonProperty(value = "virtualHost")
		public String getVirtualHost() {
			return virtualHost;
		}

		@JsonProperty(value = "virtualHost")
		public void setVirtualHost(String virtualHost) {
			this.virtualHost = virtualHost;
		}
	}
	
	public static void processRequest(CCUser user,HttpServletRequest request,HttpServletResponse response) throws IOException {
		//to be able to create virtual hosts the user must be an administrator of the platform.
		if(user != null && user.isAdmin()) {
			switch(request.getMethod()) {
				case "PUT":
					PUT(user, request, response);
					break;
				case "GET":
					GET(user,request,response);
					break;
				case "DELETE":
					DELETE(user,request,response);
					break;
				default:
					response.sendError(404);
			}
		} else {
			response.sendError(403);
		}
	}
	
	private static void DELETE(CCUser user,HttpServletRequest request,HttpServletResponse response) throws IOException {
		String item = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/'));
		if(item.equals("/") || request.getRequestURI().endsWith("/x2manager/virtualhosts")) {
			response.sendError(400); //bad request
		} else {
			final String fItem = item.startsWith("//") || !item.startsWith("/") ? item : item.substring(1);
			Map<String,String> deletedDefinitions = getVirtualHostDefinitions().entrySet().stream().filter(e -> e.getKey().startsWith(fItem) || e.getKey().startsWith("//"+fItem)).collect(Collectors.toMap((t) -> t.getKey(), (t) -> t.getValue()));
			if(deletedDefinitions.isEmpty()) {
				response.sendError(404); //not found
			} else {
				Map<String,String> newDefinitions = getVirtualHostDefinitions().entrySet().stream().filter(e -> !deletedDefinitions.containsKey(e.getKey())).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
				CloudProvider.getInstance().getRuntime().store(CCPluginManager.VHOST_KEY, newDefinitions);
				response.setContentType("application/json");
				OutputStream out = response.getOutputStream();
				out.write(CC.toJSONString(deletedDefinitions).getBytes("UTF-8"));
				out.flush();
				out.close();
				
				notifyReload();
			}
		}
	}
	
	/**
	 * this method will create a new virtual host, it expects a json which contains 2 parameters
	 * {
	 *	 "modulePath" : "/pluginId/version/module_path",
	 *	 "virtualHost" : "//www.mydomain.com"
	 * }
	 * 
	 * @param user
	 * @param request
	 * @param response
	 * @throws IOException 
	 */
	private static void PUT(CCUser user,HttpServletRequest request,HttpServletResponse response) throws IOException {
		//this will create a new virtual host
		CCVirtualHost vhost = CC.fromJSONString(CCVirtualHost.class, CC.toString(request.getInputStream()));
		if(vhost != null) {
			Map<String,String> currentDefinitions = getVirtualHostDefinitions();
			if(currentDefinitions == null) {
				currentDefinitions = new HashMap<>();
			}
			currentDefinitions.put(vhost.getVirtualHost(), vhost.getModulePath());
			CloudProvider.getInstance().getRuntime().store(CCPluginManager.VHOST_KEY, currentDefinitions);
			
			//we should now notify the system that the definitions need to be reloaded.
			notifyReload();
		} else {
			response.sendError(400);
		}
	}
	
	private static void GET(CCUser user,HttpServletRequest request,HttpServletResponse response) throws IOException {
		//this will return a list of all the virtual hosts which have been registered for this platform.
		String item = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/'));
		if(item.equals("/") || request.getRequestURI().endsWith("/x2manager/virtualhosts")) {
			item = "";
		} else {
			item = item.substring(1);
		}
		response.setContentType("application/json");
		OutputStream out = response.getOutputStream();
		if(item.isEmpty()) {
			//list all the virtual hosts
			out.write(CC.toJSONString(getVirtualHostDefinitions()).getBytes("UTF-8"));
		} else {
			//return items that start with //{item}
			final String fItem = item.startsWith("//") || !item.startsWith("/") ? item : item.substring(1);
			out.write(CC.toJSONString(getVirtualHostDefinitions().entrySet().stream().filter(e -> e.getKey().startsWith(fItem) || e.getKey().startsWith("//"+fItem)).collect(Collectors.toMap((t) -> t.getKey(), (t) -> t.getValue()))).getBytes("UTF-8"));
		}
		out.flush();
		out.close();
	}
	
	public static Map<String,String> getVirtualHostDefinitions() {
		return CloudProvider.getInstance().getRuntime().retrieve(VHOST_KEY, Map.class);
	}
	
	public static void notifyReload() throws JsonProcessingException {
		CCMessage msg = new CCMessage();
		msg.setModuleClass(CC.class.getName());
		msg.getAttributes().put(EVENT_RELOAD,"reload");
		msg.broadcast();
	}
}
