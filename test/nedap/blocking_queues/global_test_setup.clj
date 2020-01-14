(ns nedap.blocking-queues.global-test-setup)

(when (System/getenv "CI")
  (-> (reify Thread$UncaughtExceptionHandler
        (uncaughtException [_ thread e]
          (-> e pr-str println)
          (System/exit 1)))
      (Thread/setDefaultUncaughtExceptionHandler)))
