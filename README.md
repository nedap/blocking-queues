# blocking-queues

**blocking-queues** is a library that offers a simpler, safer subset of `java.util.concurrent.BlockingQueue` (in JVM Clojure) and `clojure.core.async` (in ClojureScript).

It addresses problems inherent to said libraries, reifying and enforcing best practices around concurrent programming.

The result is a small, unmistakable API, that unlike those wrapped libraries, cannot deadlock or stall.

Finally, it fosters "Functional Architecture" patterns, where decisions are decoupled from effects; although that is only optionally enforced.

Its most important features are:

* There are no indefinely-blocking operations
  * All operations are subject to a mandatory `timeout` param
* There are no untyped channels
  * All channels are created with a mandatory `spec` argument
  * spec checking in production is decoupled from `*assert*`: it has a dedicated, mandatory option controlling it.
* Error handling is mandatory
* Regular Java threads can block on multiple blocking queues
  * see `select!` operation
  * Achieved in a simple, reliable manner
* `clojure.core.async` becomes unnecessary (JVM) or less pervasive (cljs)
  * `core.async` has far too many gotchas, and it's a common experience for teams to use it incorrectly (at least for an initial period).
    * there's the unavoidable caveat that blocking ops cannot be reliably detected, so one cannot automatedly determine the correctness of typical core.async usage.
  * A typical JVM production app using `blocking-queues` will run  zero `core.async` code.
  * `core.async` is used as an implementation detail in the ClojureScript branch of this library
    * It also can be used in the JVM side, for linting purposes (more on this later)

## Installation

```clojure
[com.nedap.staffing-solutions/blocking-queues "unreleased"]
```
  
## Synopsis

**blocking-queues** offers exactly 3 verbs, and 4 nouns:

### The verbs

* `poll!`, modelled after `java.util.concurrent.BlockingQueue#poll`
* `offer!`, modelled after `java.util.concurrent.BlockingQueue#offer`
* `select!`, modelled after `clojure.core.async/alts!`
  *  works flawlessly over regular Java threads, effectively blocking over multiple options. The implementation is simple.

All verbs have a mandatory timeout param.

### The nouns

* `rendezvous`, modelled after unbuffered `clojure.core.async/chan`s
  * currently unavailable in cljs, because core.async doesn't support transducers in rendezvous channels.
* `blocking-queue`, modelled after buffered `clojure.core.async/chan`s
* `sliding-queue`, modelled after a `clojure.core.async/chan` backed by a `core.async/sliding-buffer`. 
* `dropping-queue`, modelled after a `clojure.core.async/chan` backed by a `core.async/dropping-buffer`. 

Generally, these 7 functions don't accept optional params; all configurable options are required upfront. That way this library can remain unopinionated, and consuming codebases remain explicit and easy to reason about.

Likewise, `rendezvous` and `blocking-queue` are separate functions (unlike core.async's `chan`) so the nature of your system is explicit.
Otherwise the textual difference between `(chan)` and `(chan 1)` is too tiny, and it can be indiscernible whether an existing codebase chose a specific kind of channel of a good reason, or simply by inertia.

The general philosophy of this library is to force consumers to do some amount of thinking; concurrency isn't easy and this library doesn't pretend so.

## Comparison with alternatives

|                         | Verbs' signature count | Nouns' signature count | Can deadlock or stall?                                                                                             |
|-------------------------|------------------------|------------------------|--------------------------------------------------------------------------------------------------------------------|
| **core.async**          | 30+                     | 10+                     | **Can deadlock** by blocking a member of its internal thread pool (IO, `<!!`, etc). **Can stall**, given the optionality of  timeouts (a `>!`/`<!` can wait forever, if the other side does not behave as expected)                                                |
| **j.u.c.BlockingQueue** | 11 (BlockingQueue interface methods)                     | 7 (official classes implementing this interface)                     | Two threads **can deadlock** (slightly differently from classic deadlocking; here the threads lock waiting for queues, instead of holding locks). **Can stall**, given the optionality of timeouts. |
| **blocking-queues**     | 3                  | 4                     | **No**, given all operations have a timeout, and that doing blocking IO isn't harmful under its model.                           |

#### Note on the `count` columns

Although cardinality and complexity are orthogonal, it's worth pointing out that large APIs often result in a combinatorial explosion of usage patterns, which makes resulting solutions harder to reason about.
Developers using large APIs often have to struggle with defining a sane subset of the API in question, and enforcing that that subset is used consistently in a team setting.

To make things worse, the consistency of these ad-hoc subsets is rarely amenable to automated checking (be it by static typing, runtime assertions, linting, etc).

In face of this, **blocking-queues** deliberately offers a small API, offering a sound baseline for developing correct solutions. Additionally, it can be subject to automated enforcement of even stronger patterns (Functional Architecture). 

## ns organisation

You only ever need to require `nedap.blocking-queues.api`, both in Clojure and ClojureScript.

All nouns are documented in the `api` namespace.

All verbs are defined/documented in the `nedap.blocking-queues.protocols.blocking-queue` ns; however you don't need to require this namespace or refer to it. The verbs are offered through the `api` namespace, for your convenience.

## Documentation

`clojure.repl/doc` works well with all `api` members (whether it's a noun or verb).

You are also encouraged to visit the `api` and `protocols.blocking-queue` source code; these namespaces are deliberately thin and contain the relevant specs + docstrings. 

`functional.nedap.blocking-queues.api` is a test namespace that can reasonably work as a set of runnable examples.

## Recommended material

* [Java Concurrency in Practice](https://jcip.net)
* [core.async in Use](https://www.youtube.com/watch?v=096pIlA3GDo)
* You can find references to the Functional Architecture throghout the [ploeh](https://blog.ploeh.dk) blog.

## Development

The default namespace is `dev`. Under it, `(refresh)` is available, which should give you a basic "Reloaded workflow".

> It is recommended that you use `(clojure.tools.namespace.repl/refresh :after 'formatting-stack.core/format!)`.

## License

Copyright © Nedap

This program and the accompanying materials are made available under the terms of the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0).
