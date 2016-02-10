# tool.swagger.docgen

Provides a tool for generating Swagger documents for web applications.

## Installation

Use Maven 2.x or 3.x to build the Swaggerdocgen plugin.

1. Clone the repository
   ```sh
   $ git clone https://github.com/WASdev/tool.swagger.docgen.git
   ```

2. Navigate to the tool.swagger.docgen folder
   ```sh
   $ cd tool.swagger.docgen
   ```

3. Build the plugin
   ```sh
   $ mvn install
   ```

## Plugins

### swaggerdocgen-maven-plugin

`swaggerdocgen-maven-plugin` can be used to extract the Swagger document from an application and merge it with any annotated endpoint data to create a unified Swagger document for the application.

#### Configuration

To enable `swaggerdocgen-maven-plugin` in your project add the following to your `pom.xml`:

```xml
<project>
    ...
    <build>
        <plugins>
			<!-- Enable liberty-maven-plugin -->
			<plugin>
				<groupId>net.wasdev.maven.plugins.swaggerdocgen</groupId>
				<artifactId>swaggerdocgen-maven-plugin</artifactId>
				<version>1.0</version>
				<!-- Specify configuration, executions for swaggerdocgen-maven-plugin -->
				...
			</plugin>

        </plugins>
    </build>
    ...
</project>
```

#### Goals

##### create
Creates a Swagger document for the web application. It can generate both YAML and JSON document depending on the **outputFile** parameter extension. If a Swagger document already exists in the WAR's META-INF/swagger.json or META-INF/swagger.yaml, it just converts the document to the right output format. 

Otherwise, it looks for META-INF/stub/swagger.json or META-INF/stub/swagger.yaml and also scans the application for Swagger/JAX-RS annotations, merging the data extracted from the stub document (if one exists) and annotations to generate a unified Swagger document.

Parameters used by this goal:

| Parameter | Description | Required |
| --------  | ----------- | -------  |
| outputFile | Location of the generated Swagger document. **Default value:** \${project.build.directory}/swagger.yaml | No |
| warFile| Location of the WAR to be scanned. **Default value:** \${project.build.directory}/\${project.build.finalName}.war | No |

Example:
```xml
<project>
    ...
	<build>
		<plugins>
			<plugin>
				<groupId>net.wasdev.maven.plugins.swaggerdocgen</groupId>
				<artifactId>swaggerdocgen-maven-plugin</artifactId>
				<version>1.0</version>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<goal>create</goal>
						</goals>
					</execution>
				</executions>
				<configuration>
				    <outputFile>${project.build.directory}/swagger.json</outputFile>
				</configuration>
			</plugin>
		</plugins>
	</build>
    ...
</project>
```

