(ns dev
  (:require
   [clj-java-decompiler.core :refer [decompile]]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.test :refer [run-all-tests run-tests]]
   [clojure.tools.namespace.repl :refer [clear refresh refresh-dirs set-refresh-dirs]]
   [criterium.core :refer [quick-bench]]
   [formatting-stack.branch-formatter :refer [format-and-lint-branch! lint-branch!]]
   [formatting-stack.compilers.test-runner :refer [test!]]
   [formatting-stack.component]
   [formatting-stack.project-formatter :refer [format-and-lint-project! lint-project!]]
   [lambdaisland.deep-diff]))

(set-refresh-dirs "src" "test" "dev")

(defn suite []
  (refresh)
  (run-all-tests #".*\.nedap\.blocking-queues.*"))

(defn unit []
  (refresh)
  (run-all-tests #"unit\.nedap\.blocking-queues.*"))

(defn slow []
  (refresh)
  (run-all-tests #"integration\.nedap\.blocking-queues.*"))

(defn diff [x y]
  (-> x
      (lambdaisland.deep-diff/diff y)
      (lambdaisland.deep-diff/pretty-print)))

(defn gt
  "gt stands for git tests"
  []
  (refresh)
  (test!))
