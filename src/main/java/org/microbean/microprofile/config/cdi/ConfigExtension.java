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
package org.microbean.microprofile.config.cdi;

import java.lang.annotation.Annotation;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedCallable;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessObserverMethod;

import org.eclipse.microprofile.config.Config;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * An {@link Extension} that enables injection of {@link
 * ConfigProperty}-annotated configuration property values.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public final class ConfigExtension implements Extension {

  private final Map<Set<Annotation>, Set<Type>> configPropertyTypes;

  private final Set<InjectionPoint> configPropertyInjectionPointsToValidate;

  private final Set<Set<Annotation>> allConfigQualifiers;

  /**
   * Creates a new {@link ConfigExtension}.
   */
  public ConfigExtension() {
    super();
    this.configPropertyInjectionPointsToValidate = new HashSet<>();
    this.configPropertyTypes = new HashMap<>();
    this.allConfigQualifiers = new HashSet<>();
  }

  private final <X> void processBean(@Observes final ProcessBean<X> event) {
    if (event != null) {
      final Bean<X> bean = event.getBean();
      if (bean != null) {
        final Set<InjectionPoint> beanInjectionPoints = bean.getInjectionPoints();
        if (beanInjectionPoints != null && !beanInjectionPoints.isEmpty()) {
          for (final InjectionPoint beanInjectionPoint : beanInjectionPoints) {
            if (beanInjectionPoint != null) {
              final Set<Annotation> qualifiers = beanInjectionPoint.getQualifiers();
              assert qualifiers != null;
              final Type type = beanInjectionPoint.getType();
              assert type != null;
              if (Config.class.equals(type)) {
                if (qualifiers != null && !qualifiers.isEmpty()) {
                  final Set<Annotation> configQualifiers = new HashSet<>(qualifiers);
                  if (configQualifiers.removeIf(q -> q instanceof ConfigProperty)) {
                    configQualifiers.add(ConfigPropertyLiteral.INSTANCE);
                  }
                  configQualifiers.add(AnyLiteral.INSTANCE);
                  allConfigQualifiers.add(configQualifiers);
                }
              } else {
                final Annotated annotated = beanInjectionPoint.getAnnotated();
                if (annotated != null && annotated.isAnnotationPresent(ConfigProperty.class)) {
                  this.configPropertyInjectionPointsToValidate.add(beanInjectionPoint);
                  Set<Annotation> configPropertyQualifiers = new HashSet<>(qualifiers);
                  configPropertyQualifiers.removeIf(q -> q instanceof ConfigProperty);
                  configPropertyQualifiers.add(ConfigPropertyLiteral.INSTANCE);
                  configPropertyQualifiers = Collections.unmodifiableSet(configPropertyQualifiers);
                  Set<Type> configPropertyTypes = this.configPropertyTypes.get(configPropertyQualifiers);
                  if (configPropertyTypes == null) {
                    configPropertyTypes = new HashSet<>();
                    this.configPropertyTypes.put(configPropertyQualifiers, configPropertyTypes);
                  }
                  configPropertyTypes.add(type);
                }
              }
            }
          }
        }
      }
    }
  }

  private final <T, X> void processObserverMethod(@Observes final ProcessObserverMethod<T, X> event,
                                                  final BeanManager beanManager) {
    if (event != null && beanManager != null) {
      final ObserverMethod<T> observerMethod = event.getObserverMethod();
      if (observerMethod != null) {
        final AnnotatedMethod<X> annotatedMethod = event.getAnnotatedMethod();
        if (annotatedMethod != null) {
          final List<AnnotatedParameter<X>> annotatedParameters = annotatedMethod.getParameters();
          if (annotatedParameters != null && annotatedParameters.size() > 1) {
            for (final AnnotatedParameter<X> annotatedParameter : annotatedParameters) {
              if (annotatedParameter != null &&
                  !annotatedParameter.isAnnotationPresent(Observes.class)) {
                final InjectionPoint injectionPoint = beanManager.createInjectionPoint(annotatedParameter);
                assert injectionPoint != null;
                final Type type = injectionPoint.getType();
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                if (Config.class.equals(type)) {
                  if (qualifiers != null && !qualifiers.isEmpty()) {
                    final Set<Annotation> configQualifiers = new HashSet<>(qualifiers);
                    if (configQualifiers.removeIf(q -> q instanceof ConfigProperty)) {
                      configQualifiers.add(ConfigPropertyLiteral.INSTANCE);
                    }
                    configQualifiers.add(AnyLiteral.INSTANCE);
                    allConfigQualifiers.add(Collections.unmodifiableSet(configQualifiers));
                  }
                } else if (annotatedParameter.isAnnotationPresent(ConfigProperty.class)) {
                  this.configPropertyInjectionPointsToValidate.add(injectionPoint);
                  Set<Annotation> configPropertyQualifiers = new HashSet<>(qualifiers);
                  configPropertyQualifiers.removeIf(q -> q instanceof ConfigProperty);
                  configPropertyQualifiers.add(ConfigPropertyLiteral.INSTANCE);
                  configPropertyQualifiers = Collections.unmodifiableSet(configPropertyQualifiers);
                  Set<Type> configPropertyTypes = this.configPropertyTypes.get(configPropertyQualifiers);
                  if (configPropertyTypes == null) {
                    configPropertyTypes = new HashSet<>();
                    this.configPropertyTypes.put(configPropertyQualifiers, configPropertyTypes);
                  }
                  configPropertyTypes.add(type);
                }
              }
            }
          }
        }
      }
    }
  }

  private final void afterBeanDiscovery(@Observes final AfterBeanDiscovery event,
                                        final BeanManager beanManager) {
    if (event != null) {

      final AnnotatedType<Config> configAnnotatedType = beanManager.createAnnotatedType(Config.class);
      assert configAnnotatedType != null;

      final Set<Annotation> defaultConfigQualifiers = new HashSet<>();
      defaultConfigQualifiers.add(AnyLiteral.INSTANCE);
      defaultConfigQualifiers.add(DefaultLiteral.INSTANCE);
      this.allConfigQualifiers.add(defaultConfigQualifiers);

      if (!this.configPropertyTypes.isEmpty()) {
        final Set<Entry<Set<Annotation>, Set<Type>>> entrySet = this.configPropertyTypes.entrySet();
        assert entrySet != null;
        assert !entrySet.isEmpty();
        for (final Entry<Set<Annotation>, Set<Type>> entry : entrySet) {
          assert entry != null;
          final Set<Annotation> qualifiers = entry.getKey();
          assert qualifiers != null;
          final Set<Type> types = entry.getValue();
          assert types != null;
          final Set<Type> newTypes = new HashSet<>(types);
          final Iterator<Type> iterator = newTypes.iterator();
          assert iterator != null;
          while (iterator.hasNext()) {
            final Type type = iterator.next();
            assert type != null;
            if (!noBeans(beanManager, type, qualifiers)) {
              // A user-supplied bean is already present for the type
              // and qualifiers so don't install a default bean.
              iterator.remove();
            }
          }
          event.addBean(new ConfigPropertyBean<>(newTypes, qualifiers));
        }
      }

      for (final Set<Annotation> configQualifiers : this.allConfigQualifiers) {
        if (noBeans(beanManager, Config.class, configQualifiers)) {
          event.addBean(new ConfigBean(configQualifiers));
        }
      }

    }
    this.allConfigQualifiers.clear();
  }

  private final void afterDeploymentValidation(@Observes final AfterDeploymentValidation event,
                                               final BeanManager beanManager) {
    if (event != null && beanManager != null) {
      final CreationalContext<?> cc = beanManager.createCreationalContext(null);
      try {
        for (final InjectionPoint injectionPoint : this.configPropertyInjectionPointsToValidate) {
          assert injectionPoint != null;
          if (beanManager.getInjectableReference(injectionPoint, cc) == null) {
            event.addDeploymentProblem(new DeploymentException("No value exists for the mandatory configuration property named " +
                                                               getConfigPropertyName(injectionPoint)));
          }
        }
      } finally {
        cc.release();
      }
    }
    this.configPropertyInjectionPointsToValidate.clear();
    this.configPropertyTypes.clear();
  }

  private static final boolean noBeans(final BeanManager beanManager, final Type type, final Set<Annotation> qualifiers) {
    Objects.requireNonNull(beanManager);
    Objects.requireNonNull(type);
    final Collection<?> beans;
    if (qualifiers == null || qualifiers.isEmpty()) {
      beans = beanManager.getBeans(type);
    } else {
      beans = beanManager.getBeans(type, qualifiers.toArray(new Annotation[qualifiers.size()]));
    }
    return beans == null || beans.isEmpty();
  }

  static final String getConfigPropertyName(final InjectionPoint injectionPoint) {
    return getConfigPropertyName(injectionPoint, null);
  }

  static final String getConfigPropertyName(final InjectionPoint injectionPoint, ConfigProperty configProperty) {
    String name = null;
    if (injectionPoint != null) {
      if (configProperty == null) {
        final Collection<? extends Annotation> qualifiers = injectionPoint.getQualifiers();
        if (qualifiers != null) {
          for (final Annotation qualifier : qualifiers) {
            if (qualifier != null && ConfigProperty.class.equals(qualifier.annotationType())) {
              configProperty = (ConfigProperty)qualifier;
              break;
            }
          }
        }
      }
      if (configProperty != null) {
        name = configProperty.name();
        assert name != null;
        if (name.isEmpty()) {
          final Annotated annotated = injectionPoint.getAnnotated();
          if (annotated instanceof AnnotatedField) {
            final AnnotatedField<?> field = (AnnotatedField<?>)annotated;
            final Member member = field.getJavaMember();
            if (member != null) {
              final AnnotatedType<?> declaringType = field.getDeclaringType();
              if (declaringType != null) {
                name = declaringType.getJavaClass().getCanonicalName() + "." + member.getName();
              }
            }
          } else if (annotated instanceof AnnotatedParameter) {
            final AnnotatedParameter<?> annotatedParameter = (AnnotatedParameter<?>)annotated;
            // The specification says: "[The ConfigProperty name that
            // will be synthesized if one is not provided] will be
            // derived automatically as
            // <class_name>.<injetion_point_name> [sic], where
            // injection_point_name [sic] is the field name or
            // parameter name, class_name is the fully qualified name
            // of the class being injected to [sic]."
            final AnnotatedCallable<?> declaringCallable = annotatedParameter.getDeclaringCallable();
            if (declaringCallable != null) {
              final Member member = declaringCallable.getJavaMember();
              assert member instanceof Executable;
              final Executable executable = (Executable)member;
              final int position = annotatedParameter.getPosition();
              final Parameter[] parameters = executable.getParameters();
              if (parameters != null && parameters.length > position) {
                final Parameter parameter = parameters[position];
                assert parameter != null;
                if (parameter.isNamePresent()) {
                  final AnnotatedType<?> declaringType = declaringCallable.getDeclaringType();
                  if (declaringType != null) {
                    name = declaringType.getJavaClass().getCanonicalName() + "." + parameter.getName();
                  }
                }
              }
            }
          } else {
            assert false;
          }
        }
      }
    }
    return name;
  }

}
