/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018­2019 microBean™.
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

import java.lang.reflect.Member;
import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.Dependent;

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;

import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.config.Config;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.microbean.microprofile.config.TypeConverter;

final class ConfigPropertyBean<T> implements Bean<T>, PassivationCapable {

  private static final Set<InjectionPoint> emptyInjectionPointSet = Collections.emptySet();

  private static final Set<Class<? extends Annotation>> emptyStereotypesSet = Collections.emptySet();

  private static final String[] emptyStringArray = new String[0];

  private final String id;
  
  private final Set<Type> types;

  private final Set<Annotation> qualifiers;

  private final Annotation[] configQualifiers;

  ConfigPropertyBean(final Type type, final Set<? extends Annotation> qualifiers) {
    this(type == null ? null : Collections.singleton(type), qualifiers);
  }
  
  ConfigPropertyBean(final Set<? extends Type> types, final Set<? extends Annotation> qualifiers) {
    super();
    if (types == null || types.isEmpty()) {
      this.types = Collections.emptySet();
    } else {
      this.types = Collections.unmodifiableSet(new HashSet<>(types));
    }
    final Set<Annotation> newQualifiers = new HashSet<>();
    newQualifiers.add(AnyLiteral.INSTANCE);
    if (qualifiers == null || qualifiers.isEmpty()) {
      // ConfigProperty elements are @Nonbinding
      newQualifiers.add(ConfigPropertyLiteral.INSTANCE);
      newQualifiers.add(DefaultLiteral.INSTANCE);
      this.configQualifiers = null;
    } else {
      final Set<Annotation> configQualifiers = new HashSet<>(qualifiers);
      newQualifiers.addAll(qualifiers);
      boolean found = false;
      for (final Annotation qualifier : qualifiers) {
        if (qualifier instanceof ConfigProperty) {
          found = true;
          configQualifiers.removeIf(q -> q instanceof ConfigProperty);
          break;
        }
      }
      if (!found) {
        // ConfigProperty elements are @Nonbinding
        newQualifiers.add(ConfigPropertyLiteral.INSTANCE);
      }
      this.configQualifiers = configQualifiers.toArray(new Annotation[configQualifiers.size()]);
    }
    this.qualifiers = Collections.unmodifiableSet(newQualifiers);
    this.id = new StringBuilder(this.getClass().getName()).append(";t:").append(this.getTypes()).append(";q:").append(this.getQualifiers()).toString();
  }
  
  @Override
  public final T create(final CreationalContext<T> context) {
    // This ugly construct is needed to emulate the injection of the
    // current InjectionPoint.  Do not get clever and try to do this
    // differently.
    final BeanManager beanManager = CDI.current().getBeanManager();
    assert beanManager != null;
    final InjectionPoint currentInjectionPoint = (InjectionPoint)beanManager.getInjectableReference(new CurrentInjectionPoint(InjectionPoint.class), context);
    assert currentInjectionPoint != null;
    final Set<Bean<?>> configBeans;
    if (this.configQualifiers == null) {
      configBeans = beanManager.getBeans(Config.class);
    } else {
      Set<Bean<?>> beans = beanManager.getBeans(Config.class, this.configQualifiers);
      if (beans == null || beans.isEmpty()) {
        configBeans = beanManager.getBeans(Config.class);
      } else {
        configBeans = beans;
      }
    }
    assert configBeans != null;
    final Config config = (Config)beanManager.getReference(beanManager.resolve(configBeans), Config.class, context);
    assert config != null;
    final String value = getValue(config, currentInjectionPoint);
    final TypeConverter typeConverter;
    if (config instanceof TypeConverter) {
      typeConverter = (TypeConverter)config;
    } else {
      final Set<Bean<?>> typeConverterBeans;
      if (this.configQualifiers == null) {
        typeConverterBeans = beanManager.getBeans(TypeConverter.class);
      } else {
        typeConverterBeans = beanManager.getBeans(TypeConverter.class, this.configQualifiers);
      }
      typeConverter = (TypeConverter)beanManager.getReference(beanManager.resolve(typeConverterBeans), TypeConverter.class, context);
    }
    final T returnValue = typeConverter.convert(value == null ? getDefaultValue(currentInjectionPoint) : value, currentInjectionPoint.getType());
    return returnValue;
  }

  
  @Override
  public final void destroy(final T configurationValue, final CreationalContext<T> creationalContext) {
    try {
      if (configurationValue instanceof AutoCloseable) {
        try {
          ((AutoCloseable)configurationValue).close();
        } catch (final InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
        } catch (final RuntimeException throwMe) {
          throw throwMe;
        } catch (final Exception exception) {
          throw new RuntimeException(exception.getMessage(), exception);
        }
      }
    } finally {
      if (creationalContext != null) {
        creationalContext.release();
      }
    }
  }
  
  @Override
  public final Set<InjectionPoint> getInjectionPoints() {
    return emptyInjectionPointSet;
  }
  
  @Override
  public final Class<?> getBeanClass() {
    return ConfigPropertyBean.class;
  }
  
  @Override
  public final boolean isNullable() {
    return false;
  }

  @Override
  public final Set<Type> getTypes() {
    return this.types;
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
  public final String getName() {
    return null;
  }
  
  @Override
  public final Set<Class<? extends Annotation>> getStereotypes() {
    return emptyStereotypesSet;
  }
  
  @Override
  public final boolean isAlternative() {
    return false;
  }
  
  public final String getId() {
    return this.id;
  }

  private static final String getDefaultValue(final InjectionPoint injectionPoint) {
    Objects.requireNonNull(injectionPoint);

    ConfigProperty configProperty = null;
    final Collection<? extends Annotation> qualifiers = injectionPoint.getQualifiers();
    if (qualifiers != null) {
      for (final Annotation qualifier : qualifiers) {
        if (qualifier != null && ConfigProperty.class.equals(qualifier.annotationType())) {
          configProperty = (ConfigProperty)qualifier;
          break;
        }
      }
    }

    final String returnValue;
    if (configProperty == null) {
      returnValue = null;
    } else {
      final String defaultValue = configProperty.defaultValue();
      assert defaultValue != null;
      if (defaultValue.equals(ConfigProperty.UNCONFIGURED_VALUE)) {
        returnValue = null;
      } else {
        returnValue = defaultValue;
      }
    }

    return returnValue;
  }
  
  private static final String getValue(final Config config, final InjectionPoint injectionPoint) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(injectionPoint);

    String returnValue = null;
    
    final String name = ConfigExtension.getConfigPropertyName(injectionPoint);

    if (name != null && !name.isEmpty()) {
      final Optional<String> optionalValue = config.getOptionalValue(name, String.class);
      if (optionalValue.isPresent()) {
        returnValue = optionalValue.get();
      }
    }

    return returnValue;
  }
  
  private static final class ConfigPropertyLiteral extends AnnotationLiteral<ConfigProperty> implements ConfigProperty {

    private static final long serialVersionUID = 1L;

    private static final ConfigProperty INSTANCE = new ConfigPropertyLiteral();
    
    private ConfigPropertyLiteral() {
      super();
    }
    
    @Override
    public final String name() {
      return "";
    }
    
    @Override
    public final String defaultValue() {
      return "";
    }
  }

  private static final class CurrentInjectionPoint implements InjectionPoint {

    private final Type type;
    
    private CurrentInjectionPoint(final Type type) {
      super();
      this.type = type;
    }

    @Override
    public final Type getType() {
      return this.type;
    }

    @Override
    public final Set<Annotation> getQualifiers() {
      return Collections.singleton(DefaultLiteral.INSTANCE);
    }

    @Override
    public final Bean<?> getBean() {
      return null;
    }

    @Override
    public final Member getMember() {
      return null;
    }

    @Override
    public final Annotated getAnnotated() {
      return null;
    }

    @Override
    public final boolean isDelegate() {
      return false;
    }

    @Override
    public final boolean isTransient() {
      return false;
    }
    
  }

}
