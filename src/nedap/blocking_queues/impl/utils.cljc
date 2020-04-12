(ns nedap.blocking-queues.impl.utils
  (:require
   [clojure.spec.alpha :as spec]
   [nedap.speced.def :as speced]))

(speced/defn filter-of [spec, ^boolean? check-unconditionally?]
  (fn [rf]
    (fn
      ([]
       (rf))

      ([result]
       (rf result))

      ([result input]
       ;; The `*assert*` variable should NOT be observed directly in cljs code. Only use `(assert ...)`
       (assert (spec/valid? spec input)
               (str (pr-str input)
                    " does not satisfy the "
                    (pr-str spec)
                    " spec."))
       (if (or (not check-unconditionally?)
               (and check-unconditionally?
                    (spec/valid? spec input)))
         (rf result input)
         (throw (ex-info (str (pr-str input)
                              " does not satisfy the "
                              (pr-str spec)
                              " spec.")
                         {})))))))
