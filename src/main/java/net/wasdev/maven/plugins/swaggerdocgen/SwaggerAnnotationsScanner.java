/**
* (C) Copyright IBM Corporation 2016.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package net.wasdev.maven.plugins.swaggerdocgen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.swagger.annotations.Api;
import io.swagger.annotations.SwaggerDefinition;
import net.wasdev.maven.plugins.swaggerdocgen.xml.Servlet;
import net.wasdev.maven.plugins.swaggerdocgen.xml.WebApp;

public class SwaggerAnnotationsScanner {
	private static final String JAX_RS_APPLICATION_CLASS_NAME = "javax.ws.rs.core.Application";
	private static final String JAX_RS_APPLICATION_INIT_PARAM = "javax.ws.rs.Application";
	private static final Logger logger = Logger.getLogger(SwaggerAnnotationsScanner.class.getName());

	private ClassLoader classLoader;
	private WebApp webApp;
	private Set<Class<?>> annotated;
	private Set<Class<?>> appClasses;
	private List<String> classNames;
	private final ZipFile warFile;

	public SwaggerAnnotationsScanner(ClassLoader classLoader, ZipFile warFile) throws IOException {
		this.classLoader = classLoader;
		this.warFile = warFile;
		Enumeration<? extends ZipEntry> entries = warFile.entries();
		classNames = new ArrayList<String>();
		while (entries.hasMoreElements()) {
			ZipEntry zipEntry = (ZipEntry) entries.nextElement();
			String ename = zipEntry.getName();
			if (ename.startsWith("WEB-INF/classes/")) {
				if (ename.endsWith(".class")) {
					String className = ename.substring(16, ename.length() - 6);
					className = className.replace("/", ".");
					classNames.add(className);
				}
			}
		}
		ZipEntry webXmlEntry = warFile.getEntry("WEB-INF/web.xml");
		if (webXmlEntry != null) {
			webApp = WebApp.loadWebXML(warFile.getInputStream(webXmlEntry));
		}
	}

	public Set<Class<?>> getScannedClasses() {
		appClasses = new HashSet<Class<?>>();
		List<Class<?>> loadedClasses = loadClasses(classNames);
		annotated = getAnnotatedClasses(loadedClasses);
		return Collections.unmodifiableSet(annotated);
	}

	private Set<Class<?>> getAnnotatedClasses(List<Class<?>> classses) {
		Set<Class<?>> annotated = new HashSet<Class<?>>();
		for (Class<?> clazz : classses) {
			if (isAnnotated(clazz)) {
				annotated.add(clazz);
			}
			if (isAppClass(clazz)) {
				appClasses.add(clazz);
			}
		}
		return annotated;
	}

	private List<Class<?>> loadClasses(List<String> classNames) {
		List<Class<?>> loadedClasses = new ArrayList<Class<?>>();
		try {
			WARClassLoader cl = new WARClassLoader(classLoader, warFile);
			if (classNames != null && !classNames.isEmpty()) {
				for (String className : classNames) {
					Class<?> clz = cl.loadClass(className);
					loadedClasses.add(clz);
				}
			}
		} catch (ClassNotFoundException e) {
			logger.finest(e.getMessage());
		}
		return loadedClasses;
	}

	private boolean isAnnotated(Class<?> clz) {
		if (clz.getAnnotation(Api.class) != null) {
			return true;
		}
		if (clz.getAnnotation(SwaggerDefinition.class) != null) {
			return true;
		}
		if (clz.getAnnotation(javax.ws.rs.Path.class) != null) {
			return true;
		}

		return false;
	}

	private boolean isAppClass(Class<?> clz) {
		if (Application.class.isAssignableFrom(clz)) {
			return true;
		}

		return false;
	}

	private String getUrlPatternForCoreAppServlet() {
		// Check for this scenario
		// <servlet>
		// <servlet-name>javax.ws.rs.core.Application</servlet-name>
		// </servlet>
		// <servlet-mapping>
		// <servlet-name>javax.ws.rs.core.Application</servlet-name>
		// <url-pattern>/sample1/*</url-pattern>
		// </servlet-mapping>
		//
		// This scenario means no sub-class of Application exists,
		// and servlet mapping is required.
		if (webApp != null) {
			return webApp.getServletMapping(JAX_RS_APPLICATION_CLASS_NAME);
		}
		return null;
	}

	private String findServletMappingForApp(String appClassName) {

		if (appClassName == null) {
			return null;
		}
		if (webApp == null) {
			return null;
		}

		// Check for this scenario
		// <servlet>
		// <servlet-name>apps.MyApp</servlet-name>
		// </servlet>
		// servlet-mapping or @ApplicationPath is needed

		Servlet servlet = webApp.getServlet(appClassName);
		if (servlet != null) {
			logger.finest("Found servlet using sevlet-name: " + appClassName);
			return webApp.getServletMapping(servlet.getName());
		}

		// Check each servlet for 2 scenarios
		List<Servlet> servlets = webApp.getServlets();
		if (servlets != null) {
			for (Servlet srvlet : servlets) {
				// Check if <servlet-class> is application
				String servletClass = srvlet.getServletClass();
				if (servletClass != null && servletClass.equals(appClassName)) {
					logger.finest("Found servlet using sevlet-class");
					return webApp.getServletMapping(srvlet.getName());
				}
				// check if application is specified through init-param
				String initParam = srvlet.getInitParamValue(JAX_RS_APPLICATION_INIT_PARAM);
				if (initParam != null && initParam.equals(appClassName)) {
					logger.finest("Found servlet using init-param");
					return webApp.getServletMapping(srvlet.getName());
				}

			}
		}
		logger.finest("Didn't find servlet mapping in web.xml for " + appClassName);
		return null;
	}

	public String getUrlMapping() {
		String urlPattern = null;
		if (appClasses.size() == 0) {
			urlPattern = getUrlPatternForCoreAppServlet();
		} else if (appClasses.size() == 1) {
			Class<?> appClass = appClasses.iterator().next();
			urlPattern = findServletMappingForApp(appClass.getName());
			if (urlPattern == null) {
				ApplicationPath apath = appClass.getAnnotation(ApplicationPath.class);
				if (apath != null) {
					urlPattern = apath.value();
					if (urlPattern == null || urlPattern.isEmpty() || urlPattern.equals("/")) {
						return null;
					}
					if (urlPattern != null && !urlPattern.startsWith("/")) {
						urlPattern = "/" + urlPattern;
					}
				} else {
					logger.finest("Didn't find @ApplicationPath in Application classs " + appClass.getName());
				}
			}
		}
		return urlPattern;
	}
}
