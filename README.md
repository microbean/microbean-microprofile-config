# microBean&trade; MicroProfile Config

[![Build Status](https://travis-ci.com/microbean/microbean-microprofile-config.svg?branch=master)](https://travis-ci.com/microbean/microbean-microprofile-config)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.microbean/microbean-microprofile-config/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.microbean/microbean-microprofile-config)

An implementation of version 1.3 of the [MicroProfile Config
specification](https://github.com/eclipse/microprofile-config/tree/master/spec/src/main/asciidoc).

# Features

* Fully MicroProfile Config 1.3 compliant (honors the specification
  and passes the
  [TCK](https://github.com/eclipse/microprofile-config/tree/master/tck))
* Thread safe
* [Conversion can work on `Type`s, not just
  `Class`es](https://github.com/microbean/microbean-microprofile-config/blob/f50a3331a8f396d3797cf9d08467c8d14d980887/src/main/java/org/microbean/microprofile/config/Converters.java#L81-L162)
* [`PropertyEditor`](https://docs.oracle.com/javase/8/docs/api/java/beans/PropertyEditor.html)s
  are used as lowest priority
  [`Converter`](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/org/eclipse/microprofile/config/spi/Converter.html)s
  when available
* `Config` instances [may be built without
  registration](https://microbean.github.io/microbean-microprofile-config/apidocs/org/microbean/microprofile/config/Config.html#Config--)
* CDI support [works with CDI alternatives and permits alternative
  means of production and multiple injected `Config`s based on
  qualifier
  sets](https://github.com/microbean/microbean-microprofile-config/blob/master/src/main/java/org/microbean/microprofile/config/cdi/ConfigExtension.java)
* Observer method injected `ConfigProperty`-annotated parameters are
  validated, [not just field injection
  points](https://github.com/eclipse/microprofile-config/pull/423)
* Potential [resource leaks easily permitted by the
  specification](https://github.com/eclipse/microprofile-config/blob/1.3/spec/src/main/asciidoc/configprovider.asciidoc#accessing-or-creating-a-certain-configuration)
  are handled properly, as much as possible
* No unnecessary dependencies
* [Fully documented](https://microbean.github.io/microbean-microprofile-config/)
