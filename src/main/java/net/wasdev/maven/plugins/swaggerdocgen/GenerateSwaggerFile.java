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
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;

public class GenerateSwaggerFile {

    private static final Logger logger = Logger.getLogger(GenerateSwaggerFile.class.getName());

    public static void main(String[] args) throws Exception {
        if(args.length == 1 || args.length == 2) {
            File warFile = createWARFile(args[0]);
            if(warFile == null) {
                System.exit(1);
            }

            File outputFile;
            if(args.length == 1) {
                outputFile = createOutputFile(warFile, null);
            } else {
                outputFile = createOutputFile(warFile, args[1]);
            }

            if(outputFile == null) {
                System.exit(1);
            }

            SwaggerProcessor processor = new SwaggerProcessor(GenerateSwaggerFile.class.getClassLoader(), warFile, outputFile); 
            processor.process();
                
            logger.info("Success: Your swagger file was succcessfully generated.");
            
            System.exit(0);
        }

        logger.severe("Please provide the path of the WAR application as an argument (and optionally the name of the swagger file)");
        System.exit(1);
    }


    /**
    * Handle the first argument.
    * Return WAR application as a file on success
    * Return null if failiure
    */
    private static File createWARFile(String argument) {
        File warFile = new File(argument);
        if(warFile.exists()) {
            return warFile;
        }

        logger.severe("Error: The specified WAR application could not be found.");
        return null;
    }

    /**
    * Handle the second argument
    * Return output File object on success
    * Return null if failiure
    */
    private static File createOutputFile(File warFile, String argument) {
        File warFileDirectory = warFile.getAbsoluteFile().getParentFile();
        if(argument == null) {
            return new File(warFileDirectory.getPath() + File.separator + "swagger.yaml");
        }
        String ext = FilenameUtils.getExtension(argument);
        if(!(ext.equalsIgnoreCase("json") || ext.equalsIgnoreCase("yaml"))) {
            logger.severe("Please specify the extension type as json or yaml");
            return null;
        }
        File tempFile = new File(argument);
        if(tempFile.isAbsolute() && !tempFile.isDirectory()) {
            return tempFile;
        }

        return new File(warFileDirectory.getPath() + File.separator + argument);

    }
}
