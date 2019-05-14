/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2019 microBean.
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

import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestMicroProfileConfigProperties {

  private static Properties old;
  
  @BeforeClass
  public static void install() {
    old = System.getProperties();
    MicroProfileConfigProperties.installAsSystemProperties();
  }

  @AfterClass
  public static void uninstall() {
    System.setProperties(old);
  }

  @Test
  public void testGet() {
    assertNotNull(System.getProperty("java.home"));
  }
  
  @Test
  public void testSystemPropertiesBehavior() {
    final Properties properties = System.getProperties();
    assertTrue(properties.containsKey("java.home"));
  }
  
}
