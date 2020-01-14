(ns functional.nedap.blocking-queues.api
  (:require
   [clojure.core.async :as async]
   #?(:clj [clojure.test :refer [deftest testing are is use-fixtures]] :cljs [cljs.test :refer-macros [deftest testing is are] :refer [use-fixtures]])
   [clojure.spec.alpha :as spec]
   [nedap.blocking-queues.api :as sut :refer [offer! poll! select!]]
   [nedap.speced.def :as speced]
   [nedap.utils.spec.api #?(:clj :refer :cljs :refer-macros) [check!]])
  #?(:cljs (:require-macros [functional.nedap.blocking-queues.api :refer [deftest-async]] [nedap.blocking-queues.api :refer [poll! select! offer!]])))

(defn aborting-ex-handler [e]
  (-> e pr-str println)
  #?(:clj  (System/exit 1)
     :cljs (set! js/process.exitCode 1)))

(def check-specs? true)

(spec/def ::any any?)

(def fair? false)

#?(:cljs (goog-define ^boolean ASSERT true))

#?(:clj (defmacro deftest-async
          {:style/indent 1}
          [name & args]
          (assert (symbol? name))
          (assert (-> args count pos?))
          (let [clj? (-> &env :ns nil?)]
            (if clj?
              `(deftest ~name
                 ~@args)
              `(cljs.test/deftest ~name
                 (cljs.test/async done#
                   (async/go
                     ~@args
                     (done#))))))))

(def default-blocking-queue-opts {:spec          ::any
                                  :queue-size    1
                                  :fair?         false
                                  :error-handler aborting-ex-handler
                                  :check-specs?  check-specs?})

(def default-rendezvous-opts (dissoc default-blocking-queue-opts :queue-size))

(deftest-async select!-1
  (testing "The first op, a `poll!`, can be selected"
    (let [a (sut/blocking-queue default-blocking-queue-opts)
          b (sut/blocking-queue default-blocking-queue-opts)
          a-offered-value ::A
          b-offered-value ::B
          timeout 1000
          proof (atom false)]

      (async/go
        (and (offer! a a-offered-value :ms timeout)
             (reset! proof true)))

      (async/go
        (async/<! (async/timeout (* timeout 5))) ;; make it impossible that the `b` op can win
        (offer! b b-offered-value :ms timeout))

      (is (= a-offered-value
             (-> (select! [a b] {:ms timeout})
                 first)))

      (is @proof))))

(deftest-async select!-2
  (testing "The non-first op, a `poll!`, can be selected"
    (let [a (sut/blocking-queue default-blocking-queue-opts)
          b (sut/blocking-queue default-blocking-queue-opts)
          a-offered-value ::A
          b-offered-value ::B
          timeout 1000
          proof (atom false)]

      (async/go
        (async/<! (async/timeout (* timeout 5))) ;; make it impossible that the `a` op can win
        (offer! a a-offered-value :ms timeout))

      (async/go
        (and (offer! b b-offered-value :ms timeout)
             (reset! proof true)))

      (is (= b-offered-value
             (-> (select! [a b] {:ms timeout})
                 first)))

      (is @proof))))

(deftest-async select!-3
  (testing "The first op, an `offer!`, can be selected"
    (let [a (sut/blocking-queue default-blocking-queue-opts)
          b (sut/blocking-queue default-blocking-queue-opts)
          a-offered-value ::A
          b-offered-value ::B
          timeout 1000
          proof (atom false)]

      (is (sut/offer! a ::anything :ms 10))
      (is (sut/offer! b ::anything :ms 10))

      (async/go
        (and (poll! a :ms timeout)
             (reset! proof true)))

      (async/go
        (async/<! (async/timeout (* timeout 5))) ;; make it impossible that the `b` op can win
        (poll! b :ms timeout))

      (is (= a
             (-> (select! [[a a-offered-value] [b b-offered-value]]
                          {:ms timeout})
                 second)))

      (is @proof))))

(deftest-async select!-4
  (testing "The non-first op, an `offer!`, can be selected"
    (let [a (sut/blocking-queue default-blocking-queue-opts)
          b (sut/blocking-queue default-blocking-queue-opts)
          a-offered-value ::A
          b-offered-value ::B
          timeout 1000
          proof (atom false)]

      (is (sut/offer! a ::anything :ms 10))
      (is (sut/offer! b ::anything :ms 10))

      (async/go
        (async/<! (async/timeout (* timeout 5))) ;; make it impossible that the `a` op can win
        (poll! a :ms timeout))

      (async/go
        (and (poll! b :ms timeout)
             (reset! proof true)))

      (is (= b
             (-> (select! [[a a-offered-value] [b b-offered-value]]
                          {:ms timeout})
                 second)))

      (is @proof))))

(deftest-async select!-5
  (testing "It can time out, on polling"
    (let [rendezvous #?(:clj  (sut/rendezvous default-rendezvous-opts)
                        :cljs (sut/blocking-queue default-blocking-queue-opts))
          timeout 1000
          extra-timeout timeout
          proof (atom false)
          sleep (+ timeout extra-timeout)]

      #?(:clj (async/go
                (async/<! (async/timeout sleep))
                (or (offer! rendezvous ::anything :ms timeout)
                    (reset! proof true))))

      (is (nil? (select! [rendezvous] {:ms timeout})))

      #?(:clj
         (do
           (async/<!! (async/timeout (+ sleep extra-timeout 10)))
           (is @proof))))))

(deftest-async select!-6
  (testing "It can time out, on offering"
    (let [rendezvous #?(:clj  (sut/rendezvous default-rendezvous-opts)
                        :cljs (sut/blocking-queue default-blocking-queue-opts))
          timeout 1000
          extra-timeout timeout
          proof (atom false)
          sleep (+ timeout extra-timeout)]

      #?(:clj  (async/go
                 (async/<! (async/timeout sleep))
                 (or (poll! rendezvous :ms timeout)
                     (reset! proof true)))
         :cljs (is (offer! rendezvous ::anything :ms timeout)))

      (is (nil? (select! [[rendezvous ::anything]] {:ms timeout})))

      #?(:clj (do
                (async/<!! (async/timeout (+ sleep extra-timeout 10)))
                (is @proof))))))

#?(:clj
   (deftest-async rendezvous-1
     (testing "Timeouts can succeed, instead of blocking the current thread if no other thread interacts with the rendezvous"
       (testing "Polling"
         (is (not (poll! (sut/rendezvous default-rendezvous-opts)
                         :ms 10)))))))

#?(:clj
   (deftest-async rendezvous-2
     (testing "Timeouts can succeed, instead of blocking the current thread if no other thread interacts with the rendezvous"
       (testing "Offering"
         (is (not (offer! (sut/rendezvous default-rendezvous-opts)
                          ::anything :ms 10)))))))

(def lower-bound 1)

(def upper-bound 10)

(assert (> upper-bound lower-bound))

(deftest-async sliding
  (testing sut/sliding-queue
    (let [next-work-item (atom 0)
          q (sut/sliding-queue default-blocking-queue-opts)
          timeout 5]

      (dotimes [i upper-bound]
        (swap! next-work-item inc)
        (offer! q @next-work-item :ms timeout))

      (is (= upper-bound
             (poll! q :ms timeout))))))

(deftest-async dropping
  (testing sut/dropping-queue
    (let [next-work-item (atom 0)
          q (sut/dropping-queue default-blocking-queue-opts)
          timeout 5]

      (dotimes [i upper-bound]
        (swap! next-work-item inc)
        (offer! q @next-work-item :ms timeout))

      (is (= lower-bound
             (poll! q :ms timeout))))))

(spec/def ::number number?)

(when #?(:clj  *assert*
         :cljs ASSERT)
  (deftest-async spec-checking--assert-true
    (testing "\"NaN\" throws an exception due to *assert* (even in face of a void `ex-handler`, and a false `check-specs?`)"
      (let [proof (atom nil)
            q (sut/blocking-queue {:spec          ::number
                                   :queue-size    1
                                   :fair?         false
                                   :error-handler (fn [x]
                                                    #?(:cljs (reset! proof
                                                                     (-> x .-message)))
                                                    true)
                                   :check-specs?  false})]
        (testing "red path"
          #?(:clj  (is (spec-assertion-thrown? ::number (offer! q "NaN" :ms 10)))
             :cljs (do
                     (offer! q "NaN" :ms 10)
                     (is (= "Assert failed: \"NaN\" does not satisfy the :functional.nedap.blocking-queues.api/number spec.\n(spec/valid? spec input)"
                            @proof)))))

        (testing "green path"
          (try
            (offer! q 42 :ms 10)
            (is true)
            (catch #?(:clj  Throwable
                      :cljs :default) e
              (is false (pr-str e)))))))))

(when-not #?(:clj  *assert*
             :cljs ASSERT)
  (deftest-async spec-checking--assert-false
    (are [check? v obtained-error] (testing [check? v]
                                     (let [proof (atom nil)
                                           q (sut/blocking-queue {:spec          ::number
                                                                  :queue-size    1
                                                                  :fair?         false
                                                                  :error-handler (fn [x]
                                                                                   (reset! proof
                                                                                           #?(:clj (-> x ex-data :explanation)
                                                                                              :cljs (-> x .-message)))
                                                                                   true)
                                                                  :check-specs?  check?})]
                                       (#?(:clj   with-out-str
                                           ;; with-out-str eats part of the cljs.test output, somehow. Avoid that
                                           :cljs do)
                                        (offer! q v :ms 10))
                                       (is (= obtained-error
                                              @proof)))
                                     true)
      true  "NaN" #?(:clj  "\"NaN\" - failed: number? spec: :functional.nedap.blocking-queues.api/number\n"
                     :cljs "\"NaN\" does not satisfy the :functional.nedap.blocking-queues.api/number spec.")
      true  42    nil
      false "NaN" nil
      false 42    nil)))
