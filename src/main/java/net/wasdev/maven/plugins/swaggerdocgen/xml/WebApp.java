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

package net.wasdev.maven.plugins.swaggerdocgen.xml;

import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "web-app")
public class WebApp {

	private static final Logger logger = Logger.getLogger(WebApp.class.getName());

	@XmlElement(name = "servlet")
	private List<Servlet> servlets;

	@XmlElement(name = "servlet-mapping")
	private List<ServletMapping> servletMappings;

	public List<Servlet> getServlets() {
		return servlets;
	}

	public List<ServletMapping> getServletMappings() {
		return servletMappings;
	}

	public Servlet getServlet(String name) {
		if (servlets == null) {
			return null;
		}
		for (Servlet servlet : servlets) {
			if (servlet.getName().equals(name)) {
				return servlet;
			}
		}
		return null;
	}

	public List<String> getServletUrlPatterns(String servletName) {
		if (servletMappings == null) {
			return null;
		}
		for (ServletMapping sm : servletMappings) {
			if (sm.getServletName().equals(servletName)) {
				return sm.getUrlPatterns();
			}
		}
		return null;
	}

	public String getServletMapping(String servletName) {
		List<String> urlPatterns = getServletUrlPatterns(servletName);
		if (urlPatterns != null && !urlPatterns.isEmpty()) {
			String urlMapping = urlPatterns.get(0);
			if (urlMapping.endsWith("/*")) {
				urlMapping = urlMapping.substring(0, urlMapping.length() - 2);
			}
			if (urlMapping.endsWith("/")) {
				urlMapping = urlMapping.substring(0, urlMapping.length() - 1);
			}
			return urlMapping;
		}
		return null;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ServletMapping {

		@XmlElement(name = "servlet-name")
		private String servletName;

		@XmlElement(name = "url-pattern")
		private List<String> urlPatterns;

		public String getServletName() {
			return servletName;
		}

		public List<String> getUrlPatterns() {
			return urlPatterns;
		}
	}

	public static WebApp loadWebXML(InputStream xml) {
		try {
			JAXBContext context = JAXBContext.newInstance(WebApp.class);
			Unmarshaller unm = context.createUnmarshaller();
			return (WebApp) unm.unmarshal(xml);
		} catch (JAXBException e) {
			logger.finest(e.getMessage());
		}
		return null;
	}
}
