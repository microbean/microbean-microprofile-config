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

import java.util.Objects;

import org.eclipse.microprofile.config.spi.ConfigSource;

abstract class AbstractConfigSource implements ConfigSource, Serializable {

  private static final long serialVersionUID = 1L;
  
  private final String name;

  private final Integer ordinal;

  AbstractConfigSource(final String name) {
    this(name, null);
  }

  AbstractConfigSource(final String name, final int ordinal) {
    this(name, Integer.valueOf(ordinal));
  }
  
  AbstractConfigSource(final String name, final Integer ordinal) {
    super();
    this.name = Objects.requireNonNull(name);
    this.ordinal = ordinal;
  }

  @Override
  public final String getName() {
    return this.name;
  }

  @Override
  public final int getOrdinal() {
    final int returnValue;
    if (this.ordinal == null) {
      returnValue = ConfigSource.super.getOrdinal();
    } else {
      returnValue = this.ordinal.intValue();
    }
    return returnValue;
  }

}
