package net.wasdev.maven.plugins.swaggerdocgen;

import java.io.File;


public class GenerateSwaggerFile {
	public static void main(String[] args) {
		if(args.length != 1) {
			System.out.println("Please provide a single argument containing the path of the application WAR");
		} else {
			
			File warFile = new File(args[0]);
			if(warFile.exists()) {
				File warFileDirectory = warFile.getAbsoluteFile().getParentFile();
				if(warFileDirectory != null) {
					File outputFile = new File(warFileDirectory.getPath() + File.separator + "swagger.json");
					
					try {
						SwaggerProcessor processor = new SwaggerProcessor(GenerateSwaggerFile.class.getClassLoader(), warFile, outputFile); 
						processor.process();
						
						System.out.println("Success: Your swagger.json file was succcessfully generated.");
					} catch(Exception e) {
						System.out.println("Please include all dependencies of the application in the classpath - including the runtime of the java ee application.");
						System.out.println(e);
					}
					
					
				} else {
					System.out.println("Error: The specified WAR application could not be found.");
				}
			} else {
				System.out.println("Error: The specified WAR application could not be found.");
			}
		}
	}
}
