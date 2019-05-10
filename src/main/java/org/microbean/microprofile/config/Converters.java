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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Priority;

import org.eclipse.microprofile.config.spi.Converter;

final class Converters {

  private Converters() {
    super();
  }

  static final Type getConversionType(final Converter<?> converter) {
    final Type returnValue;
    if (converter instanceof PrioritizedConverter) {
      returnValue = ((PrioritizedConverter<?>)converter).getConversionType();
    } else {
      returnValue = getConversionType(Objects.requireNonNull(converter).getClass());
    }
    return returnValue;
  }

  static final int getPriority(final Converter<?> converter) {
    int returnValue = 100; // per specification by default
    if (converter != null) {
      if (converter instanceof PrioritizedConverter) {
        returnValue = ((PrioritizedConverter)converter).getPriority();
      } else {
        final Integer priorityInteger = getPriority(converter.getClass());
        if (priorityInteger != null) {
          returnValue = priorityInteger.intValue();
        }
      }
    }
    return returnValue;
  }

  private static final Integer getPriority(final AnnotatedElement annotatedElement) {
    Integer returnValue = null;
    if (annotatedElement != null) {
      final Priority priority = annotatedElement.getAnnotation(Priority.class);
      if (priority != null) {
        returnValue = Integer.valueOf(priority.value());
      }
    }
    return returnValue;
  }

  static final Type getConversionType(final Type type) {
    return getConversionType(type, null, null);
  }

  private static final Type getConversionType(final Type type, Set<Type> seen, Map<TypeVariable<?>, Type> reifiedTypes) {
    Type returnValue = null;
    if (type != null) {
      if (seen == null) {
        seen = new HashSet<>();
      }
      if (reifiedTypes == null) {
        reifiedTypes = new HashMap<>();
      }
      if (!seen.contains(type)) {
        seen.add(type);

        if (type instanceof Class) {
          final Class<?> c = (Class<?>)type;
          if (Converter.class.isAssignableFrom(c)) {
            final Type[] genericInterfaces = c.getGenericInterfaces();
            assert genericInterfaces != null;
            for (final Type genericInterface : genericInterfaces) {
              returnValue = getConversionType(genericInterface, seen, reifiedTypes); // XXX recursive call
              if (returnValue != null) {
                break;
              }
            }
            if (returnValue == null) {
              returnValue = getConversionType(c.getSuperclass(), seen, reifiedTypes); // XXX recursive call
            }
          }

        } else if (type instanceof ParameterizedType) {
          final ParameterizedType pt = (ParameterizedType)type;
          final Type rawType = pt.getRawType();
          assert rawType instanceof GenericDeclaration;
          final TypeVariable<?>[] typeParameters = ((GenericDeclaration)rawType).getTypeParameters();
          assert typeParameters != null;
          final Type[] actualTypeArguments = pt.getActualTypeArguments();
          assert actualTypeArguments != null;
          assert actualTypeArguments.length == typeParameters.length;
          if (actualTypeArguments.length > 0) {
            for (int i = 0; i < actualTypeArguments.length; i++) {
              Type actualTypeArgument = actualTypeArguments[i];
              if (actualTypeArgument instanceof Class ||
                  actualTypeArgument instanceof ParameterizedType) {
                reifiedTypes.put(typeParameters[i], actualTypeArgument);
              } else if (actualTypeArgument instanceof TypeVariable) {
                final Type reifiedType = reifiedTypes.get((TypeVariable)actualTypeArgument);
                if (reifiedType == null) {
                  reifiedTypes.put((TypeVariable)actualTypeArgument, Object.class);
                  actualTypeArgument = Object.class;
                } else {
                  actualTypeArgument = reifiedType;
                }
                assert actualTypeArgument != null;
                assert (actualTypeArgument instanceof ParameterizedType || actualTypeArgument instanceof Class) : "Unexpected actualTypeArgument: " + actualTypeArgument;
                reifiedTypes.put(typeParameters[i], actualTypeArgument);
              }
            }
          }
          if (Converter.class.equals(rawType)) {
            assert actualTypeArguments.length == 1;
            final Type typeArgument = actualTypeArguments[0];
            if (typeArgument instanceof Class ||
                typeArgument instanceof ParameterizedType) {
              returnValue = typeArgument;
            } else if (typeArgument instanceof TypeVariable) {
              final TypeVariable<?> typeVariable = (TypeVariable<?>)typeArgument;
              returnValue = reifiedTypes.get(typeVariable);
              assert returnValue instanceof ParameterizedType || returnValue instanceof Class : "Unexpected returnValue: " + returnValue;
            } else {
              throw new IllegalArgumentException("Unhandled conversion type: " + typeArgument);
            }
          } else {
            returnValue = getConversionType(rawType, seen, reifiedTypes); // XXX recursive call
          }

        } else {
          throw new IllegalArgumentException("Unhandled type: " + type);
        }

      }
    }
    return returnValue;
  }

}
