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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Servlet {
    @XmlElement(name = "servlet-name")
    private String name;

    @XmlElement(name = "init-param")
    private List<InitParam> initParams;

    @XmlElement(name = "servlet-class")
    private String servletClass;

    public String getName() {
        return name;
    }

    public List<InitParam> getInitParams() {
        return initParams;
    }

    public String getServletClass() {
        return servletClass;
    }

    public String getInitParamValue(String paramName) {
        if (initParams == null) {
            return null;
        }
        for (InitParam initParam : initParams) {
            if (initParam != null) {
                final String initParamName = initParam.getName();
                if (initParamName != null && initParamName.equals(paramName)) {
                    return initParam.getValue();
                }
            }
        }
        return null;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class InitParam {

        @XmlElement(name = "param-name")
        private String name;

        @XmlElement(name = "param-value")
        private String value;

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
