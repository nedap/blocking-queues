(ns nedap.blocking-queues.test-runner
  (:require
   [cljs.nodejs :as nodejs]
   [nedap.utils.test.api :refer-macros [run-tests]]
   [unit.nedap.blocking-queues.api]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests
   'unit.nedap.blocking-queues.api))

(set! *main-cli-fn* -main)
