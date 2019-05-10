/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018 microBean.
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

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.Map;

import java.util.regex.Pattern;

class EnvironmentVariablesConfigSource extends AbstractConfigSource {

  private static final long serialVersionUID = 1L;

  private static final Pattern toUnderscorePattern = Pattern.compile("[^a-zA-Z0-9_]");

  EnvironmentVariablesConfigSource() {
    // The specification does not provide guidance on name choices.
    super("Environment", 300); // 300 == from specification
  }

  @Override
  public Map<String, String> getProperties() {
    return AccessController.doPrivileged((PrivilegedAction<Map<String, String>>)() -> System.getenv());
  }

  @Override
  public String getValue(final String name) {
    return AccessController.doPrivileged((PrivilegedAction<String>)() -> {
        String value = System.getenv(name);
        if (value == null) {
          final String doctoredName = toUnderscorePattern.matcher(name).replaceAll("_");
          value = System.getenv(doctoredName);
          if (value == null) {
            value = System.getenv(doctoredName.toUpperCase());
          }
        }
        return value;
      });
  }

}
