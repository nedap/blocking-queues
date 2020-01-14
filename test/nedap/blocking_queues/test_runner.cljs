(ns nedap.blocking-queues.test-runner
  (:require
   [cljs.nodejs :as nodejs]
   [functional.nedap.blocking-queues.api]
   [nedap.utils.test.api :refer-macros [run-tests]]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests 'functional.nedap.blocking-queues.api))

(set! *main-cli-fn* -main)
