(ns nedap.blocking-queues.api
  "This namespace offers the complete API of the `blocking-queues` library.

  Some parts are documented in the `nedap.blocking-queues.protocols.blocking-queue` ns."
  (:require
   [clojure.core.async :as async]
   #?(:clj [clojure.spec.alpha :as spec] :cljs [cljs.spec.alpha :as spec])
   #?(:clj [nedap.blocking-queues.impl :as impl :refer [max-queue-size]])
   [nedap.blocking-queues.impl.utils :refer [filter-of]]
   [nedap.blocking-queues.protocols.blocking-queue :as protocols.blocking-queue]
   [nedap.speced.def :as speced])
  #?(:clj (:import (java.util.concurrent ArrayBlockingQueue LinkedBlockingDeque SynchronousQueue)) :cljs (:require-macros [nedap.blocking-queues.impl :refer [max-queue-size]] [nedap.blocking-queues.api :refer [offer! poll! select!]])))

(spec/def ::queue-size (spec/int-in 1 (max-queue-size)))

(speced/def-with-doc ::fair
  "See the `fair` boolean param throughout the java.util.concurrent javadocs.

Not used in ClojureScript."
  boolean?)

(speced/def-with-doc ::type
  "The type of messages that are allowed to be stored into a given queue, expresed as a Spec in keyword form.

Attempting to store a message that doesn't satisfy the spec will throw an error, if:

  * assertions are enabled; or
  * A `::check-specs?` argument with a value of `true` was passed when building a given blocking queue."
  qualified-keyword?)

(speced/def-with-doc ::check-specs?
  "Whether to check that messages conform to `::type`, even in production systems where the Clojure assertion system
is presumably disabled."
  boolean?)

#?(:clj (speced/defn rendezvous
          "Creates a rendezvous. Note that meeting over a rendezvous does not imply the possibility of blocking indefinitely;
all `poll!`s and `offer!`s that go through them are still subject to timeouts.

Not offered in ClojureScript at the moment, because core.async doesn't support transducers in rendezvous channels,
which means that `spec` checking cannot be implemented for this case."
          [{:keys [^::type spec, ^::fair fair?, ^ifn? error-handler, ^::check-specs? check-specs?]}]
          (-> (SynchronousQueue. fair?)
              (impl/->RendezVous spec error-handler check-specs?))))

(speced/defn blocking-queue
  "Creates a queue such that:
 `poll!` waits for the queue to become non-empty when retrieving an element; and
 `offer!` waits for space to become available in the queue when storing an element.

Such waits are never unbounded; they are subject to mandatory timeouts."
  [{:keys [^::type spec, ^::queue-size queue-size, ^::fair fair?, ^ifn? error-handler, ^::check-specs? check-specs?]}]
  #?(:clj  (-> queue-size
               ^int (int)
               (ArrayBlockingQueue. fair?)
               (impl/->SimpleBlockingQueue spec error-handler check-specs?))
     :cljs (async/chan (async/buffer queue-size)
                       (filter-of spec check-specs?)
                       error-handler)))

(speced/defn sliding-queue
  "A `blocking-queue` variant in which writes always succeed, by discarding the oldest value in the queue.

Such sliding only happens as a last resource; `offer!` will prefer a non-lossy transfer,
as long as the timeout was not surpassed."
  [{:keys [^::type spec, ^::queue-size queue-size, ^ifn? error-handler, ^::check-specs? check-specs?]}]
  #?(:clj  (-> queue-size
               ^int (int)
               (LinkedBlockingDeque.)
               (impl/->LinkedBlockingDequeAdapter)
               (impl/->SlidingQueue spec
                                    (Object.)
                                    error-handler
                                    check-specs?))
     :cljs (async/chan (async/sliding-buffer queue-size)
                       (filter-of spec check-specs?)
                       error-handler)))

(speced/defn dropping-queue
  "A `blocking-queue` variant in which writes always succeed, by discarding the `offer!`ed value.

Such dropping only happens as a last resource; `offer!` will prefer a non-lossy transfer,
as long as the timeout was not surpassed."
  [{:keys [^::type spec, ^::queue-size queue-size, ^ifn? error-handler, ^::check-specs? check-specs?]}]
  #?(:clj  (-> queue-size
               ^int (int)
               (LinkedBlockingDeque.)
               (impl/->LinkedBlockingDequeAdapter)
               (impl/->DroppingQueue spec
                                     (Object.)
                                     error-handler
                                     check-specs?))
     :cljs (async/chan (async/dropping-buffer queue-size)
                       (filter-of spec check-specs?)
                       error-handler)))

(speced/def-with-doc ::acceptable-blocking-queue
  "Only queues created via this API ns are acceptable."
  #?(:clj  :nedap.blocking-queues.impl/acceptable-blocking-queue
     :cljs any?))

#?(:clj (impl/emit-offer!))

#?(:clj (impl/emit-poll!))

#?(:clj (impl/emit-select!))
