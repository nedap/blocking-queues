(ns nedap.blocking-queues.protocols.blocking-queue
  "Defines and documents this library's 'verbs'.

  Please don't require or refer to this namespace in consuming applications: use the `api` ns instead.

  That is necessary for a correct cross-compiler setting (JVM, JS)."
  (:require
   [clojure.spec.alpha :as spec]
   [nedap.speced.def :as speced]))

(spec/def ::ms pos-int?)

(spec/def :nedap.blocking-queues.protocols.blocking-queue.select!.opts/ms ::ms)

(spec/def :nedap.blocking-queues.protocols.blocking-queue.select!.opts/resolution ::ms)

(spec/def :nedap.blocking-queues.protocols.blocking-queue.select!.opts/priority boolean?)

(spec/def ::select!.opts (spec/keys :req-un [:nedap.blocking-queues.protocols.blocking-queue.select!.opts/ms]
                                    :opt-un [:nedap.blocking-queues.protocols.blocking-queue.select!.opts/resolution
                                             :nedap.blocking-queues.protocols.blocking-queue.select!.opts/priority]))

(spec/def ::select.offer-to (spec/tuple :nedap.blocking-queues.api/acceptable-blocking-queue some?))

(spec/def ::select.op (spec/or :poll-from :nedap.blocking-queues.api/acceptable-blocking-queue
                               :offer-to  ::select.offer-to))

(spec/def ::ops (spec/coll-of ::select.op :min-count 1))

(def default-resolution-ms
  "(Applies to JVM Clojure only)

  `#'select!` works by dividing its `timeout-ms` argument into N chunks of time, which value is the 'resolution'.
  That way, it can fairly choose among blocking choices, instead of blocking on the first choice because of a large timeout.

  `#'default-resolution-ms` is the default resolution for `#'select!`, which you can override with is `:resolution` option."
  5)

(speced/defprotocol BlockingQueue
  "A BlockingQueue is any one of the queues that are created via `nedap.blocking-queues.api`.

This protocol is not meant to be extended; and internal specs intentionally prevent that.

There's an intentionally small set of methods: just two, with a single signature each.
This enables guaranteeing that all usages of queues go through a timeout."

  (^boolean? offer! [^:nedap.blocking-queues.api/acceptable-blocking-queue queue
                     ^some? x
                     ^{::speced/spec #{:ms}} _
                     ^::ms timeout]
    "Offers `x` to `queue` up to `timeout` milliseconds. Returns whether the value was succesfully passed.")

  (poll! [^:nedap.blocking-queues.api/acceptable-blocking-queue queue
          ^{::speced/spec #{:ms}} _
          ^::ms timeout]
    "Tries to accept a value from `queue`, up to `timeout` milliseconds. Returns `nil` iff nothing could be accepted.")

  (^{::speced/spec (spec/nilable (spec/tuple any? :nedap.blocking-queues.api/acceptable-blocking-queue))}
    select!
    [^::ops ops
     ^::select!.opts opts]
    "Similar to `clojure.core.async/alts!`. See specs.

On JVM clojure, blocking happens fairly over the passed `ops` (instead of blocking on the first option),
via the `#'default-resolution-ms` mechanism (see its doc).

Returns nil on timeout."))
