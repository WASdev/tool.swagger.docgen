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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private WARClassLoader WARCL;
    private WebApp webApp;
    private Set<Class<?>> annotated;
    private Set<Class<?>> appClasses;
    private List<String> classNames;
    private final ZipFile warFile;

    private Path TmpJarFolder = null;

    public SwaggerAnnotationsScanner(ClassLoader classLoader, ZipFile warFile) throws IOException {
    	this(".",null, classLoader, warFile);
    }
    
    public SwaggerAnnotationsScanner(String tmpPath, String packageName, ClassLoader classLoader, ZipFile warFile) throws IOException {
        this.warFile = warFile;
        
        classNames = getClassesInArchive(warFile, "WEB-INF/classes/");
        TmpJarFolder = unpackJars(warFile, tmpPath);
        for(File f : TmpJarFolder.toFile().listFiles())
        {
        	classNames.addAll(getClassesInArchive(new ZipFile(f)));
        }
       
        
        if(packageName != null)
        {
        	ArrayList<String> tmp = new ArrayList<String>();
        	for(String className : classNames)
        	{
        		if(className.startsWith(packageName))
        		{
        			tmp.add(className);
        		}
        	}
        	classNames = tmp;
        }
        
        WARCL = getClassLoader(warFile, TmpJarFolder, classLoader);
        
        ZipEntry webXmlEntry = warFile.getEntry("WEB-INF/web.xml");
        if (webXmlEntry != null) {
            webApp = WebApp.loadWebXML(warFile.getInputStream(webXmlEntry));
        }
    }
    
    private WARClassLoader getClassLoader (ZipFile warF, Path tmpJarPath, ClassLoader defaultClassLoader)
    {
    	if(tmpJarPath == null)
        {
        	return new WARClassLoader(defaultClassLoader, warF);
        } 
        else 
        {
            URLClassLoader ucl = getURLClassLoader(tmpJarPath, defaultClassLoader);
            if (ucl == null) {
            	cleanupJarFolder();
            	return new WARClassLoader(defaultClassLoader, warF);
            } else {
                return new WARClassLoader(ucl, warF);
            }
        }
    }
    
    private ArrayList<String> getClassesInArchive(ZipFile archive)
    {
    	return getClassesInArchive(archive, "");
    }
    
    private ArrayList<String> getClassesInArchive(ZipFile archive, String pathPrefix)
    {
    	ArrayList<String> clsNames = new ArrayList<String>();
    	Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            String ename = zipEntry.getName();
            if (ename.startsWith(pathPrefix)) {
                if (ename.endsWith(".class")) {
                    String className = ename.substring(pathPrefix.length(), ename.length() - 6);
                    className = className.replace("/", ".");
                    clsNames.add(className);
                }
            }
        }
        
        return clsNames;
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

            if (classNames != null && !classNames.isEmpty()) {
                for (String className : classNames) {
                    Class<?> clz = WARCL.loadClass(className);
                    loadedClasses.add(clz);
                }
            }
        } catch (ClassNotFoundException e) {
            logger.finest(e.getMessage());
        }

        return loadedClasses;
    }

    private URLClassLoader getURLClassLoader(Path jarPath, ClassLoader cl) {
        try {
            ArrayList<URL> urls = new ArrayList<URL>();
            for (File f : jarPath.toFile().listFiles()) {
                URL url = new URL("jar:file:" + f.getAbsolutePath() + "!/");
                urls.add(url);
            }

            URL[] urlArray = new URL[urls.size()];
            urls.toArray(urlArray);
            URLClassLoader urlcl = new URLClassLoader(urlArray, cl);
            return urlcl;
        } catch (Exception e) {
            return null;
        }

    }

    private Path unpackJars(ZipFile war, String tmpPath) {
        Path tmpDir = null;
        try {
        	File f = new File(tmpPath+"\\tmp");
            f.mkdir();
            Path cwd = f.toPath();
            tmpDir = java.nio.file.Files.createTempDirectory(cwd, "jars");
            Enumeration<? extends ZipEntry> entries = war.entries();
            classNames = new ArrayList<String>();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) entries.nextElement();
                String ename = zipEntry.getName();
                if (ename.startsWith("WEB-INF/lib/") && ename.endsWith(".jar")) {
                    InputStream is = warFile.getInputStream(zipEntry);
                    byte[] jarData = new byte[(int) zipEntry.getSize()];
                    int toRead = jarData.length;
                    int startIndex = 0;
                    while (toRead > 0) {
                        int actuallyRead = is.read(jarData, startIndex, toRead);
                        startIndex += actuallyRead;
                        toRead -= actuallyRead;

                    }
                    is.close();

                    String jarName = ename.substring(12, ename.length());
                    File jarPath = new File(tmpDir.toFile(), jarName);
                    FileOutputStream fos = new FileOutputStream(jarPath);
                    fos.write(jarData);
                    fos.flush();
                    fos.close();
                }
            }
            return tmpDir;
        } catch (Exception e) {
            if (tmpDir != null) {
                File f = tmpDir.toFile();
                for (File tmp : f.listFiles()) {
                    tmp.deleteOnExit();
                }
                f.deleteOnExit();
            }
            return null;
        }
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
            logger.finest("Found servlet using servlet-name: " + appClassName);
            return webApp.getServletMapping(servlet.getName());
        }

        // Check each servlet for 2 scenarios
        List<Servlet> servlets = webApp.getServlets();
        if (servlets != null) {
            for (Servlet srvlet : servlets) {
                // Check if <servlet-class> is application
                String servletClass = srvlet.getServletClass();
                if (servletClass != null && servletClass.equals(appClassName)) {
                    logger.finest("Found servlet using servlet-class");
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

    public Object getUrlMapping() {

        final Set<Class<?>> appClasses = this.appClasses;
        final int size = appClasses.size();

        if (size < 2) {
            String urlMapping = null;
            if (size == 0) {
                urlMapping = getUrlPatternForCoreAppServlet();
            } else {
                Class<?> appClass = appClasses.iterator().next();
                urlMapping = findServletMappingForApp(appClass.getName());
                if (urlMapping == null) {
                    urlMapping = getURLMappingFromApplication(appClass);
                }
            }
            return urlMapping;
        } else {
            // Mapping between operation paths and URLs.
            final Map<String, String> urlMappings = new HashMap<String, String>();
            final Set<String> allOperationPaths = new HashSet<String>();
            for (Class<?> appClass : appClasses) {
                String urlMapping = findServletMappingForApp(appClass.getName());
                if (urlMapping == null) {
                    urlMapping = getURLMappingFromApplication(appClass);
                }
                if (urlMapping == null) {
                    continue;
                }
                Set<Class<?>> classes = getClassesFromApplication(appClass);
                if (classes != null) {
                    for (Class<?> cls : classes) {
                        Set<String> operationPaths = JAXRSAnnotationsUtil.getOperationPaths(cls);
                        if (!allOperationPaths.isEmpty()) {
                            for (String operationPath : operationPaths) {
                                // More than one operation has the same operation path.
                                // Only support a mix of applications that have unique
                                // 1-n mappings between URLs and operation paths for now.
                                if (allOperationPaths.contains(operationPath)) {
                                    return null;
                                }
                                allOperationPaths.add(operationPath);
                                urlMappings.put(operationPath, urlMapping);
                            }
                        } else {
                            allOperationPaths.addAll(operationPaths);
                        }
                    }
                }
            }
            return urlMappings;
        }
    }

    private String getURLMappingFromApplication(Class<?> appClass) {
        ApplicationPath apath = appClass.getAnnotation(ApplicationPath.class);
        if (apath != null) {
            String urlMapping = apath.value();
            if (urlMapping == null || urlMapping.isEmpty() || urlMapping.equals("/")) {
                return "";
            }
            if (urlMapping != null && !urlMapping.startsWith("/")) {
                urlMapping = "/" + urlMapping;
            }
            return urlMapping;
        } else {
            logger.finest("Didn't find @ApplicationPath in Application classs " + appClass.getName());
        }
        return null;
    }

    public Set<Class<?>> getClassesFromApplication(Class<?> appClass) {
        try {
            if (!Application.class.isAssignableFrom(appClass)) {
                return null;
            }
            Application app = (Application) appClass.newInstance();
            Set<Class<?>> clss = app.getClasses();
            Set<Object> singletons = app.getSingletons();
            HashSet<Class<?>> classes = new HashSet<Class<?>>();
            for (Class<?> cls : clss) {
                classes.add(cls);
            }
            for (Object singleton : singletons) {
                classes.add(singleton.getClass());
            }
            return classes;
        } catch (IllegalAccessException e) {
            logger.finest("Failed to initialize Application: " + appClass.getName() + ": " + e.getMessage());
        } catch (InstantiationException e) {
            logger.finest("Failed to initialize Application: " + appClass.getName() + ": " + e.getMessage());
        }
        return null;
    }

    public void cleanupJarFolder() {
        if (TmpJarFolder != null) {
            File f = TmpJarFolder.toFile();
            for (File tmp : f.listFiles()) {
                tmp.deleteOnExit();
            }
            f.deleteOnExit();
        }
    }
}
