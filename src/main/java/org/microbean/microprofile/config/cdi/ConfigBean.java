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

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;

import javax.inject.Singleton;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

final class ConfigBean implements Bean<Config>, PassivationCapable {

  private static final Set<InjectionPoint> emptyInjectionPointSet = Collections.emptySet();

  private static final Set<Class<? extends Annotation>> emptyStereotypesSet = Collections.emptySet();

  private static final Set<Type> configTypes = Collections.singleton(Config.class);
  
  private final String id;
  
  private final Set<Annotation> qualifiers;

  ConfigBean() {
    this(null);
  }
  
  ConfigBean(final Set<Annotation> qualifiers) {
    super();
    final Set<Annotation> myQualifiers;
    if (qualifiers == null || qualifiers.isEmpty()) {
      myQualifiers = new HashSet<>();
    } else {
      myQualifiers = new HashSet<>(qualifiers);
    }
    myQualifiers.add(DefaultLiteral.INSTANCE);
    myQualifiers.add(AnyLiteral.INSTANCE);    
    this.qualifiers = Collections.unmodifiableSet(myQualifiers);
    this.id = new StringBuilder(this.getClass().getName()).append(";t:").append(this.getTypes()).append(";q:").append(this.getQualifiers()).toString();
  }

  @Override
  public final Config create(final CreationalContext<Config> cc) {
    final Config config = ConfigProvider.getConfig();
    assert config != null;
    return config;
  }

  @Override
  public void destroy(final Config config, final CreationalContext<Config> cc) {
    try {
      if (config != null) {
        ConfigProviderResolver.instance().releaseConfig(config);
      }
    } finally {
      if (cc != null) {
        cc.release();
      }
    }
  }

  @Override
  public final Class<?> getBeanClass() {
    return ConfigBean.class;
  }

  @Override
  public final String getId() {
    return this.id;
  }

  @Override
  public final Set<InjectionPoint> getInjectionPoints() {
    return emptyInjectionPointSet;
  }

  @Override
  public final String getName() {
    return "config";
  }

  @Override
  public final Set<Annotation> getQualifiers() {
    return this.qualifiers;
  }

  @Override
  public final Class<? extends Annotation> getScope() {
    // Note: do not get clever and try to change this to
    // ApplicationScoped.  MicroProfile Config-mandated validation of
    // injection points will fail in ConfigExtension because at the
    // time of specification-mandated validation the
    // ApplicationContext is not active yet, whereas DependentContext
    // and SingletonContext are, so you can't call
    // beanManager.getInjectableReference(Config.class).
    //
    // You also don't want to change this to Dependent.class, the
    // naive approach, because you then end up in a situation where
    // you don't want to destroy the instance ever, but if you do
    // that, then you leak Config instances.
    //
    // The worst case scenario with Singleton.class is that everything
    // works properly until someone decides to call
    // ConfigProviderResolver.instance().releaseConfig(ourInstance)
    // for some reason.  This will close the Config (if it implements
    // AutoCloseable, as ours does, so as to honor the other seemingly
    // contradictory part of the specification which says all
    // ConfigSources and Converters must be closed if possible) but
    // any references to that Config will be effectively unusable
    // since it will be (irrevocably) closed.  That may be
    // unavoidable.
    //
    // These are all inherent flaws within the specification and the
    // least evil tradeoff was made here.
    return Singleton.class;
  }

  @Override
  public final Set<Class<? extends Annotation>> getStereotypes() {
    return emptyStereotypesSet;
  }

  @Override
  public final Set<Type> getTypes() {
    return configTypes;
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
  public final String toString() {
    return new StringBuilder(this.getClass().getName()).append(" ").append(this.getQualifiers()).toString();
  }

}
