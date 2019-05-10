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

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import java.util.regex.Pattern;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * A {@link Serializable}, {@link Closeable} {@link TypeConverter}
 * implementation that is based on a collection of {@link Converter}s.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #convert(String, Type)
 */
public class ConversionHub implements Closeable, Serializable, TypeConverter {

  private static final long serialVersionUID = 1L;

  private static final Pattern splitPattern = Pattern.compile("(?<!\\\\),");

  private static final Pattern backslashCommaPattern = Pattern.compile("\\\\,");

  private static final Map<Class<?>, Class<?>> wrapperClasses;
  
  static {
    wrapperClasses = new HashMap<>();
    wrapperClasses.put(boolean.class, Boolean.class);
    wrapperClasses.put(byte.class, Byte.class);
    wrapperClasses.put(char.class, Character.class);
    wrapperClasses.put(double.class, Double.class);
    wrapperClasses.put(float.class, Float.class);
    wrapperClasses.put(int.class, Integer.class);
    wrapperClasses.put(long.class, Long.class);
    wrapperClasses.put(short.class, Short.class);
  }
  
  private final Map<Type, Converter<?>> converters;

  private volatile boolean closed;

  /**
   * Creates a new {@link ConversionHub}.
   */
  public ConversionHub() {
    super();
    final Map<? extends Type, ? extends Converter<?>> discoveredConverters = getDiscoveredConverters(null);
    this.converters = new HashMap<>(discoveredConverters);
  }
  
  /**
   * Creates a new {@link ConversionHub}.
   *
   * <h2>Thread Safety</h2>
   *
   * <p><strong>{@code converters} will be sychronized on and iterated
   * over by this constructor</strong>, which may have implications on
   * the type of {@link Map} supplied.</p>
   *
   * @param converters a {@link Map} of {@link Converter} instances,
   * indexed by the {@link Type} describing the type of the return
   * value of their respective {@link Converter#convert(String)}
   * methods; may be {@code null}; <strong>will be synchronized on and
   * iterated over</strong>; copied by value; no reference is kept to
   * this object
   */
  public ConversionHub(final Map<? extends Type, ? extends Converter<?>> converters) {
    super();
    if (converters == null) {
      this.converters = new HashMap<>();
    } else {
      synchronized (converters) {
        this.converters = new HashMap<>(converters);
      }
    }
  }

  /**
   * Closes this {@link ConversionHub} using a best-effort strategy.
   *
   * <p>This method attempts to close each of this {@link
   * ConversionHub}'s associated {@link Closeable} {@link Converter}s.
   * Any {@link IOException} thrown during such an attempt does not
   * abort the closing process.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @exception IOException if at least one underlying {@link
   * Closeable} {@link Converter} could not be closed
   */
  @Override
  public void close() throws IOException {
    /*
      The specification says:

      "A factory method ConfigProviderResolver#releaseConfig(Config
      config) to release the Config instance [sic]. This will unbind
      the current Config from the application. The ConfigSources that
      implement the java.io.Closeable interface will be properly
      destroyed. The Converters that implement the java.io.Closeable
      interface will be properly destroyed."
      
      It is not clear which ConfigSources and which Converters are
      meant here, but assuming they are only those ones present "in"
      the Config being released, there's no way to "get" those from a
      given Config, since (a) there is no requirement that a Config
      actually house Converters and (b) consequently there is nothing
      like a Config#getConverters() method.

      So we implement Closeable to at least provide the ability to
      close everything cleanly and in a thread-safe manner.
    */

    IOException throwMe = null;
    synchronized (this.converters) {
      if (!this.converters.isEmpty()) {
        final Collection<? extends Converter<?>> converters = this.converters.values();
        assert converters != null;
        assert !converters.isEmpty();
        for (final Converter<?> converter : converters) {
          if (converter instanceof Closeable) {
            try {
              ((Closeable)converter).close();
            } catch (final IOException ioException) {
              if (throwMe == null) {
                throwMe = ioException;
              } else {
                throwMe.addSuppressed(ioException);
              }
            }
          }
        }
      }
    }
    if (throwMe != null) {
      throw throwMe;
    }
    this.closed = true;
  }

  /**
   * Attempts to convert the supplied {@link String} value to an
   * object assignable to the supplied {@link Type}, throwing an
   * {@link IllegalArgumentException} if such conversion is
   * impossible.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple
   * threads.</p>
   *
   * @param value the value to convert; may be {@code null}
   *
   * @param type the {@link Type} to which the value should be
   * converted; must not be {@code null}; the type of the return value
   * resulting from invocations this method should be assignable to
   * references of this type
   *
   * @return the converted object
   *
   * @exception IllegalArgumentException if conversion could not occur
   * for any reason
   *
   * @exception IllegalStateException if this {@link ConversionHub}
   * was {@linkplain #close() closed}
   */
  @Override
  @SuppressWarnings("unchecked")
  public final <T> T convert(final String value, final Type type) {
    if (this.closed) {
      throw new IllegalStateException();
    }
    Converter<T> converter;
    synchronized (this.converters) {
      converter = (Converter<T>)this.converters.get(type);
      if (converter == null) {
        try {
          converter = this.computeConverter(type);
        } catch (final ReflectiveOperationException reflectiveOperationException) {
          throw new IllegalArgumentException(reflectiveOperationException.getMessage(), reflectiveOperationException);
        }
        if (converter != null) {
          this.converters.put(type, converter);
        }
      }
    }
    if (converter == null) {
      throw new IllegalArgumentException("\"" + value + "\" could not be converted to " + (type == null ? "null" : type.getTypeName()));
    }
    final T returnValue = converter.convert(value);
    return returnValue;      
  }

  @SuppressWarnings("unchecked")
  private final <T> Converter<T> computeConverter(final Type conversionType) throws ReflectiveOperationException {
    Converter<T> returnValue = null;
    if (CharSequence.class.equals(conversionType) || String.class.equals(conversionType)) {
      returnValue = new SerializableConverter<T>() {
          private static final long serialVersionUID = 1L;
          @Override
          public final T convert(final String rawValue) {
            return (T)rawValue;
          }
        };
      
    } else if (Boolean.class.equals(conversionType) || boolean.class.equals(conversionType)) {
      returnValue = new SerializableConverter<T>() {
          private static final long serialVersionUID = 1L;
          @Override
          public final T convert(final String rawValue) {
            return (T)Boolean.valueOf(rawValue != null &&
                                      ("true".equalsIgnoreCase(rawValue) ||
                                       "y".equalsIgnoreCase(rawValue) ||
                                       "yes".equalsIgnoreCase(rawValue) ||
                                       "on".equalsIgnoreCase(rawValue) ||
                                       "1".equals(rawValue)));
          }
        };
           
    } else if (URL.class.equals(conversionType)) {
      returnValue = new SerializableConverter<T>() {
          private static final long serialVersionUID = 1L;
          @Override
          public final T convert(final String rawValue) {
            try {
              return (T)URI.create(rawValue).toURL();
            } catch (final MalformedURLException malformedUrlException) {
              throw new IllegalArgumentException(malformedUrlException.getMessage(), malformedUrlException);
            }
          }
        };
      
    } else if (Class.class.equals(conversionType)) {
      returnValue = new SerializableConverter<T>() {
          private static final long serialVersionUID = 1L;
          @Override
          public final T convert(final String rawValue) {
            try {
              // Seems odd that the specification mandates the use of the
              // single-argument Class#forName(String) method, but it's
              // spelled out in black and white.
              return (T)Class.forName(rawValue);
            } catch (final ClassNotFoundException classNotFoundException) {
              throw new IllegalArgumentException(classNotFoundException.getMessage(), classNotFoundException);
            }
          }
        };
      
    } else if (conversionType instanceof ParameterizedType) {
      final ParameterizedType parameterizedType = (ParameterizedType)conversionType;
      final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
      assert actualTypeArguments != null;
      assert actualTypeArguments.length > 0;
      final Type rawType = parameterizedType.getRawType();
      assert rawType instanceof Class : "!(parameterizedType.getRawType() instanceof Class): " + rawType;
      final Class<?> conversionClass = (Class<?>)rawType;
      assert !conversionClass.isArray();
      
      if (Optional.class.isAssignableFrom(conversionClass)) {
        assert actualTypeArguments.length == 1;
        final Type firstTypeArgument = actualTypeArguments[0];
        returnValue = new SerializableConverter<T>() {
            private static final long serialVersionUID = 1L;
            @Override
            public final T convert(final String rawValue) {
              return (T)Optional.ofNullable(ConversionHub.this.convert(rawValue, firstTypeArgument)); // XXX recursive call
            }
          };
        
      } else if (Class.class.isAssignableFrom(conversionClass)) {
        returnValue = new SerializableConverter<T>() {
            private static final long serialVersionUID = 1L;
            @Override
            public final T convert(final String rawValue) {
              return ConversionHub.this.convert(rawValue, conversionClass); // XXX recursive call
            }
          };
        
      } else if (Collection.class.isAssignableFrom(conversionClass)) {
        returnValue = new SerializableConverter<T>() {
            private static final long serialVersionUID = 1L;
            @Override
            public final T convert(final String rawValue) {
              Collection<Object> container = null;
              if (conversionClass.isInterface()) {
                if (Set.class.isAssignableFrom(conversionClass)) {
                  container = new HashSet<>();
                } else {
                  container = new ArrayList<>();
                }
              } else {
                try {
                  container = (Collection<Object>)conversionClass.getDeclaredConstructor().newInstance();
                } catch (final ReflectiveOperationException reflectiveOperationException) {
                  throw new IllegalArgumentException(reflectiveOperationException.getMessage(), reflectiveOperationException);
                }
              }
              assert container != null;
              final Type firstTypeArgument = actualTypeArguments[0];
              final String[] parts = split(rawValue);
              assert parts != null;
              assert parts.length > 0;
              for (final String part : parts) {
                final Object scalar = ConversionHub.this.convert(part, firstTypeArgument); // XXX recursive call
                container.add(scalar);
              }
              final T temp = (T)container;
              return temp;
            }
          };
      } else {
        throw new IllegalArgumentException("Unhandled conversion type: " + conversionType);
      }
      
    } else if (conversionType instanceof Class) {
      final Class<?> conversionClass = (Class<?>)conversionType;
      if (conversionClass.isArray()) {
        returnValue = new SerializableConverter<T>() {
            private static final long serialVersionUID = 1L;
            @Override
            public final T convert(final String rawValue) {
              final String[] parts = split(rawValue);
              assert parts != null;
              T container = (T)Array.newInstance(conversionClass.getComponentType(), parts.length);
              for (int i = 0; i < parts.length; i++) {
                final Object scalar = ConversionHub.this.convert(parts[i], conversionClass.getComponentType()); // XXX recursive call
                Array.set(container, i, scalar);
              }
              return container;
            }
          };
        
      } else {
        final Class<?> cls;
        if (conversionClass.isPrimitive()) {
          cls = wrapperClasses.get(conversionClass);
          assert cls != null;
        } else {
          cls = conversionClass;
        }
        returnValue = getConverterFromStaticMethod(cls, "of", String.class);
        if (returnValue == null) {
          returnValue = getConverterFromStaticMethod(cls, "of", CharSequence.class);
          if (returnValue == null) {
            returnValue = getConverterFromStaticMethod(cls, "valueOf", String.class);
            if (returnValue == null) {
              returnValue = getConverterFromStaticMethod(cls, "valueOf", CharSequence.class);
              if (returnValue == null) {
                returnValue = getConverterFromConstructor((Class<T>)cls, String.class);
                if (returnValue == null) {
                  returnValue = getConverterFromConstructor((Class<T>)cls, CharSequence.class);
                  if (returnValue == null) {
                    returnValue = getConverterFromStaticMethod(cls, "parse", String.class);
                    if (returnValue == null) {
                      returnValue = getConverterFromStaticMethod(cls, "parse", CharSequence.class);
                      if (returnValue == null) {
                        final PropertyEditor editor = PropertyEditorManager.findEditor(cls);
                        if (editor != null) {
                          returnValue = new PropertyEditorConverter<T>(cls, editor);
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      returnValue = null;
    }
    return returnValue;
  }

  private static final <T> Converter<T> getConverterFromStaticMethod(Class<?> methodHostClass, final String methodName, final Class<? extends CharSequence> soleParameterType) {
    Objects.requireNonNull(methodHostClass);
    Objects.requireNonNull(methodName);
    Objects.requireNonNull(soleParameterType);
    if (methodHostClass.isArray()) {
      throw new IllegalArgumentException("methodHostClass.isArray(): " + methodHostClass.getName());
    } else if (methodHostClass.isPrimitive()) {
      throw new IllegalArgumentException("methodHostClass.isPrimitive(): " + methodHostClass.getName());
    }
    Converter<T> returnValue = null;
    final Method method;
    Method temp = null;
    try {
      temp = methodHostClass.getMethod(methodName, soleParameterType);
    } catch (final NoSuchMethodException noSuchMethodException) {
      
    } finally {
      method = temp;
    }
    if (method != null && Modifier.isStatic(method.getModifiers()) && methodHostClass.isAssignableFrom(method.getReturnType())) {
      returnValue = new ExecutableBasedConverter<>(method);
    }
    return returnValue;
  }

  private static final <T> Converter<T> getConverterFromConstructor(Class<T> constructorHostClass, final Class<? extends CharSequence> soleParameterType) {
    Objects.requireNonNull(constructorHostClass);
    Objects.requireNonNull(soleParameterType);
    if (constructorHostClass.isPrimitive()) {
      throw new IllegalArgumentException("constructorHostClass.isPrimitive(): " + constructorHostClass.getName());
    } else if (constructorHostClass.isArray()) {
      throw new IllegalArgumentException("constructorHostClass.isArray(): " + constructorHostClass.getName());
    }

    Converter<T> returnValue = null;
    final Constructor<T> constructor;
    Constructor<T> temp = null;
    try {
      temp = constructorHostClass.getConstructor(soleParameterType);
    } catch (final NoSuchMethodException noSuchMethodException) {

    } finally {
      constructor = temp;
    }
    if (constructor != null) {
      returnValue = new ExecutableBasedConverter<>(constructor);
    }
    return returnValue;
  }

  static final String[] split(final String text) {
    final String[] returnValue;
    if (text == null) {
      returnValue = new String[0];
    } else {
      returnValue = splitPattern.split(text);
      assert returnValue != null;
      for (int i = 0; i < returnValue.length; i++) {
        returnValue[i] = backslashCommaPattern.matcher(returnValue[i]).replaceAll(",");
      }
    }
    return returnValue;
  }

  static final Map<? extends Type, ? extends Converter<?>> getDiscoveredConverters(ClassLoader classLoader) {
    if (classLoader == null) {
      classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
    }
    final Map<Type, Converter<?>> converters = new HashMap<>();
    @SuppressWarnings("rawtypes")
    final ServiceLoader<Converter> discoveredConverters = ServiceLoader.load(Converter.class, classLoader);
    assert discoveredConverters != null;
    for (final Converter<?> discoveredConverter : discoveredConverters) {
      if (discoveredConverter != null) {
        final Type conversionType = Converters.getConversionType(discoveredConverter);
        if (conversionType == null) {
          throw new IllegalStateException("Could not determine the conversion type for converter: " + discoveredConverter);
        }
        converters.put(conversionType, discoveredConverter);
      }
    }
    return Collections.unmodifiableMap(converters);
  }

  private static abstract class SerializableConverter<T> implements Converter<T>, Serializable {

    private static final long serialVersionUID = 1L;

    protected SerializableConverter() {
      super();
    }
    
  }

  private static final class ExecutableBasedConverter<T> extends SerializableConverter<T> {

    private static final long serialVersionUID = 1L;

    private transient Executable executable;

    private ExecutableBasedConverter(final Method method) {
      super();
      this.executable = Objects.requireNonNull(method);
      if (!Modifier.isStatic(method.getModifiers())) {
        throw new IllegalArgumentException("method is not static: " + method);
      }
    }

    private ExecutableBasedConverter(final Constructor<T> constructor) {
      super();
      this.executable = Objects.requireNonNull(constructor);
    }

    @Override
    public final T convert(final String rawValue) {
      final T returnValue;
      if (rawValue == null) {
        // Most valueOf(String) methods and constructors that the
        // specification intended this kludgy mechanism to handle do
        // not accept null as a value.
        returnValue = null;
      } else {
        T convertedObject = null;
        try {
          if (this.executable instanceof Method) {
            @SuppressWarnings("unchecked")
            final T invocationResult = (T)((Method)this.executable).invoke(null, rawValue);
            convertedObject = invocationResult;
          } else {
            assert this.executable instanceof Constructor;
            @SuppressWarnings("unchecked")
            final T invocationResult = ((Constructor<T>)this.executable).newInstance(rawValue);
            convertedObject = invocationResult;
          }
        } catch (final ReflectiveOperationException reflectiveOperationException) {
          throw new IllegalArgumentException(reflectiveOperationException.getMessage(), reflectiveOperationException);
        } finally {
          returnValue = convertedObject;
        }
      }
      return returnValue;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
      if (in != null) {
        in.defaultReadObject();
        final boolean constructor = in.readBoolean();        
        final Class<?> declaringClass = (Class<?>)in.readObject();
        assert declaringClass != null;
        final String methodName;
        if (constructor) {
          methodName = null;
        } else {
          methodName = in.readUTF();
          assert methodName != null;
        }
        final Class<?>[] parameterTypes = (Class<?>[])in.readObject();
        assert parameterTypes != null;
        try {
          if (constructor) {
            this.executable = declaringClass.getDeclaredConstructor(parameterTypes);
          } else {
            this.executable = declaringClass.getMethod(methodName, parameterTypes);
          }
        } catch (final ReflectiveOperationException reflectiveOperationException) {
          throw new IOException(reflectiveOperationException.getMessage(), reflectiveOperationException);
        }
        assert this.executable != null;
      }
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
      if (out != null) {
        out.defaultWriteObject();
        assert this.executable != null;
        final boolean constructor = this.executable instanceof Constructor;
        out.writeBoolean(constructor); // true means Constructor
        out.writeObject(this.executable.getDeclaringClass());
        if (!constructor) {
          out.writeUTF(this.executable.getName());
        }
        out.writeObject(this.executable.getParameterTypes());
      }
    }
    
  }
  
  private static final class PropertyEditorConverter<T> extends SerializableConverter<T> {

    private static final long serialVersionUID = 1L;

    private final Class<?> conversionClass;
    
    private transient PropertyEditor editor;
    
    private PropertyEditorConverter(final Class<?> conversionClass, final PropertyEditor editor) {
      super();
      this.conversionClass = Objects.requireNonNull(conversionClass);
      if (editor == null) {
        this.editor = PropertyEditorManager.findEditor(conversionClass);
      } else {
        this.editor = editor;
      }
    }

    @Override
    public final T convert(final String rawValue) {
      if (this.editor == null) {
        throw new IllegalArgumentException("No PropertyEditor available to convert " + rawValue);
      }
      final T returnValue;
      synchronized (this.editor) {
        editor.setAsText(rawValue);
        T result = null;
        try {
          @SuppressWarnings("unchecked")
          final T temp = (T)editor.getValue();
          result = temp;
        } catch (final ClassCastException classCastException) {
          throw new IllegalArgumentException(classCastException.getMessage(), classCastException);
        } finally {
          returnValue = result;
        }
      }
      return returnValue;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
      if (in != null) {
        in.defaultReadObject();
        this.editor = PropertyEditorManager.findEditor(conversionClass);
      }
    }

  }
  
}
