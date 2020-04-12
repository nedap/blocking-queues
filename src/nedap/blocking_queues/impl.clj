(ns nedap.blocking-queues.impl
  (:require
   [clojure.spec.alpha :as spec]
   [nedap.blocking-queues.impl.cljs-macros]
   [nedap.blocking-queues.protocols.blocking-queue :as protocols.blocking-queue :refer [BlockingQueue offer! poll!]]
   [nedap.speced.def :as speced]
   [nedap.utils.spec.api :refer [check!]])
  (:import
   (java.util.concurrent ArrayBlockingQueue BlockingDeque LinkedBlockingDeque SynchronousQueue TimeUnit)))

(defmacro max-queue-size []
  Integer/MAX_VALUE)

(spec/def ::type qualified-keyword?)

(spec/def ::check-specs? boolean?)

(defmacro catching-via
  {:style/indent 1}
  [ex-handler & body]
  (assert (some? ex-handler))
  (assert (sequential? body))
  (assert (-> body count pos?))
  `(let [handler# ~ex-handler]
     (assert (check! ifn? handler#))
     (try
       ~@body
       (catch Throwable e#
         (handler# e#)))))

;; ^:unsynchronized-mutable is there for privacy. No references are intended to be mutated
(deftype RendezVous [^:unsynchronized-mutable ^SynchronousQueue impl, spec, ex-handler, check-specs?]
  BlockingQueue
  (--offer! [^SynchronousQueue q x _ t]
    (assert (check! #{SynchronousQueue} (class impl)
                    ::type              spec
                    spec                x
                    ifn?                ex-handler
                    ::check-specs?      check-specs?))
    (catching-via ex-handler
      (when check-specs?
        (check! spec x))
      (-> impl (.offer x t TimeUnit/MILLISECONDS))))

  (--poll! [^SynchronousQueue q _ t]
    (assert (->> impl class #{SynchronousQueue}))
    (catching-via ex-handler
      (-> impl (.poll t TimeUnit/MILLISECONDS)))))

;; ^:unsynchronized-mutable is there for privacy. No references are intended to be mutated
(deftype SimpleBlockingQueue [^:unsynchronized-mutable ^ArrayBlockingQueue impl, spec, ex-handler, check-specs?]
  BlockingQueue
  (--offer! [^ArrayBlockingQueue q x _ t]
    (assert (check! #{ArrayBlockingQueue} (class impl)
                    ::type                spec
                    spec                  x
                    ifn?                  ex-handler
                    ::check-specs?        check-specs?))
    (catching-via ex-handler
      (when check-specs?
        (check! spec x))
      (-> impl (.offer x t TimeUnit/MILLISECONDS))))

  (--poll! [^ArrayBlockingQueue q _ t]
    (assert (->> impl class #{ArrayBlockingQueue}))
    (catching-via ex-handler
      (-> impl (.poll t TimeUnit/MILLISECONDS)))))

;; ^:unsynchronized-mutable is there for privacy. No references are intended to be mutated
(deftype LinkedBlockingDequeAdapter [^:unsynchronized-mutable ^LinkedBlockingDeque impl]
  BlockingQueue
  (--offer! [q x _ t]
    (assert (->> impl class #{LinkedBlockingDeque}))
    (-> impl (.offer x t TimeUnit/MILLISECONDS)))

  (--poll! [q _ t]
    (assert (->> impl class #{LinkedBlockingDeque}))
    (-> impl (.poll t TimeUnit/MILLISECONDS)))

  BlockingDeque
  (remainingCapacity [this]
    (assert (->> impl class #{LinkedBlockingDeque}))
    (-> impl (.remainingCapacity)))

  (removeFirst [this]
    (assert (->> impl class #{LinkedBlockingDeque}))
    (-> impl (.removeFirst)))

  (add [this x]
    (assert (->> impl class #{LinkedBlockingDeque}))
    (-> impl (.add x))))

;; ^:unsynchronized-mutable is there for privacy. No references are intended to be mutated
(deftype SlidingQueue [^:unsynchronized-mutable ^LinkedBlockingDequeAdapter backing-queue
                       spec
                       ^:unsynchronized-mutable lock
                       ex-handler
                       check-specs?]
  BlockingQueue

  (--offer! [this x _ timeout-long-value]
    (assert (check! (partial instance? LinkedBlockingDequeAdapter) backing-queue
                    some?                                          lock
                    ::type                                         spec
                    spec                                           x
                    ifn?                                           ex-handler
                    ::check-specs?                                 check-specs?))

    ;; Rationale for `locking`:
    ;; `or` is check-then-act
    ;; `do` is a composite op
    (locking lock
      (or (catching-via ex-handler
            (when check-specs?
              (check! spec x))
            (offer! backing-queue x :ms timeout-long-value))
          (do
            (when-not (-> backing-queue .remainingCapacity pos?)
              (-> backing-queue .removeFirst))
            (-> backing-queue (.add x))))))

  (--poll! [this _ timeout-long-value]
    (poll! backing-queue :ms timeout-long-value)))

;; ^:unsynchronized-mutable is there for privacy. No references are intended to be mutated
(deftype DroppingQueue [^:unsynchronized-mutable ^LinkedBlockingDequeAdapter backing-queue
                        spec
                        ^:unsynchronized-mutable lock
                        ex-handler
                        check-specs?]
  BlockingQueue

  (--offer! [this x _ timeout-long-value]
    (assert (check! (partial instance? LinkedBlockingDequeAdapter) backing-queue
                    #{:ms}                                         _
                    some?                                          lock
                    ::type                                         spec
                    spec                                           x
                    ifn?                                           ex-handler
                    ::check-specs?                                 check-specs?))

    ;; Rationale for `locking`:
    ;; `or` is check-then-act
    ;; `if-not` also is check-then-act
    (locking lock
      (or (catching-via ex-handler
            (when check-specs?
              (check! spec x))
            (offer! backing-queue x :ms timeout-long-value))
          (if-not (-> backing-queue .remainingCapacity pos?)
            false
            (-> backing-queue (.add x))))))

  (--poll! [this _ timeout-long-value]
    (assert (#{:ms} _))
    (poll! backing-queue :ms timeout-long-value)))

(spec/def ::acceptable-blocking-queue (fn [x]
                                        (->> x
                                             class
                                             #{nedap.blocking_queues.impl.DroppingQueue
                                               nedap.blocking_queues.impl.LinkedBlockingDequeAdapter
                                               nedap.blocking_queues.impl.RendezVous
                                               nedap.blocking_queues.impl.SimpleBlockingQueue
                                               nedap.blocking_queues.impl.SlidingQueue})))

(speced/defn ^boolean? blocking-queue? [x]
  (and (not (vector? x))
       (satisfies? BlockingQueue x)))

(speced/defn ^vector? permutate [[x :as ^vector? coll]]
  (if (#{0 1} (count coll))
    coll
    (-> coll
        (subvec 1)
        (conj x))))

(speced/defn select! [^{::speced/spec (spec/coll-of ::protocols.blocking-queue/select.op :min-count 1)} ops
                      {^::protocols.blocking-queue/ms
                       timeout-ms :ms
                       :keys      [^::protocols.blocking-queue/ms resolution
                                   ^::speced/nilable ^boolean? priority]
                       :or        {resolution protocols.blocking-queue/default-resolution-ms}}]
  {:pre [(>= timeout-ms resolution)]}
  (speced/let [op-fns (cond->> ops
                        true           (mapv (fn [x]
                                               (let [b-q? (cond
                                                            (blocking-queue? x) true
                                                            (vector? x)         false
                                                            true                (throw (ex-info "This doesn't look like an op"
                                                                                                {:op x})))
                                                     f (if b-q?
                                                         (fn []
                                                           (-> x (poll! :ms resolution)))
                                                         (speced/let [[^blocking-queue? q, x
                                                                       :as ^::protocols.blocking-queue/select.offer-to _] x]
                                                           (fn []
                                                             (-> q (offer! x :ms resolution)))))
                                                     v (if b-q?
                                                         [nil x]
                                                         (speced/let [[^blocking-queue? q, x
                                                                       :as ^::protocols.blocking-queue/select.offer-to _] x]
                                                           ;; reverse the order, to mimic core.async's `alts!` api
                                                           ;; also return `true` instead of `x`, to mimic core.async's `alts!` api
                                                           [true q]))]
                                                 [f v])))
                        (not priority) (shuffle))]
    (loop [remaining-ms timeout-ms
           op-fns op-fns]
      (when (pos? remaining-ms)
        (speced/let [[f [initial-value, ^blocking-queue? chosen-queue :as drafted-return-value]] (first op-fns)
                     op-result (f)
                     return-value (when op-result
                                    (cond-> drafted-return-value
                                      (not initial-value) (assoc 0 op-result)))]
          (or return-value
              (recur (- remaining-ms resolution)
                     (permutate op-fns))))))))

(extend-protocol protocols.blocking-queue/BlockingQueue
  clojure.lang.PersistentVector
  (--select! [x y]
    (select! x y)))

(defmacro emit-offer! []
  `(defmacro ~(with-meta 'offer! {:tag      (list 'quote (-> #'protocols.blocking-queue/offer! meta :tag))
                                  :arglists (list 'quote (-> #'protocols.blocking-queue/offer! meta :arglists))
                                  :doc      (-> #'protocols.blocking-queue/offer! meta :doc)})
     [~'q ~'x ~'_ ~'t]
     (if (-> ~'&env :ns nil?)
       (list 'nedap.blocking-queues.protocols.blocking-queue/offer! ~'q ~'x ~'_ ~'t)
       (list 'nedap.blocking-queues.impl.cljs-macros/offer! ~'q ~'x ~'_ ~'t))))

(defmacro emit-poll! []
  `(defmacro ~(with-meta 'poll! {:tag      (list 'quote (-> #'protocols.blocking-queue/poll! meta :tag))
                                 :arglists (list 'quote (-> #'protocols.blocking-queue/poll! meta :arglists))
                                 :doc      (-> #'protocols.blocking-queue/poll! meta :doc)})
     [~'q ~'_ ~'t]
     (if (-> ~'&env :ns nil?)
       (list 'nedap.blocking-queues.protocols.blocking-queue/poll! ~'q ~'_ ~'t)
       (list 'nedap.blocking-queues.impl.cljs-macros/poll! ~'q ~'_ ~'t))))

(defmacro emit-select! []
  `(defmacro ~(with-meta 'select! {:tag      (list 'quote (-> #'protocols.blocking-queue/select! meta :tag))
                                   :arglists (list 'quote (-> #'protocols.blocking-queue/select! meta :arglists))
                                   :doc      (-> #'protocols.blocking-queue/select! meta :doc)})
     [~'ops ~'opts]
     (if (-> ~'&env :ns nil?)
       (list 'nedap.blocking-queues.protocols.blocking-queue/select! ~'ops ~'opts)
       (list 'nedap.blocking-queues.impl.cljs-macros/select! ~'ops ~'opts))))
