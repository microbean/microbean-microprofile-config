/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean.
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

import java.io.Serializable;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

class SystemPropertiesConfigSource extends AbstractConfigSource {

  private static final long serialVersionUID = 1L;
  
  SystemPropertiesConfigSource() {
    // The specification does not provide guidance on name choices.
    super("System properties", 400); // 400 == from specification
  }

  @Override
  public Map<String, String> getProperties() {
    final Properties systemProperties = AccessController.doPrivileged((PrivilegedAction<Properties>)() -> System.getProperties());
    assert systemProperties != null;
    final Map<String, String> returnValue = new LinkedHashMap<>();
    final Collection<? extends String> systemPropertyNames = systemProperties.stringPropertyNames();
    assert systemPropertyNames != null;
    for (final String name : systemPropertyNames) {
      assert name != null;
      returnValue.put(name, systemProperties.getProperty(name));
    }
    return Collections.unmodifiableMap(returnValue);
  }

  @Override
  public String getValue(final String name) {
    return AccessController.doPrivileged((PrivilegedAction<String>)() -> System.getProperty(name));
  }
  
}
