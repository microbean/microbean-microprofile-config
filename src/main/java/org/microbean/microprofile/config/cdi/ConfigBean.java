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
package org.microbean.microprofile.config.cdi;

import java.lang.annotation.Annotation;

import java.lang.reflect.Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.Dependent;

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;

import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

final class ConfigBean implements Bean<Config> {

  private final Set<Annotation> qualifiers;

  ConfigBean() {
    this(null);
  }
  
  ConfigBean(final Set<Annotation> qualifiers) {
    super();
    if (qualifiers == null || qualifiers.isEmpty()) {
      this.qualifiers = new HashSet<>();
    } else {
      this.qualifiers = new HashSet<>(qualifiers);
    }
    this.qualifiers.add(DefaultLiteral.INSTANCE);
    this.qualifiers.add(AnyLiteral.INSTANCE);
  }

  @Override
  public Config create(final CreationalContext<Config> cc) {
    return ConfigProvider.getConfig(Thread.currentThread().getContextClassLoader());
  }

  @Override
  public void destroy(final Config config, final CreationalContext<Config> cc) {
    if (config != null) {
      final ConfigProviderResolver configProviderResolver = ConfigProviderResolver.instance();
      assert configProviderResolver != null;
      configProviderResolver.releaseConfig(config);
    }
  }

  @Override
  public Class<?> getBeanClass() {
    return ConfigBean.class;
  }

  @Override
  public Set<InjectionPoint> getInjectionPoints() {
    return Collections.emptySet();
  }

  @Override
  public final String getName() {
    return null;
  }

  @Override
  public final Set<Annotation> getQualifiers() {
    return this.qualifiers;
  }

  @Override
  public final Class<? extends Annotation> getScope() {
    return Dependent.class;
  }

  @Override
  public final Set<Class<? extends Annotation>> getStereotypes() {
    return Collections.emptySet();
  }

  @Override
  public final Set<Type> getTypes() {
    return Collections.singleton(Config.class);
  }

  @Override
  public final boolean isAlternative() {
    return false;
  }

  @Override
  public final boolean isNullable() {
    return false;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(this.getClass().getName());
    sb.append(" ").append(this.getQualifiers());
    return sb.toString();
  }

}
