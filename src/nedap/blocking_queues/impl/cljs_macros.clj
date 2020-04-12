(ns nedap.blocking-queues.impl.cljs-macros
  (:require
   [clojure.core.async :as async]
   [nedap.blocking-queues.protocols.blocking-queue :as protocols.blocking-queue]
   [nedap.utils.spec.api :refer [check!]]))

(defmacro offer! [q x _ t]
  `(let [q# ~q
         x# ~x
         t# ~t
         _# (assert (check! :nedap.blocking-queues.api/acceptable-blocking-queue q#
                            some?                                                x#
                            #{:ms}                                               ~_
                            ::protocols.blocking-queue/ms                        t#))
         timeout# (async/timeout t#)
         r# (async/alts! [[q# x#] timeout#])]
     (-> (when-not (->> r# second #{timeout#})
           r#)
         (boolean))))

(defmacro poll! [q _ t]
  `(let [q# ~q
         t# ~t
         _# (assert (check! :nedap.blocking-queues.api/acceptable-blocking-queue q#
                            #{:ms}                                               ~_
                            ::protocols.blocking-queue/ms                        t#))
         timeout# (async/timeout t#)
         r# (async/alts! [q# timeout#])]
     (when-not (->> r# second #{timeout#})
       (first r#))))

(defmacro select!
  "Returns nil on timeout"
  [ops
   {timeout-ms :ms
    :keys      [priority]}]
  `(let [ops# ~ops
         timeout-ms# ~timeout-ms
         priority# ~priority
         _# (assert (check! ::protocols.blocking-queue/ops     ops#
                            ::protocols.blocking-queue/ms      timeout-ms#
                            (cljs.spec.alpha/nilable boolean?) priority#))
         t# (async/timeout timeout-ms#)
         r# (async/alts! (conj ops# t#)
                         :priority priority#)]
     (when-not (->> r# second #{t#})
       r#)))
