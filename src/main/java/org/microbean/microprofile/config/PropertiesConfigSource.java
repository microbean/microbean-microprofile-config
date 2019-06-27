/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.microprofile.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

class PropertiesConfigSource extends AbstractConfigSource {

  private static final long serialVersionUID = 1L;
  
  private final Properties properties;

  PropertiesConfigSource(final Properties properties, final String name) {
    this(properties, name, null);
  }
  
  PropertiesConfigSource(final Properties properties, final String name, final Integer ordinal) {
    // The specification does not provide guidance on name choices.
    super(name, ordinal);
    this.properties = properties;
  }

  @Override
  public Map<String, String> getProperties() {
    Map<String, String> returnValue = null;
    final Collection<? extends String> propertyNames = this.getPropertyNames();
    if (propertyNames != null && !propertyNames.isEmpty()) {
      returnValue = new LinkedHashMap<>();
      for (final String propertyName : propertyNames) {
        if (propertyName != null) {
          returnValue.put(propertyName, this.properties.getProperty(propertyName));
        }
      }
    }
    if (returnValue == null || returnValue.isEmpty()) {
      returnValue = Collections.emptyMap();
    } else {
      returnValue = Collections.unmodifiableMap(returnValue);
    }
    return returnValue;
  }

  @Override
  public Set<String> getPropertyNames() {
    final Set<String> returnValue;
    if (this.properties == null) {
      returnValue = Collections.emptySet();
    } else {
      returnValue = this.properties.stringPropertyNames();
    }
    return returnValue;
  }

  @Override
  public String getValue(final String name) {
    final String returnValue;
    if (this.properties == null) {
      returnValue = null;
    } else {
      returnValue = this.properties.getProperty(name);
    }
    return returnValue;
  }

}
