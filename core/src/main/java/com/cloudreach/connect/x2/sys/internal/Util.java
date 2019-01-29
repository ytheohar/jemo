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
package com.cloudreach.connect.x2.sys.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

/**
 * these are a series of utility methods used by the X2 core.
 * 
 * @author christopher stura
 */
public class Util {
	public static final ObjectMapper mapper = new ObjectMapper();
	public static final Pattern INT_PATTERN = Pattern.compile("[0-9]+");
	public static final Pattern RANGE_PATTERN = Pattern.compile("([0-9]+)-([0-9]+)");
	public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	
	static {
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(Util.class.getClassLoader());
		mapper.setTypeFactory(tf);
	}
	
	public static int parse(String str) {
		int result = 0;
		try {
			result = new Double(str).intValue();
		}catch(Throwable ex) {}
		
		return result;
	}
	
	public static String toString(InputStream in) throws IOException {
		StringBuilder out = new StringBuilder();
		byte[] buf = new byte[8192];
		int rb = 0;
		while((rb = in.read(buf)) != -1) {
			out.append(new String(buf,0,rb,"UTF-8"));
		}
		return out.toString();
	}
	
	public static byte[] toByteArray(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		stream(out, in, true);
		return out.toByteArray();
	}
	
	public static String md5(String origStr) throws NoSuchAlgorithmException,UnsupportedEncodingException {
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(origStr.getBytes("UTF-8"));
		return String.format("%032x", new BigInteger(1, md5.digest()));
	}
	
	public static <T extends Object,K extends Object> Map<T,K> MAP(Map.Entry<T,K>... entryList) {
		Map<T,K> map = new LinkedHashMap<>();
		Arrays.asList(entryList).stream().forEach(e -> map.put(e.getKey(),e.getValue()));
		return map;
	}
	
	public static void stream(OutputStream out,InputStream in) throws IOException {
		stream(out,in,true);
	}
	
	public static void stream(OutputStream out,InputStream in,boolean close) throws IOException {
		byte[] buf = new byte[8192];
		int rb = 0;
		while((rb = in.read(buf)) != -1) {
			out.write(buf,0,rb);
			out.flush();
		}
		if(close) {
			in.close();
		}
	}
	
	public static final void log(Level logLevel,String message,Object... args) {
		try {
			Class ccClass = Class.forName("com.cloudreach.connect.x2.CC");
			ccClass.getMethod("log", Level.class, String.class, Object[].class).invoke(ccClass,logLevel, message, args);
		}catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {}
	}
	
	public static final <T extends Object> T fromJSONString(Class<T> cls,String jsonString) throws IOException {
		if(jsonString != null && !jsonString.isEmpty()) {
			try {
				return mapper.readValue(jsonString, cls);
			}catch(JsonParseException parseEx) {
				return null;
			}
		}
		
		return null;
	}
	
	public static final String toJSONString(Object obj) throws JsonProcessingException {
		return mapper.writeValueAsString(obj);
	}
	
	public static final void setFieldValue(Object instanceOrClass,String fieldName,Object value) {
		Util.B(null, x -> {
			Field f = (instanceOrClass instanceof Class ? (Class)instanceOrClass : instanceOrClass.getClass()).getDeclaredField(fieldName);
			f.setAccessible(true); //we are going to need to ensure that the util class is given access to introspect everything. (via module-info.java)
			f.set(instanceOrClass, value);
		});
	}
	
	public static final <T extends Object> T getFieldValue(Object instanceOrClass,String fieldName,Class<T> returnType) {
		return F(null, x -> {
			Field f = (instanceOrClass instanceof Class ? (Class)instanceOrClass : instanceOrClass.getClass()).getDeclaredField(fieldName);
			f.setAccessible(true);
			return returnType.cast(f.get(instanceOrClass));
		});
	}
	/**
	 * this method will use reflection to run a static method on a class of choice. This method will allow you to access
	 * methods regardless of their modifiers so you can run public, private or protected methods
	 * 
	 * @param <T> the return value of the method if retval is set to null then null will be returned.
	 * @param retval the type of the return value to be expected from the method.
	 * @param className the class name where the static method is defined.
	 * @param methodName the name of the method to execute
	 * @param args the values to pass in as arguments to the method. if null values are specified then the types will not be matched
	 *						 and the first method matching the number of arguments and the sequence of valued types will be used.
	 * @return the return value of the method executed.
	 */
	public static final <T extends Object> T runStaticMethod(Class<T> retval,String className,String methodName,Object... args) throws ClassNotFoundException,NoSuchMethodException {
		return runMethod(null, retval, className, methodName, args);
	}
	
	public static final <T extends Object> T runMethod(Object target, Class<T> returnType,String methodName,Object... args) throws ClassNotFoundException,NoSuchMethodException {
		return runMethod(target, returnType, target.getClass().getName(), methodName, args);
	}
	
	private static final <T extends Object> T runMethod(Object target, Class<T> returnType,String className,String methodName,Object... args) throws ClassNotFoundException,NoSuchMethodException {
		Class cls = Class.forName(className);
		Method method = Arrays.asList(cls.getDeclaredMethods()).stream()
			.filter(m -> m.getName().equals(methodName) && args != null ? m.getParameterCount() == args.length : args.length == 0)
			.filter(m -> {
				if(args != null) {
					Class[] paramTypes = m.getParameterTypes();
					for(int i = 0; i < args.length; i++) {
						if(!(args[i] == null || paramTypes[i].isAssignableFrom(args[i].getClass()))) {
							return false;
						}
					}
				}
				
				return true;
			}).findFirst().orElseThrow(() -> new NoSuchMethodException(className+":"+methodName));
		
		try {
			Object r = method.invoke(target == null ? cls : target, args);
			if(returnType != null) {
				return returnType.cast(r);
			}
			
			return null;
		}catch(IllegalAccessException | InvocationTargetException accEx) {
			throw new NoSuchMethodException(className+":"+methodName+" because of: "+accEx.getClass().getSimpleName()+" = "+accEx.getMessage());
		}
	}
	
	public static <T,P> T F(P param,ManagedFunctionWithException<P,T> function) {
		try {
			return function.apply(param);
		}catch(Throwable ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static <P> boolean A(P param,ManagedAcceptor<P> acceptor) {
		try {
			return acceptor.accept(param);
		}catch(Throwable ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static <P> void B(P param,ManagedConsumer<P> consumer) {
		try {
			consumer.accept(param);
		}catch(Throwable ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static final String getTimeString(long elapsedTime) {
		long hours = TimeUnit.HOURS.convert(elapsedTime, TimeUnit.MILLISECONDS);
		elapsedTime = elapsedTime-(TimeUnit.MILLISECONDS.convert(hours, TimeUnit.HOURS));
		long minutes = TimeUnit.MINUTES.convert(elapsedTime, TimeUnit.MILLISECONDS);
		elapsedTime = elapsedTime-(TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES));
		long seconds = TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS);
		
		return String.format("%d Hours %d Minutes %d Seconds", hours, minutes, seconds);
	}
	
	public static final boolean deleteDirectory(File dir) {
		Arrays.asList(dir.listFiles()).stream().forEach(f -> {
			if(f.isDirectory()) {
				deleteDirectory(f);
			} else {
				f.delete();
			}
		});
		return dir.delete();
	}
	
	public static Set<Integer> parseIntegerRangeDefinition(final String rangeDefinition) {
		return rangeDefinition == null ? new HashSet<>() : Arrays.asList(rangeDefinition.split(","))
		.stream().map(p -> {
			Matcher intMatcher = INT_PATTERN.matcher(p);
			Matcher rangeMatcher = RANGE_PATTERN.matcher(p);
			Set<Integer> idRange = new HashSet<>();
			if(rangeMatcher.matches()) {
				rangeMatcher.find(0);
				int start = Integer.parseInt(rangeMatcher.group(1));
				int end = Integer.parseInt(rangeMatcher.group(2));
				idRange.addAll(IntStream.rangeClosed(start, end).mapToObj(Integer::new).collect(Collectors.toList()));
			} else if(intMatcher.matches()) {
				idRange.add(Integer.parseInt(p));
			}
			return idRange;
		}).flatMap(rs -> rs.stream()).collect(Collectors.toSet());
	}
	
	public static final <T extends Object> List<T> LIST(List<T> list) {
		return list != null ? list : new ArrayList<>();
	}
	
	public static void createJar(OutputStream out,Class ... classList) throws Throwable {
		try(JarOutputStream jarOut = new JarOutputStream(out)) {
			for(Class cls : classList) {
				addClassToJar(jarOut, cls);
			}
			jarOut.flush();
		}
	}
		
	private static void addClassToJar(JarOutputStream jarOut,Class cls) throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		try(InputStream clsIn = cls.getResourceAsStream("/"+cls.getName().replace('.','/')+".class")) {
			byte[] buf = new byte[8192];
			int rb = 0;
			while((rb = clsIn.read(buf)) != -1) {
				byteOut.write(buf, 0, rb);
			}
		}
		byte[] clsBytes = byteOut.toByteArray();
		JarEntry entry = new JarEntry(cls.getName().replace('.','/')+".class");
		entry.setSize(clsBytes.length);
		entry.setTime(System.currentTimeMillis());
		jarOut.putNextEntry(entry);
		jarOut.write(clsBytes);
		jarOut.closeEntry();
	}
	
	public static long crc(byte[] bytes) {
		final CRC32 crc = new CRC32();
		crc.update(bytes);
		return crc.getValue();
	}
}
