/**
 * Copyright 2015 SmartBear Software
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.wasdev.maven.plugins.swaggerdocgen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.swagger.util.PathUtils;
import io.swagger.util.ReflectionUtils;

// Portions of this class were borrowed from io.swagger.jaxrs.Reader.
public class JAXRSAnnotationsUtil {
    
    public static Set<String> getOperationPaths(final Class<?> cls) {
        final Set<String> operationPaths = new HashSet<String>();
        final javax.ws.rs.Path apiPath = cls.getAnnotation(javax.ws.rs.Path.class);
        final Method methods[] = cls.getMethods();
        for (Method method : methods) {
            if (ReflectionUtils.isOverriddenMethod(method, cls)) {
                continue;
            }
            final javax.ws.rs.Path methodPath = getAnnotation(method, javax.ws.rs.Path.class);
            String operationPath = getPath(apiPath, methodPath);
            final Map<String, String> regexMap = new HashMap<String, String>();
            operationPath = PathUtils.parsePath(operationPath, regexMap);
            if (operationPath != null) {
                operationPaths.add(operationPath);
            }
        }
        return Collections.unmodifiableSet(operationPaths);
    }
    
    private static <A extends Annotation> A getAnnotation(final Method method, final Class<A> annotationClass) {
        A annotation = method.getAnnotation(annotationClass);
        if (annotation == null) {
            Method superclassMethod = ReflectionUtils.getOverriddenMethod(method);
            if (superclassMethod != null) {
                annotation = getAnnotation(superclassMethod, annotationClass);
            }
        }
        return annotation;
    }
    
    private static String getPath(final javax.ws.rs.Path classLevelPath, final javax.ws.rs.Path methodLevelPath) {
        if (classLevelPath == null && methodLevelPath == null) {
            return null;
        }
        final StringBuilder b = new StringBuilder();
        if (classLevelPath != null) {
            b.append(classLevelPath.value());
        }
        if (methodLevelPath != null && !"/".equals(methodLevelPath.value())) {
            String methodPath = methodLevelPath.value();
            if (!methodPath.startsWith("/") && !b.toString().endsWith("/")) {
                b.append("/");
            }
            if (methodPath.endsWith("/")) {
                methodPath = methodPath.substring(0, methodPath.length() - 1);
            }
            b.append(methodPath);
        }
        String output = b.toString();
        if (!output.startsWith("/")) {
            output = "/" + output;
        }
        if (output.endsWith("/") && output.length() > 1) {
            return output.substring(0, output.length() - 1);
        } 
        else {
            return output;
        }
    }
}
