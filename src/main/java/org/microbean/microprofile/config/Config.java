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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;

import java.lang.reflect.Type;

import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator; // for javadoc only
import java.util.List;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * A {@link Serializable} implementation of the {@link
 * org.eclipse.microprofile.config.Config} interface that is also
 * a {@link Closeable} {@link TypeConverter}.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see org.eclipse.microprofile.config.Config
 */
public final class Config implements Closeable, org.eclipse.microprofile.config.Config, Serializable, TypeConverter {

  private static final long serialVersionUID = 1L;

  private final TypeConverter typeConverter;

  private final Collection<ConfigSource> sources;

  private volatile boolean closed;

  /**
   * Creates a new {@link Config} with a <a
   * href="https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/org/eclipse/microprofile/config/package-summary.html#package.description"
   * target="_parent">default set of {@link ConfigSource}s</a> and a
   * {@linkplain Converter default set of <code>Converter</code>s}
   * (including discovered {@link Converter}s).
   *
   * @exception IOException if an error occurs while reading a
   * {@code META-INF/microprofile-config.properties} resource
   *
   * @exception java.util.ServiceConfigurationError if there is a
   * problem interacting with a {@link ServiceLoader}
   */
  public Config() throws IOException {
    super();
    final List<ConfigSource> sources = new LinkedList<>();
    final Collection<? extends ConfigSource> defaultConfigSources = getDefaultConfigSources();
    final Collection<? extends ConfigSource> discoveredConfigSources = getDiscoveredConfigSources(null);
    sources.addAll(defaultConfigSources);
    sources.addAll(discoveredConfigSources);
    Collections.sort(sources, ConfigSourceComparator.INSTANCE);
    this.sources = Collections.unmodifiableCollection(sources);
    this.typeConverter = new ConversionHub();
  }

  /**
   * Creates a new {@link Config} instance.
   *
   * <p>The MicroProfile Config specification wants {@link
   * org.eclipse.microprofile.config.Config} implementations to be
   * acquired using {@link
   * org.eclipse.microprofile.config.ConfigProvider#getConfig()}
   * invocations.  This constructor exists primarily for
   * convenience.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p><strong>{@code sources} will be synchronized on and iterated
   * over by this constructor</strong>, which may have implications on
   * the type of {@link Collection} supplied.</p>
   *
   * @param sources a {@link Collection} of {@link ConfigSource}s; may
   * be {@code null}; <strong>will be synchronized on and iterated
   * over</strong>; copied by value; no reference is retained to this
   * object
   *
   * @param typeConverter a {@link TypeConverter} implementation used
   * for type conversion; must not be {@code null}; if it implements
   * {@link Closeable} <strong>it will be {@linkplain
   * Closeable#close() closed} by this {@link Config}'s {@link
   * #close()} method</strong>
   *
   * @exception NullPointerException of {@code typeConverter} is
   * {@code null}
   *
   * @see ConfigProviderResolver
   *
   * @see org.eclipse.microprofile.config.ConfigProvider#getConfig()
   */
  public Config(final Collection<? extends ConfigSource> sources,
                final TypeConverter typeConverter) {
    super();
    this.typeConverter = Objects.requireNonNull(typeConverter);
    if (sources == null) {
      this.sources = Collections.emptySet();
    } else {
      synchronized (sources) {
        if (sources.isEmpty()) {
          this.sources = Collections.emptySet();
        } else {
          this.sources = Collections.unmodifiableCollection(sources);
        }
      }
    }
  }

  /**
   * Closes this {@link Config} using a best-effort strategy.
   *
   * <p>An attempt is made to close each {@link ConfigSource} that is
   * itself an instance of {@link Closeable} and that was supplied to
   * this {@link Config} at construction time.  If any errors occur
   * along the way, they are added as {@linkplain
   * Throwable#addSuppressed(Throwable) suppressed exceptions} to an
   * {@link IOException} which is thrown as a kind of
   * aggregate.</p>
   *
   * <p>When this method finishes normally, all of this {@link
   * Config}'s associated {@link Closeable} {@link ConfigSource}s will
   * be {@linkplain Closeable#close() closed}.  <strong>In addition,
   * the {@link TypeConverter} supplied at construction time will be
   * closed as well if it implements {@link Closeable}.</strong></p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is idempotent and safe for concurrent use by
   * multiple threads.</p>
   *
   * @exception IOException if at least one {@link Closeable} {@link
   * ConfigSource} was not successfully closed or if the {@link
   * TypeConverter} supplied at construction time implements {@link
   * Closeable} and threw an {@link IOException}
   */
  @Override
  public final void close() throws IOException {
    final boolean oldClosed = this.isClosed();
    if (!oldClosed) {
      IOException throwMe = null;
      synchronized (this.sources) {
        for (final ConfigSource configSource : this.sources) {
          if (configSource instanceof Closeable) {
            try {
              ((Closeable)configSource).close();
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
      if (this.typeConverter instanceof Closeable) {
        try {
          ((Closeable)this.typeConverter).close();
        } catch (final IOException ioException) {
          if (throwMe == null) {
            throwMe = ioException;
          } else {
            throwMe.addSuppressed(ioException);
          }
        }
      }
      if (throwMe != null) {
        throw throwMe;
      }
      this.closed = true;
      ConfigProviderResolver.instance().releaseConfig(this); // XXX re-entrant call, potentially
    }
  }

  /**
   * Returns {@code true} if this {@link Config} has been {@linkplain
   * #close() closed}.
   *
   * <p>A closed {@link Config} will throw {@link
   * IllegalStateException} from most of its methods.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * @return {@code true} if this {@link Config} has been {@linkplain
   * #close() closed}; {@code false} otherwise
   *
   * @see #close()
   */
  public final boolean isClosed() {
    return this.closed;
  }

  /**
   * Returns an {@link Iterable} representing a snapshot at a moment
   * in time of this {@link Config}'s affiliated {@link ConfigSource}s
   * as they existed at that time.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>The underlying collection of {@link ConfigSource}s the
   * returned {@link Iterable} is capable of iterating over is an
   * immutable copy of the internal collection of {@link
   * ConfigSource}s managed by this {@link Config}.</p>
   *
   * <p>The {@link Iterable} returned by this method {@linkplain
   * Iterable#iterator() creates <code>Iterator</code>s} that do not
   * support the {@link Iterator#remove()} method.</p>
   *
   * <p>The MicroProfile Config specification implies a state of
   * affairs that permits {@link ConfigSource}s "inside" a {@link
   * Config} to come and go.  Consequently, this method returns what
   * is effectively a dissociated snapshot at a moment in time of a
   * collection of {@link ConfigSource}s that were once managed by
   * this {@link Config} at that moment in time.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * <p>The {@link Iterable} returned by this method is safe for use
   * by multiple threads.</p>
   *
   * @return an {@link Iterable} of {@link ConfigSource}s; never
   * {@code null}
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   */
  @Override
  public final Iterable<ConfigSource> getConfigSources() {
    if (this.isClosed()) {
      throw new IllegalStateException("this.isClosed()");
    }
    final Iterable<ConfigSource> returnValue;
    synchronized (this.sources) {
      if (this.sources.isEmpty()) {
        returnValue = Collections.emptySet();
      } else {
        returnValue = Collections.unmodifiableCollection(new ArrayList<>(this.sources));
      }
    }
    return returnValue;
  }

  /**
   * Returns an {@link Iterable} representing a snapshot at a moment
   * in time of the configuration property names as they existed at
   * that time.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>The underlying collection of property names the returned
   * {@link Iterable} is capable of iterating over is an immutable
   * copy of the internal collection of configuration property names
   * managed by this {@link Config}.</p>
   *
   * <p>The {@link Iterable} returned by this method {@linkplain
   * Iterable#iterator() creates <code>Iterator</code>s} that do not
   * support the {@link Iterator#remove()} method.</p>
   *
   * <p>The MicroProfile Config specification implies a state of
   * affairs that permits {@link ConfigSource}s "inside" a {@link
   * Config} to come and go.  Consequently, this method returns what
   * is effectively a dissociated snapshot at a moment in time of a
   * collection of the configuration property names that were once
   * managed by this {@link Config} at that moment in time.</p>
   *
   * <p>No caching is performed by this method.  Property names are
   * harvested from calls to {@link ConfigSource#getPropertyNames()}
   * on each {@link ConfigSource} present in the {@link Iterable}
   * returned by the {@link #getConfigSources()} method.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * <p>The {@link Iterable} returned by this method is safe for use
   * by multiple threads.</p>
   *
   * @return an {@link Iterable} representing a snapshot of a
   * collection of configuration property names which this {@link
   * Config} manages; never {@code null}
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   */
  @Override
  public final Iterable<String> getPropertyNames() {
    final Iterable<String> returnValue;
    final Iterable<ConfigSource> configSources = this.getConfigSources();
    if (configSources == null) {
      returnValue = Collections.emptySet();
    } else {
      final Set<String> names = new TreeSet<>();
      for (final ConfigSource configSource : configSources) {
        if (configSource != null) {
          // The specification says nothing about concurrency.
          synchronized (configSource) {
            final Collection<? extends String> sourceNames = configSource.getPropertyNames();
            if (sourceNames != null && !sourceNames.isEmpty()) {
              names.addAll(sourceNames);
            }
          }
        }
      }
      returnValue = Collections.unmodifiableSet(names);
    }
    assert returnValue != null;
    return returnValue;
  }

  /**
   * Returns an {@link Optional} representing an optional
   * configuration property value for the supplied {@code name}, as
   * converted to an object of the supplied {@code type}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method does not cache the value it returns.</p>
   *
   * <p>It is worth noting that the MicroProfile Config
   * specification does not say anything about whether {@link
   * Converter}s are allowed to attempt to "convert" {@code null}
   * values from {@link ConfigSource#getValue(String)} invocations
   * into non-{@code null} objects.  The <a
   * href="https://github.com/eclipse/microprofile-config/tree/master/tck"
   * target="_parent">MicroProfile Config TCK</a> will fail if {@link
   * Converter}s _do_ attempt to work on {@code null} values, so this
   * implementation never uses a {@code null} value for
   * conversion.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * @param <T> the type of the value being requested
   *
   * @param name the name of the configuration property whose
   * converted value should be returned; may be {@code null}; passed
   * unaltered to {@link ConfigSource#getValue(String)} so subject to
   * that method's (unspecified) preconditions
   *
   * @param type the {@link Class} representing the type any
   * non-{@code null} value should be converted to if possible; must
   * not be {@code null}
   *
   * @return an {@link Optional} representing an optional
   * configuration property value for the supplied {@code name}, as
   * converted to an object of the supplied {@code type}; never {@code
   * null}
   *
   * @exception IllegalArgumentException if the value could not be
   * converted to the requested type
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   *
   * @see #getOptionalValue(String, Type)
   *
   * @see #getValue(String, Class)
   *
   * @see #getValue(String, Type)
   *
   * @see ConfigSource#getValue(String)
   *
   * @see TypeConverter#convert(String, Type)
   *
   * @see Converter#convert(String)
   */
  @Override
  public final <T> Optional<T> getOptionalValue(final String name, final Class<T> type) {
    return this.getOptionalValue(name, (Type)type);
  }

  /**
   * Returns an {@link Optional} representing an optional
   * configuration property value for the supplied {@code name}, as
   * converted to an object of the supplied {@code type}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method does not cache the value it returns.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * @param <T> the type of the value being requested
   *
   * @param name the name of the configuration property whose
   * converted value should be returned; may be {@code null}; passed
   * unaltered to {@link ConfigSource#getValue(String)} so subject to
   * that method's (unspecified) preconditions
   *
   * @param type the {@link Type} representing the type any
   * non-{@code null} value should be converted to if possible; must
   * not be {@code null}
   *
   * @return an {@link Optional} representing an optional
   * configuration property value for the supplied {@code name}, as
   * converted to an object of the supplied {@code type}; never {@code
   * null}
   *
   * @exception IllegalArgumentException if the value could not be
   * converted to the requested type
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   *
   * @see #getOptionalValue(String, Class)
   *
   * @see #getValue(String, Class)
   *
   * @see #getValue(String, Type)
   *
   * @see ConfigSource#getValue(String)
   *
   * @see TypeConverter#convert(String, Type)
   *
   * @see Converter#convert(String)
   */
  public final <T> Optional<T> getOptionalValue(final String name, final Type type) {
    Objects.requireNonNull(type);
    Optional<T> returnValue = null;
    final Iterable<ConfigSource> configSources = this.getConfigSources();
    if (configSources != null) {
      for (final ConfigSource configSource : configSources) {
        if (configSource != null) {
          final String value = configSource.getValue(name);
          if (value != null) {
            returnValue = Optional.of(this.convert(value, type));
            assert returnValue != null;
            if (returnValue.isPresent()) {
              break;
            }
          }
        }
      }
    }
    if (returnValue == null) {
      returnValue = Optional.empty();
    }
    return returnValue;
  }

  /**
   * Returns the value for the configuration property named by the
   * supplied {@code name}, as converted to an object of the supplied
   * {@code type}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method does not cache the value it returns.</p>
   *
   * <p>The MicroProfile Config specification does not say whether
   * implementations of the {@link
   * org.eclipse.microprofile.config.Config#getValue(String, Class)}
   * method may or may not return {@code null}.  {@code null} values
   * from {@link ConfigSource#getValue(String)} invocations are taken
   * to indicate a given configuration property value's absence,
   * however.  Coupled with the fact that all {@link
   * org.eclipse.microprofile.config.Config#getValue(String, Class)}
   * are required to throw {@link NoSuchElementException}s when "the
   * property isn't present in the configuration", this implementation
   * chooses never to return {@code null}.</p>
   *
   * <p>It is also worth noting that the MicroProfile Config
   * specification does not say anything about whether {@link
   * Converter}s are allowed to attempt to "convert" {@code null}
   * values from {@link ConfigSource#getValue(String)} invocations
   * into non-{@code null} objects.  The <a
   * href="https://github.com/eclipse/microprofile-config/tree/master/tck"
   * target="_parent">MicroProfile Config TCK</a> will fail if {@link
   * Converter}s _do_ attempt to work on {@code null} values, so this
   * implementation never uses a {@code null} value for
   * conversion.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * @param <T> the type of the values returned by this method
   *
   * @param name the name of the configuration property whose value
   * should be returned; may be {@code null}; passed unaltered to
   * {@link ConfigSource#getValue(String)} so subject to that method's
   * (unspecified) preconditions
   *
   * @param type the {@link Class} representing the type any
   * non-{@code null} value should be converted to if possible; must
   * not be {@code null}
   *
   * @return the converted value; never {@code null}
   *
   * @exception NoSuchElementException if the requested configuration
   * property value does not exist
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   *
   * @see ConfigSource#getValue(String)
   *
   * @see #getValue(String, Type)
   *
   * @see #getOptionalValue(String, Class)
   *
   * @see #getOptionalValue(String, Type)
   */
  @Override
  public final <T> T getValue(final String name, final Class<T> type) {
    return this.getValue(name, (Type)type);
  }

  /**
   * Returns the value for the configuration property named by the
   * supplied {@code name}, as converted to an object of the supplied
   * {@code type}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method does not cache the value it returns.</p>
   *
   * <p>It is also worth noting that the MicroProfile Config
   * specification does not say anything about whether {@link
   * Converter}s are allowed to attempt to "convert" {@code null}
   * values from {@link ConfigSource#getValue(String)} invocations
   * into non-{@code null} objects.  The <a
   * href="https://github.com/eclipse/microprofile-config/tree/master/tck"
   * target="_parent">MicroProfile Config TCK</a> will fail if {@link
   * Converter}s _do_ attempt to work on {@code null} values, so this
   * implementation never uses a {@code null} value for
   * conversion.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * @param <T> the type of the values returned by this method
   *
   * @param name the name of the configuration property whose value
   * should be returned; may be {@code null}; passed unaltered to
   * {@link ConfigSource#getValue(String)} so subject to that method's
   * (unspecified) preconditions
   *
   * @param type the {@link Type} representing the type any
   * non-{@code null} value should be converted to if possible; must
   * not be {@code null}
   *
   * @return the converted value; never {@code null}
   *
   * @exception NoSuchElementException if the requested configuration
   * property value does not exist
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   *
   * @see ConfigSource#getValue(String)
   *
   * @see #getValue(String, Class)
   *
   * @see #getOptionalValue(String, Class)
   *
   * @see #getOptionalValue(String, Type)
   */
  public final <T> T getValue(final String name, final Type type) {
    Objects.requireNonNull(type);
    T returnValue = null;
    final Iterable<ConfigSource> configSources = this.getConfigSources();
    if (configSources != null) {
      for (final ConfigSource configSource : configSources) {
        if (configSource != null) {
          final String value = configSource.getValue(name);
          if (value != null) {
            returnValue = this.convert(value, type);
            if (returnValue != null) {
              break;
            }
          }
        }
      }
    }
    if (returnValue == null) {
      throw new NoSuchElementException(name);
    }
    return returnValue;
  }

  /**
   * Implements the {@link TypeConverter#convert(String, Type)} method
   * by invoking the same method on this {@link Config}'s affiliated
   * {@link TypeConverter} supplied at construction time and returning
   * the result.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for use by multiple threads.</p>
   *
   * @param rawValue the value to convert; may be {@code null}
   *
   * @param type the {@link Type} to which the current invocation of
   * this method's return value should be assignable; must not be
   * {@code null}
   *
   * @return an object assignable to the supplied {@code type}, or
   * {@code null}
   *
   * @see TypeConverter#convert(String, Type)
   *
   * @exception NullPointerException if {@code type} is {@code null}
   *
   * @exception IllegalArgumentException if conversion could not occur
   * for any reason
   *
   * @exception IllegalStateException if this {@link Config} has been
   * {@linkplain #close() closed}
   */
  @Override
  public final <T> T convert(final String rawValue, final Type type) {
    if (this.isClosed()) {
      throw new IllegalStateException("this.isClosed()");
    }
    return this.typeConverter.convert(rawValue, type);
  }

  static final Collection<? extends ConfigSource> getDefaultConfigSources() throws IOException {
    final Collection<ConfigSource> sources = new LinkedList<>();
    sources.add(new SystemPropertiesConfigSource());
    sources.add(new EnvironmentVariablesConfigSource());
    final Collection<? extends ConfigSource> microprofileConfigPropertiesConfigSources = getMicroprofileConfigPropertiesSources(null);
    if (microprofileConfigPropertiesConfigSources != null && !microprofileConfigPropertiesConfigSources.isEmpty()) {
      sources.addAll(microprofileConfigPropertiesConfigSources);
    }
    return Collections.unmodifiableCollection(sources);
  }

  static final Collection<? extends ConfigSource> getMicroprofileConfigPropertiesSources(ClassLoader classLoader) throws IOException {
    if (classLoader == null) {
      classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
    }
    final Enumeration<? extends URL> urls = classLoader.getResources("/META-INF/microprofile-config.properties");
    Collection<ConfigSource> returnValue = new ArrayList<>();
    if (urls != null) {
      while (urls.hasMoreElements()) {
        final URL url = urls.nextElement();
        if (url != null) {
          final Properties properties = new Properties();
          // The specification does not mandate a character set for
          // the /META-INF/microprofile-config.properties, nor whether
          // it should be in java.util.Properties format.  We'll
          // assume ISO-8859-1 and java.util.Properties format.
          try (final Reader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.ISO_8859_1))) {
            properties.load(reader);
          }
          // The specification provides no guidance on ConfigSource naming.
          returnValue.add(new PropertiesConfigSource(properties, url.toString()));
        }
      }
    }
    if (returnValue.isEmpty()) {
      returnValue = Collections.emptySet();
    } else {
      returnValue = Collections.unmodifiableCollection(returnValue);
    }
    return returnValue;
  }

  static final Collection<? extends ConfigSource> getDiscoveredConfigSources(ClassLoader classLoader) {
    if (classLoader == null) {
      classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
    }
    final ServiceLoader<ConfigSource> discoveredSources = ServiceLoader.load(ConfigSource.class, classLoader);
    assert discoveredSources != null;
    final Collection<ConfigSource> sources = new LinkedList<>();
    for (final ConfigSource source : discoveredSources) {
      if (source != null) {
        sources.add(source);
      }
    }
    final ServiceLoader<ConfigSourceProvider> discoveredConfigSourceProviders = ServiceLoader.load(ConfigSourceProvider.class, classLoader);
    assert discoveredConfigSourceProviders != null;
    for (final ConfigSourceProvider provider : discoveredConfigSourceProviders) {
      if (provider != null) {
        final Iterable<? extends ConfigSource> configSources = provider.getConfigSources(classLoader);
        if (configSources != null) {
          for (final ConfigSource configSource : configSources) {
            if (configSource != null) {
              sources.add(configSource);
            }
          }
        }
      }
    }
    return Collections.unmodifiableCollection(sources);
  }
}
