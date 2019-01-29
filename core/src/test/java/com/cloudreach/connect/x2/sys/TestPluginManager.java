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
import com.cloudreach.connect.x2.api.KeyValue;
import com.cloudreach.connect.x2.api.Module;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.connect.x2.internal.model.CloudRuntime;
import com.cloudreach.connect.x2.internal.model.SystemDBObject;
import com.cloudreach.connect.x2.internal.model.X2ApplicationMetaData;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.cloudreach.connect.x2.sys.internal.Util;
import com.cloudreach.x2.HttpServletRequestAdapter;
import com.cloudreach.x2.HttpServletResponseAdapter;
import com.cloudreach.x2.X2GSMTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.Holder;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestPluginManager extends X2GSMTest {
	
	public TestPluginManager() throws Throwable { super(); }
	
	@Test
	public void testGetInstances() throws Throwable {
		Class pluginManagerClass = Class.forName("com.cloudreach.connect.x2.sys.CCPluginManager");
		Object pluginManager = pluginManagerClass.getMethod("getInstance").invoke(pluginManagerClass);
		List<String> instances = (List)pluginManagerClass.getMethod("listInstances", String.class).invoke(pluginManager, "AWS");
		Logger.getAnonymousLogger().info(String.format("instances: %s", instances));
		CloudRuntime runtime = CloudProvider.getInstance().getRuntime();
		
		CCPluginManager ccPluginManager = (CCPluginManager)pluginManager;
		ccPluginManager.runWithModuleContext(Void.class, x -> {
			Map<String,String> payload = new HashMap<>();
			CCMessage msg = new CCMessage();
			msg.setSourcePluginId(20);
			msg.setPluginId(20);
			msg.setModuleClass("com.cloudreach.connect.x2.connect.ConnectModule");
			/*msg.getAttributes().put("queue", getName()); //name of connect queue
			msg.getAttributes().put("class", m.getClass().getName()); //the connect class which recieves the queue (EventProcessor implementation)
			msg.getAttributes().put("pluginId", pluginId); //the id of the connect plugin.*/

			instances.forEach(i -> {
				try {
					Long whenWasILaunched = runtime.retrieve("metoffice_instance_run_"+i, Long.class);
					if(whenWasILaunched == null || System.currentTimeMillis()-whenWasILaunched > TimeUnit.HOURS.toMillis(2)) {
						runtime.store("metoffice_instance_run_"+i, System.currentTimeMillis());
						msg.getAttributes().put("payload", CC.toJSONString(payload)); //the data to send to connect.
						msg.send(runtime.getQueueId("CC-AWS-"+i));
					}
				}catch(Throwable ex) {
					throw new RuntimeException(ex);
				}
			});
			
			return null;
		});
	}
	
	@Test
	public void test_getLocationList() throws Throwable {
		//so for this test to pass we must have a queue id value for all of the locations in the list.
		Set<String> locations = x2server.getPluginManager().getLocationList();
		Set<String> queueIdList = locations.stream()
			.map(l -> CloudProvider.getInstance().getRuntime().getQueueId("CC-"+l+"-WORK-QUEUE"))
			.filter(qId -> qId != null)
			.collect(Collectors.toSet());
		
		assertEquals(locations.size(),queueIdList.size());
	}
	
	@Test
	public void test_getActiveLocationList() throws Throwable {
		Set<String> activeLocations = x2server.getPluginManager().getActiveLocationList();
		Set<String> queueIdList = activeLocations.stream()
			.map(l -> CloudProvider.getInstance().getRuntime().getQueueId("CC-"+l+"-WORK-QUEUE"))
			.filter(qId -> qId != null)
			.collect(Collectors.toSet());
		assertEquals(activeLocations.size(),queueIdList.size());
		
		//also each location must have at least 1 active instance.
		Set<String> activeInstances = activeLocations.stream()
			.filter(l -> !x2server.getPluginManager().listInstances(l).isEmpty())
			.collect(Collectors.toSet());
		assertEquals(activeLocations.size(),activeInstances.size());
	}
	
	@Test
	public void test_getInstanceLocations() throws Throwable {
		Set<String> instanceList = x2server.getPluginManager().getActiveInstanceList();
		assertArrayEquals(x2server.getPluginManager().getActiveLocationList().toArray(), x2server.getPluginManager().getInstanceLocations(instanceList.toArray(new String[] {})).toArray());
	}
	
	@Test
	public void test_getInstanceLocationMap() throws Throwable {
		assertEquals(locationList().stream()
			.flatMap(l -> instanceList(l).stream().map(i -> new KeyValue<>(i,l)))
			.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue)), x2server.getPluginManager().getInstanceLocationMap(x2server.getPluginManager().getActiveInstanceList().toArray(new String[] {})));
	}
	
	@Test
	public void test_MonitoringInterval_httpRequest() {
		CCPluginManager.MonitoringInterval interval = new CCPluginManager.MonitoringInterval("1M", 1, TimeUnit.MINUTES);
		interval.httpRequest(1000);
		assertEquals(1,interval.getHttpRequests());
		assertEquals(1000,interval.getTotalHttpTime());
	}
	
	@Test
	public void test_MonitoringInterval_checkInterval() {
		CCPluginManager.MonitoringInterval interval = new CCPluginManager.MonitoringInterval("1M", 1, TimeUnit.MINUTES);
		interval.httpRequest(1000);
		assertEquals(1,interval.getHttpRequests());
		assertEquals(1000,interval.getTotalHttpTime());
		interval.eventRequest(100);
		assertEquals(1,interval.getEventRequests());
		assertEquals(100,interval.getTotalEventTime());
		assertEquals("1M",interval.getKey());
		assertEquals(TimeUnit.MINUTES.toMillis(1), interval.getDuration());
		Util.setFieldValue(interval, "intervalStart", System.currentTimeMillis() - (interval.getDuration()+100));
		interval.eventRequest(100);
		assertEquals(1,interval.getEventRequests());
		assertEquals(100,interval.getTotalEventTime());
	}
	
	@Test
	public void test_ModuleInfoCache() {
		CCPluginManager.ModuleInfoCache cache = new CCPluginManager.ModuleInfoCache("TEST1",new HashSet<>());
		assertEquals("TEST1",cache.getLocation());
		long cachedOn = System.currentTimeMillis();
		Util.setFieldValue(cache, "cachedOn", cachedOn);
		assertEquals(cachedOn, cache.getCachedOn());
	}
	
	@Test
	public void test_listApplications() {
		//we need to set a mock cloud runtime up first
		List<X2ApplicationMetaData> origKnownApplications = new ArrayList<>();
		List<X2ApplicationMetaData> knownApplications = Util.getFieldValue(x2server.getPluginManager(),"KNOWN_APPLICATIONS",List.class);
		try {
			origKnownApplications.addAll(knownApplications);
			knownApplications.clear();
			CloudProvider.defineCustomeRuntime(new MockRuntime() {
				List<SystemDBObject> objList = new ArrayList<>();
				
				@Override
				public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
					return objList.stream().map(obj -> objectType.cast(obj)).collect(Collectors.toList());
				}

				@Override
				public Set<String> listModules() {
					return Arrays.asList("10_Test-1-1.0.jar").stream().collect(Collectors.toSet());
				}

				@Override
				public boolean hasNoSQLTable(String tableName) {
					return true;
				}

				@Override
				public Long getModuleInstallDate(String moduleJar) throws IOException {
					return System.currentTimeMillis();
				}

				@Override
				public void saveNoSQL(String tableName, SystemDBObject... data) {
					objList.addAll(Arrays.asList(data));
				}
			});
			x2server.getPluginManager().listApplications();
			assertEquals(1,knownApplications.size());
			X2ApplicationMetaData resultMetadata = knownApplications.iterator().next();
			assertNotNull(resultMetadata);
			X2ApplicationMetaData targetMetadata = new X2ApplicationMetaData();
			targetMetadata.setId("10_Test-1-1.0.jar");
			assertEquals(targetMetadata.getId(),resultMetadata.getId());
		}finally {
			CloudProvider.defineCustomeRuntime(null);
			knownApplications.clear();
			knownApplications.addAll(origKnownApplications);
		}
	}
	
	@Test
	public void test_getVirtualHostMap() {
		try {
			CloudProvider.defineCustomeRuntime(new MockRuntime() {
				@Override
				public <T> T retrieve(String key, Class<T> objType) {
					Map<String,String> currentDefinitions = new HashMap<>();
					currentDefinitions.put("www.google.com","/3/v1.0/google");
					return objType.cast(currentDefinitions);
				}
			});
			assertNotNull(x2server.getPluginManager().getVirtualHostMap());
			x2server.getPluginManager().loadVirtualHostDefinitions();
			assertFalse(x2server.getPluginManager().getVirtualHostMap().isEmpty());
			assertTrue(x2server.getPluginManager().getVirtualHostMap().containsKey("www.google.com"));
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
	
	@Test
	public void test_getMonitoringInterval() {
		assertNotNull(x2server.getPluginManager().getMonitoringInterval("5M"));
	}
	
	@Test
	public void test_listMonitoringIntervals() {
		x2server.getPluginManager().listMonitoringIntervals().forEach(interval -> assertNotNull(x2server.getPluginManager().getMonitoringInterval(interval)));
	}
	
	@Test
	public void test_PLUGIN_INSTALLED_ON() {
		try {
			CloudProvider.defineCustomeRuntime(new MockRuntime() {
				@Override
				public Long getModuleInstallDate(String moduleJar) throws IOException {
					throw new IOException(moduleJar);
				}
			});
			long now = System.currentTimeMillis();
			long installedOn = CCPluginManager.PLUGIN_INSTALLED_ON("10_test-1-1.0.jar");
			assertTrue(installedOn >= now);
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
	
	@Test
	public void test_MEMORY_CHECK() {
		assertTrue(CCPluginManager.MEMORY_CHECK() > 0);
		assertTrue(CCPluginManager.garbageCollectIfNecessary(0));
		assertFalse(CCPluginManager.garbageCollectIfNecessary(150000));
	}
	
	public static class TestModule implements Module {

		@Override
		public void construct(Logger logger, String name, int id, double version) {}
		
	}
	
	@Test
	public void test_MODULE_LIST() throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, TestModule.class, TestPluginManager.class);
		byte[] jarBytes = byteOut.toByteArray();
		try(X2ClassLoader clsLoader = new X2ClassLoader(UUID.randomUUID().toString(), jarBytes)) {
			List<String> moduleList = x2server.getPluginManager().MODULE_LIST("10_Test-1-1.0.jar", jarBytes, clsLoader);
			assertTrue(moduleList.contains(TestModule.class.getName()));
		}
		assertTrue(x2server.getPluginManager().MODULE_LIST("100_Test-1-1.0.jar", null, null).isEmpty());
		CloudProvider.getInstance().getRuntime().uploadModule("101_Test-1-1.0.jar", jarBytes);
		List<String> moduleList = x2server.getPluginManager().MODULE_LIST("101_Test-1-1.0.jar");
		assertTrue(moduleList.contains(TestModule.class.getName()));
		try {
			CloudProvider.defineCustomeRuntime(new MockRuntime() {});
			assertTrue(x2server.getPluginManager().MODULE_LIST("102_Test-1-1.0.jar").isEmpty());
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
	
	@Test
	public void test_listPlugins() {
		List<X2ApplicationMetaData> appList = Util.getFieldValue(x2server.getPluginManager(), "APPLICATION_LIST", List.class);
		assertEquals(appList.size(), x2server.getPluginManager().listPlugins().size());
	}
	
	public static class TestWebModule implements Module {

		@Override
		public void construct(Logger logger, String name, int id, double version) {}
		
		@Override
		public String getBasePath() {
			return "/test";
		}

		@Override
		public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {}
		
	}
	
	@Test
	public void test_process() throws Throwable {
		try {
			CCMessage msg = new CCMessage();
			msg.setPluginId(100);
			msg.setModuleClass("org.test.com.Test");
			x2server.getPluginManager().process(msg);
		}catch(Throwable rtEx) {
			assertEquals("Module: 100 - org.test.com.Test could not be found", rtEx.getMessage());
		}
		
		Holder<String> errorStr = new Holder<>();
		x2server.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/1/v1.0/test";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080/1/v1.0/test");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertEquals("no module mapping defined for: /1/v1.0/test supported mappings are: "+Util.getFieldValue(x2server.getPluginManager(), "moduleEndpointMap", Map.class).toString(), errorStr.value);
		//ok now lets test the virtual host scenario
		Map<String,String> vhostMap = Util.getFieldValue(x2server.getPluginManager(), "virtualHostMap", Map.class);
		vhostMap.put("//www.google.com", "/1/v1.0/test");
		x2server.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://www.google.com/");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertEquals("the path: /1/v1.0/test does not currespond to any mappings. supported mappings are: "+Util.getFieldValue(x2server.getPluginManager(), "moduleEndpointMap", Map.class).toString(), errorStr.value);
		//ok now lets add a module map for our test module.
		uploadModule(1, 1.0, TestWebModule.class.getSimpleName(), TestWebModule.class, TestPluginManager.class);
		//we now need to wait until the upload has completed.
		do {
			TimeUnit.SECONDS.sleep(1);
		}while(!((List<X2ApplicationMetaData>)Util.getFieldValue(x2server.getPluginManager(),"APPLICATION_LIST", List.class)).stream().anyMatch(app -> CCPluginManager.PLUGIN_ID(app.getId()) == 1));
		assertNotNull(Util.getFieldValue(x2server.getPluginManager(), "moduleEndpointMap", Map.class).get("/1/v1.0/test"));
		errorStr.value = null;
		x2server.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://www.google.com/");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertNull(errorStr.value);
		//we need to call a url on the plugin manager
		x2server.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/x2manager/check";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080/x2manager/check");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertNotNull(errorStr.value);
	}
	
	@Test
	public void test_process_http_pluginmanager_module() throws Throwable {
		Holder<String> contentType = new Holder<>();
		x2server.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/x2manager/check";
			}

			@Override
			public String getRequestURI() {
				return getServletPath();
			}
			
			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080/x2manager/check");
			}

			@Override
			public String getHeader(String string) {
				return "Basic "+Base64.getEncoder().encodeToString("test:test".getBytes(Util.UTF8_CHARSET));
			}

			@Override
			public String getParameter(String string) {
				return null;
			}

			@Override
			public String getMethod() {
				return "GET";
			}
			
			
			
		}, new HttpServletResponseAdapter() {
			@Override
			public void setContentType(String string) {
				contentType.value = string;
			}

			@Override
			public void setContentLength(int i) {
				
			}

			@Override
			public ServletOutputStream getOutputStream() throws IOException {
				return new ServletOutputStream() {
					private ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
					
					@Override
					public boolean isReady() {
						return true;
					}

					@Override
					public void setWriteListener(WriteListener wl) {}

					@Override
					public void write(int b) throws IOException {
						byteOut.write(b);
					}
				};
			}
			
			
			@Override
			public void sendError(int i, String string) throws IOException {}
		});
		assertNotNull(contentType.value);
		assertEquals("text/html",contentType.value);
	}
}
