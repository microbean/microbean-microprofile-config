/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2021 microBean™.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.WeakHashMap;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider; // for javadoc only

/**
 * An {@link AutoCloseable} implementation of the abstract {@link
 * org.eclipse.microprofile.config.spi.ConfigProviderResolver} class.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is safe for concurrent use by multiple threads.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see org.eclipse.microprofile.config.spi.ConfigProviderResolver
 */
public final class ConfigProviderResolver extends org.eclipse.microprofile.config.spi.ConfigProviderResolver implements AutoCloseable {

  // @GuardedBy("self")
  private final WeakHashMap<ClassLoader, WeakAutoCloseableReference<ClassLoader, Config>> configMap;

  private final ReferenceQueue<? super ClassLoader> autoClosingReferenceQueue;

  /**
   * Creates a new {@link ConfigProviderResolver}.
   */
  public ConfigProviderResolver() {
    super();
    this.configMap = new WeakHashMap<>();
    this.autoClosingReferenceQueue = new ReferenceQueue<>();
    final Thread cleaner = new AutoCloseableCloserThread(this.autoClosingReferenceQueue);
    cleaner.start();
  }

  /**
   * Closes this {@link ConfigProviderResolver} using a best-effort
   * strategy.
   *
   * <p>This method attempts to {@linkplain #releaseConfig(Config)
   * release} each of the {@link AutoCloseable} {@link Config}
   * instances that are {@linkplain #registerConfig(Config,
   * ClassLoader) registered} with it.  If any exception occurs, it is
   * aggregated with any others and thrown at the end of the whole
   * process.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is idempotent and safe for concurrent use by
   * multiple threads.</p>
   *
   * @exception RuntimeException if an error occurs
   *
   * @see #registerConfig(Config, ClassLoader)
   *
   * @see #releaseConfig(Config)
   */
  @Override
  public final void close() {
    synchronized (this.configMap) {
      if (!this.configMap.isEmpty()) {
        // Remember that configMap can technically be shrinking all
        // the time, even with the lock held.
        final Collection<? extends WeakAutoCloseableReference<?, ? extends Config>> configsSnapshot = new HashSet<>(this.configMap.values());
        if (!configsSnapshot.isEmpty()) {
          RuntimeException throwMe = null;
          for (final WeakAutoCloseableReference<?, ? extends Config> weakConfigReference : configsSnapshot) {
            assert weakConfigReference != null;
            final Config config = weakConfigReference.getPotentialAutoCloseable();
            if (config != null) {
              try {
                this.releaseConfig(config);
              } catch (final RuntimeException runtimeException) {
                if (throwMe == null) {
                  throwMe = runtimeException;
                } else {
                  throwMe.addSuppressed(runtimeException);
                }
              } catch (final Exception exception) {
                if (throwMe == null) {
                  throwMe = new RuntimeException(exception);
                } else {
                  throwMe.addSuppressed(exception);
                }
              }
            }
          }
          if (throwMe != null) {
            throw throwMe;
          }
        }
      }
    }
  }

  /**
   * Creates and returns a new {@link
   * org.eclipse.microprofile.config.spi.ConfigBuilder}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple threads.</p>
   *
   * <p>The {@link ConfigBuilder} implementation is safe for
   * concurrent use by multiple threads.</p>
   *
   * <h2>Implementation Notes</h2>
   *
   * <p>Because of the requirement of having an implementation of the
   * {@link ConfigBuilder#forClassLoader(ClassLoader)} method, the
   * {@link ConfigBuilder} implementation returned by this method may
   * contain a strong reference to a {@link ClassLoader} supplied to
   * it by that method.  The programmer needs to take care that this
   * does not result in classloader leaks.  In general, {@link
   * ConfigBuilder}s should be retained for only as long as is
   * necessary to {@linkplain ConfigBuilder#build() build} a {@link
   * Config} and no longer.</p>
   *
   * @return a new {@link
   * org.eclipse.microprofile.config.spi.ConfigBuilder}; never {@code
   * null}
   */
  @Override
  public final org.eclipse.microprofile.config.spi.ConfigBuilder getBuilder() {
    return new ConfigBuilder();
  }

  /**
   * Returns the sole {@link Config} instance appropriate for the
   * {@linkplain Thread#getContextClassLoader() context
   * <code>ClassLoader</code>}, creating and building a default one
   * just in time if necessary.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple threads.</p>
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a {@link Config} instance; never {@code null}
   *
   * @see #getConfig(ClassLoader)
   *
   * @see Thread#getContextClassLoader()
   *
   * @see
   * org.eclipse.microprofile.config.ConfigProvider#getConfig()
   */
  @Override
  public final Config getConfig() {
    return this.getConfig(AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader()));
  }

  /**
   * Returns the sole {@link Config} instance appropriate for the
   * supplied {@link ClassLoader}, creating and building a default one
   * just in time if necessary.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple threads.</p>
   *
   * <h2>Implementation Notes</h2>
   *
   * <p>The specification does not indicate what to do if the supplied
   * {@link ClassLoader} is {@code null}, but spread throughout other
   * areas of the specification {@linkplain ConfigProvider#getConfig()
   * there are implications} that a {@code null} {@link ClassLoader}
   * means that the current thread's {@linkplain
   * Thread#getContextClassLoader() context classloader} should be
   * used instead.  This implementation follows those implications.
   * See <a
   * href="https://github.com/eclipse/microprofile-config/issues/426"
   * target="_parent">issue 426</a> for more details.
   *
   * @param classLoader the {@link ClassLoader} used to identify the
   * {@link Config} to return; may be {@code null} in which case the
   * {@linkplain Thread#getContextClassLoader() context
   * <code>ClassLoader</code>} will be used instead
   *
   * @return a {@link Config} instance; never {@code null}
   *
   * @see #getBuilder()
   *
   * @see #getConfig()
   *
   * @see <a
   * href="https://github.com/eclipse/microprofile-config/issues/426"
   * target="_parent">MicroProfile Config issue 426</a>
   */
  @Override
  public final Config getConfig(ClassLoader classLoader) {
    if (classLoader == null) {
      // See https://github.com/eclipse/microprofile-config/issues/426
      classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
    }
    Config returnValue = null;
    synchronized (this.configMap) {
      final WeakAutoCloseableReference<?, ? extends Config> configReference = this.configMap.get(classLoader);
      if (configReference != null) {
        returnValue = configReference.getPotentialAutoCloseable();
      }
      if (returnValue == null) {
        returnValue = this.getBuilder()
          .addDefaultSources()
          .addDiscoveredSources()
          .addDiscoveredConverters()
          .forClassLoader(classLoader)
          .build();
        assert returnValue != null;
        // Deliberately called with the lock held on the configMap
        this.registerConfig(returnValue, classLoader);
      }
    }
    return returnValue;
  }

  /**
   * Registers the supplied {@link Config} instance under the supplied
   * {@link ClassLoader} in some way.
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple threads.</p>
   *
   * <h2>Implementation Notes</h2>
   *
   * <p>The specification says:</p>
   *
   * <blockquote>If the ClassLoader is null then the current
   * Application will be used.</blockquote>
   *
   * <p>This implementation assumes "current Application" is
   * equivalent to "current thread's {@linkplain
   * Thread#getContextClassLoader() context
   * <code>ClassLoader</code>}".  See <a
   * href="https://github.com/eclipse/microprofile-config/issues/426"
   * target="_parent">issue 426</a> for more details.</p>
   *
   * @param config the {@link Config} to register; may be {@code null}
   * in which case no action will be taken
   *
   * @param classLoader the {@link ClassLoader} to use as the key
   * under which the supplied {@link Config} should be registered; if
   * {@code null} then the return value of {@link
   * Thread#getContextClassLoader()} will be used instead
   *
   * @see
   * org.eclipse.microprofile.config.spi.ConfigProviderResolver#registerConfig(Config,
   * ClassLoader)
   *
   * @see <a
   * href="https://github.com/eclipse/microprofile-config/issues/426"
   * target="_parent">MicroProfile Config issue 426</a>
   */
  @Override
  public final void registerConfig(final Config config, ClassLoader classLoader) {
    if (config != null) {
      if (classLoader == null) {
        // See https://github.com/eclipse/microprofile-config/issues/426
        classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> Thread.currentThread().getContextClassLoader());
      }
      final WeakAutoCloseableReference<ClassLoader, Config> configReference = new WeakAutoCloseableReference<>(classLoader, config, this.autoClosingReferenceQueue);
      synchronized (this.configMap) {
        final WeakAutoCloseableReference<?, ? extends Config> oldConfigReference = this.configMap.putIfAbsent(classLoader, configReference);
        if (oldConfigReference != null) {
          final Config oldConfig = oldConfigReference.getPotentialAutoCloseable();
          if (oldConfig != null) {
            // The specification says that an IllegalStateException
            // should be thrown "if there is already a Config registered
            // within the Application."  It is not entirely clear what
            // "within the Application" means.  We do the best we can
            // here.
            throw new IllegalStateException("configMap.containsKey(" + classLoader + "): " + oldConfig);
          }
        }
      }
    }
  }

  /**
   * Releases the supplied {@link Config} by {@linkplain
   * AutoCloseable#close() closing} it if it implements {@link
   * AutoCloseable} and atomically removes all of its {@linkplain
   * #registerConfig(Config, ClassLoader) registrations}.
   *
   * <p>An {@link AutoCloseable} {@link Config} implementation
   * released by this method becomes effectively unusable by design
   * after this method completes.</p>
   *
   * <p>In general, owing to the underspecified nature of this method,
   * end users should probably not call it directly.</p>
   *
   * <h2>Thread Safety</h2>
   *
   * <p>This method is safe for concurrent use by multiple threads.</p>
   *
   * <h2>Implementation Notes</h2>
   *
   * <p>The specification says:</p>
   *
   * <blockquote>A Config normally gets released if the Application it
   * is associated with gets destroyed.</blockquote>
   *
   * <p>This implementation assumes the previous sentence means that
   * if a {@link Config} was previously {@linkplain
   * #registerConfig(Config, ClassLoader) registered} under a {@link
   * ClassLoader} <em>A</em>, and under a {@link ClassLoader}
   * <em>B</em>, then both registrations will be deleted.  The
   * equivalence between "Application" and {@link ClassLoader} is
   * derived from the documentation for the {@link
   * ConfigProvider#getConfig()} method, where there is an implication
   * that the {@linkplain Thread#getContextClassLoader() context
   * classloader} will be used as the key under which the {@link
   * Config} to be returned may be found.</p>
   *
   * <p>{@linkplain ConfigProvider#getConfig(ClassLoader) Elsewhere in
   * the specification}, it is stated:</p>
   *
   * <blockquote>There is exactly a single Config instance per
   * ClassLoader[.]</blockquote>
   *
   * <p>This is of course false since there may be zero {@link Config}
   * instances per {@link ClassLoader}.  Leaving that aside, this
   * sentence also does not say whether it is permitted for a single
   * {@link Config} instance to be shared between two {@link
   * ClassLoader}s.  This implementation permits such perhaps edge
   * cases, but a call to this {@link #releaseConfig(Config)} method
   * will remove both such registrations if present.</p>
   *
   * <p>The specification has no guidance on what to do if a {@link
   * Config} is released, and then another one is acquired via {@link
   * ConfigProvider#getConfig(ClassLoader)}, if anything.  This
   * implementation does not track {@link Config} instances once they
   * have been released.</p>
   *
   * <p>The specification does not indicate whether the supplied
   * {@link Config} must be non-{@code null}.  This implementation
   * consequently accepts {@code null} {@link Config}s and does
   * nothing in such cases.</p>
   *
   * <p>This method is called by the {@link #close()} method.
   *
   * @param config the {@link Config} to release; may be {@code null}
   * in which case no action will be taken
   *
   * @see #close()
   *
   * @see #registerConfig(Config, ClassLoader)
   */
  @Override
  public final void releaseConfig(final Config config) {
    // The specification says nothing about whether arguments can be null.
    if (config != null) {
      RuntimeException throwMe = null;
      synchronized (this.configMap) {
        if (!this.configMap.isEmpty()) {
          final Collection<? extends Entry<?, ? extends WeakAutoCloseableReference<?, ? extends Config>>> entrySet = this.configMap.entrySet();
          if (entrySet != null && !entrySet.isEmpty()) {
            final Iterator<? extends Entry<?, ? extends WeakAutoCloseableReference<?, ? extends Config>>> entryIterator = entrySet.iterator();
            if (entryIterator != null) {
              while (entryIterator.hasNext()) {
                Entry<?, ? extends WeakAutoCloseableReference<?, ? extends Config>> entry = null;
                try {
                  entry = entryIterator.next();
                } catch (final NoSuchElementException noSuchElementException) {
                  // This can happen legally because we're working
                  // with a WeakHashMap, so keys are effectively being
                  // removed by the garbage collector at any point.
                }
                if (entry != null) {
                  final WeakAutoCloseableReference<?, ? extends Config> configReference = entry.getValue();
                  if (configReference != null) {
                    final Config existingConfig = configReference.getPotentialAutoCloseable();
                    if (config.equals(existingConfig)) {
                      try {
                        entryIterator.remove();
                      } catch (final RuntimeException runtimeException) {
                        if (throwMe == null) {
                          throwMe = runtimeException;
                        } else {
                          throwMe.addSuppressed(runtimeException);
                        }
                      }
                      if (existingConfig instanceof AutoCloseable) {
                        try {
                          ((AutoCloseable)existingConfig).close();
                        } catch (final RuntimeException runtimeException) {
                          if (throwMe == null) {
                            throwMe = runtimeException;
                          } else {
                            throwMe.addSuppressed(runtimeException);
                          }
                        } catch (final InterruptedException interruptedException) {
                          Thread.currentThread().interrupt();
                        } catch (final Exception exception) {
                          if (throwMe == null) {
                            throwMe = new RuntimeException(exception.getMessage(), exception);
                          } else {
                            throwMe.addSuppressed(exception);
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
      }
      if (throwMe != null) {
        throw throwMe;
      }
    }
  }

  @SuppressWarnings("try")
  private static final class WeakAutoCloseableReference<R, A> extends WeakReference<R> implements AutoCloseable {

    private final A potentialAutoCloseable;

    private WeakAutoCloseableReference(final R referent,
                                       final A potentialAutoCloseable,
                                       final ReferenceQueue<? super R> referenceQueue) {
      super(referent, Objects.requireNonNull(referenceQueue));
      this.potentialAutoCloseable = potentialAutoCloseable;
    }

    private final A getPotentialAutoCloseable() {
      return this.potentialAutoCloseable;
    }

    @Override
    public final void close() throws Exception {
      final A potentialAutoCloseable = this.getPotentialAutoCloseable();
      if (potentialAutoCloseable instanceof AutoCloseable) {
        ((AutoCloseable)potentialAutoCloseable).close();
      }
    }

  }

  private static final class AutoCloseableCloserThread extends Thread {

    private final ReferenceQueue<?> referenceQueue;

    private AutoCloseableCloserThread(final ReferenceQueue<?> referenceQueue) {
      super(AutoCloseableCloserThread.class.getName());
      this.setDaemon(true);
      this.setPriority(Thread.MIN_PRIORITY + 1); // low but not too low
      this.referenceQueue = Objects.requireNonNull(referenceQueue);
    }

    @Override
    public final void run() {
      while (!this.isInterrupted()) {
        try {
          final Object reference = this.referenceQueue.remove();
          if (reference instanceof AutoCloseable) {
            try {
              ((AutoCloseable)reference).close();
            } catch (final InterruptedException interruptedException) {
              this.interrupt();
            } catch (final RuntimeException throwMe) {
              throw throwMe;
            } catch (final Exception exception) {
              throw new RuntimeException(exception.getMessage(), exception);
            }
          }
        } catch (final InterruptedException interruptedException) {
          this.interrupt();
        } catch (final RuntimeException throwMe) {
          throw throwMe;
        } catch (final Exception exception) {
          throw new RuntimeException(exception.getMessage(), exception);
        }
      }
    }

  }

}
