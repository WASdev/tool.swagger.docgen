package net.wasdev.maven.plugins.swaggerdocgen;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Create Swagger document from a WAR.
 * 
 */
@Mojo(name = "create", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class CreateMojo extends AbstractMojo {
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;

	/**
	 * Location of the output Swagger document.
	 */
	@Parameter(defaultValue = "${project.build.directory}/swagger.yaml", required = false)
	private File outputFile;

	/**
	 * Location of the target WAR file.
	 */
	@Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.war", required = false)
	private File warFile;
	
	/**
	 * The prefixes of classes to scan for annotations
	 */
	@Parameter(required = false)
	private String[] prefixes = null;
	
	/**
	 * The path the contents fo the WAR's WEB-INF\lib will temporarily be unpacked to
	 */
	@Parameter(defaultValue = "${project.build.directory}", required = false)
	private String tmpPath;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.AbstractMojo#execute()
	 */
	public void execute() throws MojoExecutionException {
		try {
			SwaggerProcessor processor = new SwaggerProcessor(prefixes, tmpPath,getClassLoader(), warFile, outputFile);
			processor.process();
		} catch (Exception e) {
			throw new MojoExecutionException("Error generating a Swagger document.", e);
		}
	}

	private ClassLoader getClassLoader() throws MalformedURLException {
		List<URL> classpathURLs = new ArrayList<URL>();
		this.addProjectDependencies(classpathURLs);
		return new URLClassLoader(classpathURLs.toArray(new URL[classpathURLs.size()]),
				CreateMojo.class.getClassLoader());
	}

	@SuppressWarnings("unchecked")
	private void addProjectDependencies(List<URL> path) throws MalformedURLException {
		List<Artifact> artifacts = new ArrayList<Artifact>();
		artifacts.addAll(project.getArtifacts());
		artifacts.addAll(project.getRuntimeArtifacts());
		for (Artifact classPathElement : artifacts) {
			path.add(classPathElement.getFile().toURI().toURL());
		}

		List<File> classpathFiles = new ArrayList<File>();
		classpathFiles.add(new File(project.getBuild().getOutputDirectory()));
		for (File classpathFile : classpathFiles) {
			URL url = classpathFile.toURI().toURL();
			path.add(url);
		}
	}
}
