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
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class WARClassLoader extends ClassLoader {
	private final ZipFile warFile;
	private static final Logger logger = Logger.getLogger(WARClassLoader.class.getName());

	public WARClassLoader(ClassLoader classLoader, ZipFile warFile) {
		super(classLoader);
		this.warFile = warFile;
	}

	private synchronized Class<?> loadFromWar(String name) {
		if (this.warFile == null) {
			return null;
		}
		try {
			String zipPath = "WEB-INF/classes/" + name.replace(".", "/") + ".class";
			logger.finest("Loading class file from WAR " + zipPath);
			ZipEntry ze = warFile.getEntry(zipPath);
			if (ze != null) {
				InputStream is = warFile.getInputStream(ze);
				byte[] classData = new byte[(int) ze.getSize()];
				int read = is.read(classData, 0, classData.length);
				if (read != classData.length) {
					return null;
				}
				is.close();
				return defineClass(name, classData, 0, classData.length);
			}
		} catch (IOException e) {
			logger.finest(e.getMessage());
		}
		return null;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class<?> cls = loadFromWar(name);
		if (cls != null) {
			return cls;
		}
		return super.findClass(name);
	}
}
