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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import io.swagger.annotations.ApiModel;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.Reader;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;

public class SwaggerProcessor {

    // Location of Swagger JSON doc in module
    private static final String DEFAULT_SWAGGER_JSON_LOCATION = "META-INF/swagger.json";

    // Location of Swagger JSON stub doc in module
    private static final String DEFAULT_SWAGGER_JSON_STUB_LOCATION = "META-INF/stub/swagger.json";

    // Location of Swagger YAML doc in module
    private static final String DEFAULT_SWAGGER_YAML_LOCATION = "META-INF/swagger.yaml";

    // Location of Swagger YAML stub doc in module
    private static final String DEFAULT_SWAGGER_YAML_STUB_LOCATION = "META-INF/stub/swagger.yaml";

    private final ClassLoader classLoader;
    private final File warFile;
    private final File outputFile;
    private final String tmpPath;
    private final String[] prefixes;
    
    
    private static final Logger logger = Logger.getLogger(SwaggerProcessor.class.getName());

    public SwaggerProcessor(ClassLoader classLoader, File warFile, File outputFile)
    {
    	this(null, ".", classLoader, warFile, outputFile);
    }
    
    public SwaggerProcessor(String[] prefixes, String tmpPath, ClassLoader classLoader, File warFile, File outputFile) {
        this.classLoader = classLoader;
        this.warFile = warFile;
        this.outputFile = outputFile;
        this.prefixes = prefixes;
        this.tmpPath = tmpPath;
    }
    
    public void process() {
        final String document = getDocument();
        if (document != null) {
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
                writer.write(document);
                writer.flush();
            } catch (IOException ioe) {
                logger.severe("Failed to write to the output document " + outputFile.getAbsolutePath());
            } finally {
                tryToClose(writer);
            }
        }
    }

    public String getDocument() {
        ZipFile warZipFile = null;
        try {
            warZipFile = new ZipFile(warFile);

            // Search for META-INF/swagger.json or META-INF/swagger.yaml in the
            // WAR
            ZipEntry entry = warZipFile.getEntry(DEFAULT_SWAGGER_JSON_LOCATION);
            if (entry == null) {
                entry = warZipFile.getEntry(DEFAULT_SWAGGER_YAML_LOCATION);
            }
            if (entry != null) {
                InputStream swaggerStream = warZipFile.getInputStream(entry);
                String swaggerDoc = getSwaggerDocFromStream(swaggerStream);
                // Swagger swaggerModel = new SwaggerParser().parse(swaggerDoc,
                // null, false);
                Swagger swaggerModel = new SwaggerParser().parse(swaggerDoc, null);

                return createYAMLfromPojo(swaggerModel);
            }

            // Search for META-INF/stub/swagger.json or
            // META-INF/stub/swagger.yaml in the WAR
            Swagger swaggerStubModel = null;
            entry = warZipFile.getEntry(DEFAULT_SWAGGER_JSON_STUB_LOCATION);
            if (entry == null) {
                entry = warZipFile.getEntry(DEFAULT_SWAGGER_YAML_STUB_LOCATION);
            }
            if (entry != null) {
                InputStream swaggerStream = warZipFile.getInputStream(entry);
                String swaggerDoc = getSwaggerDocFromStream(swaggerStream);
                swaggerStubModel = new SwaggerParser().parse(swaggerDoc, null);
            }
            // Scan the WAR for annotations and merge with the stub document.
            return getSwaggerDocFromAnnotatedClasses(warZipFile, swaggerStubModel);
        } catch (IOException ioe) {
            logger.severe("Failed to generate the Swagger document.");
        } finally {
            tryToClose(warZipFile);
        }
        return null;
    }

    private String getSwaggerDocFromStream(InputStream is) {
        try {
            return IOUtils.toString(is, "UTF-8"); // YAML document's format has
                                                    // to be preserved
        } catch (IOException ioe) {
            logger.severe("Cannot read the Swagger document inside the application.");
        } finally {
            tryToClose(is);
        }
        return null;
    }

    private String getSwaggerDocFromAnnotatedClasses(ZipFile warZipFile, Swagger swaggerStubModel) throws IOException {
        SwaggerAnnotationsScanner annScan = null;
        try {
            annScan = new SwaggerAnnotationsScanner(tmpPath, prefixes, classLoader, warZipFile);
            Set<Class<?>> classes = annScan.getScannedClasses();
            Reader reader = new Reader(swaggerStubModel);
            Set<String> stubPaths = null;
            if (swaggerStubModel != null && swaggerStubModel.getPaths() != null) {
                stubPaths = swaggerStubModel.getPaths().keySet();
            }
            
            for(Class<?> c : getModelClasses(classes))
            {
            		appendModels(c, reader.getSwagger());
            }
            
            Swagger swresult = reader.read(classes);
            
            swresult = addUrlMapping(swresult, stubPaths, annScan.getUrlMapping());
            String ext = FilenameUtils.getExtension(this.outputFile.getName());
            if (ext.equalsIgnoreCase("json")) {
                return createJSONfromPojo(swresult);
            } else if (ext.equalsIgnoreCase("yaml")) {
                return createYAMLfromPojo(swresult);
            }
            throw new IllegalArgumentException("Unsupported document type: " + ext);
        } finally {
            if (annScan != null) {
                annScan.cleanupJarFolder();
            }
        }
    }

    private Set<Class<?>> getModelClasses(Set<Class<?>> classes) {
    	Set<Class<?>> modelClasses = new HashSet<Class<?>>();
    	for(Class<?> c : classes)
    	{
    		if(c.getAnnotation(ApiModel.class) != null)
    		{
    			modelClasses.add(c);
    		}
    	}
    	return modelClasses;
	}

	private Swagger addUrlMapping(Swagger result, Set<String> ignorePaths, Object urlMappings) {
        if (urlMappings == null || "".equals(urlMappings)) {
            return result;
        }

        Map<String, Path> paths = result.getPaths();
        Map<String, Path> newPaths = new HashMap<String, Path>();

        if (urlMappings instanceof String) {
            final String urlMapping = (String) urlMappings;
            for (Entry<String, Path> pathEntry : paths.entrySet()) {
                if (ignorePaths != null && ignorePaths.contains(pathEntry.getKey())) {
                    newPaths.put(pathEntry.getKey(), pathEntry.getValue());
                    continue;
                }
                newPaths.put(urlMapping + pathEntry.getKey(), pathEntry.getValue());
            }
        } else {
            @SuppressWarnings("unchecked")
            Map<String, String> mappings = (Map<String, String>) urlMappings;
            for (Entry<String, Path> pathEntry : paths.entrySet()) {
                final String urlMapping = mappings.get(pathEntry.getKey());
                if ((ignorePaths != null && ignorePaths.contains(pathEntry.getKey())) || urlMapping == null) {
                    newPaths.put(pathEntry.getKey(), pathEntry.getValue());
                    continue;
                }
                newPaths.put(urlMapping + pathEntry.getKey(), pathEntry.getValue());
            }
        }

        result.setPaths(newPaths);
        return result;
    }

    private String createYAMLfromPojo(Object pojo) throws IOException {
        return Yaml.mapper().writeValueAsString(pojo);
    }

    private void tryToClose(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ioe) {
                logger.severe("Failed to successfully close the file.");
            }
        }
    }

    private String createJSONfromPojo(Object pojo) throws IOException {
        return Json.pretty(pojo);
    }
    
    private void appendModels(Type type, Swagger swagger) {
        final Map<String, Model> models = ModelConverters.getInstance().read(type);
        for (Map.Entry<String, Model> entry : models.entrySet()) {
            swagger.model(entry.getKey(), entry.getValue());
        }
    }
}
