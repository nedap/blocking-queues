;; Please don't bump the library version by hand - use ci.release-workflow instead.
(defproject com.nedap.staffing-solutions/blocking-queues "unreleased"
  ;; Please keep the dependencies sorted a-z.
  :dependencies [[com.nedap.staffing-solutions/speced.def "2.0.0-alpha1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.7.559"]]

  :description "A safer subset of j.u.c.BlockingQueue / clojure.core.async."

  :url "https://github.com/nedap/blocking-queues"

  :min-lein-version "2.0.0"

  :license {:name "EPL-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :signing {:gpg-key "releases-staffingsolutions@nedap.com"}

  :repositories {"releases" {:url      "https://nedap.jfrog.io/nedap/staffing-solutions/"
                             :username :env/artifactory_user
                             :password :env/artifactory_pass}}

  :repository-auth {#"https://nedap.jfrog\.io/nedap/staffing-solutions/"
                    {:username :env/artifactory_user
                     :password :env/artifactory_pass}}

  :target-path "target/%s"

  ;; Note that the following options aren't passed to production servers (unless using Leiningen there):
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" ;; increase stacktrace usefulness
             "-server" ;; JCIP recommends exercising server optimizations in all envs.
             ;; The following flags setup GC with short STW pauses, which tend to be apt for webserver workloads.
             ;; Taken from https://docs.oracle.com/cd/E40972_01/doc.70/e40973/cnf_jvmgc.htm#autoId2
             ;; Note that -Xmx and -Xms are unset, since developers can have different needs around that.
             "-XX:+UseG1GC"
             "-XX:MaxGCPauseMillis=200"
             "-XX:ParallelGCThreads=20"
             "-XX:ConcGCThreads=5"
             "-XX:InitiatingHeapOccupancyPercent=70"
             "-Dclojure.read.eval=false"]

  :monkeypatch-clojure-test false

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-pprint "1.1.2"]]

  ;; Please don't add `:hooks [leiningen.cljsbuild]`. It can silently skip running the JS suite on `lein test`.
  ;; It also interferes with Cloverage.
  :cljsbuild {:builds {"test"           {:source-paths ["src" "test"]
                                         :compiler     {:main          nedap.blocking-queues.test-runner
                                                        :output-to     "target/test/out/tests.js"
                                                        :output-dir    "target/test/out"
                                                        :target        :nodejs
                                                        :optimizations :none}}
                       "test-no-assert" {:source-paths ["src" "test"]
                                         :compiler     {:main            nedap.blocking-queues.test-runner
                                                        :output-to       "target/test-no-assert/out/tests.js"
                                                        :output-dir      "target/test-no-assert/out"
                                                        :target          :nodejs
                                                        :elide-asserts   true
                                                        :closure-defines {"functional.nedap.blocking_queues.api.ASSERT" false}
                                                        :optimizations   :none}}}}

  ;; A variety of common dependencies are bundled with `nedap/lein-template`.
  ;; They are divided into two categories:
  ;; * Dependencies that are possible or likely to be needed in all kind of production projects
  ;;   * The point is that when you realise you needed them, they are already in your classpath, avoiding interrupting your flow
  ;;   * After realising this, please move the dependency up to the top level.
  ;; * Genuinely dev-only dependencies allowing 'basic science'
  ;;   * e.g. criterium, deep-diff, clj-java-decompiler

  ;; NOTE: deps marked with #_"transitive" are there to satisfy the `:pedantic?` option.
  :profiles {:dev        {:dependencies [[cider/cider-nrepl "0.16.0" #_"formatting-stack needs it"]
                                         [com.clojure-goes-fast/clj-java-decompiler "0.2.1"]
                                         [com.nedap.staffing-solutions/utils.modular "2.0.0"]
                                         [com.nedap.staffing-solutions/utils.spec.predicates "1.1.0"]
                                         [com.stuartsierra/component "0.4.0"]
                                         [com.taoensso/timbre "4.10.0"]
                                         [criterium "0.4.5"]
                                         [formatting-stack "1.0.1"]
                                         [lambdaisland/deep-diff "0.0-29"]
                                         [medley "1.2.0"]
                                         [org.clojure/math.combinatorics "0.1.1"]
                                         [org.clojure/test.check "0.10.0-alpha3"]
                                         [org.clojure/tools.namespace "0.3.1"]]
                          :jvm-opts     ["-Dclojure.compiler.disable-locals-clearing=true"]
                          :plugins      [[lein-cloverage "1.1.1"]]
                          :source-paths ["dev"]
                          :repl-options {:init-ns dev}}

             :provided   {:dependencies [[org.clojure/clojurescript "1.10.597"
                                          :exclusions [com.cognitect/transit-clj
                                                       com.google.code.findbugs/jsr305
                                                       com.google.errorprone/error_prone_annotations]]
                                         [com.google.guava/guava "25.1-jre" #_"transitive"]
                                         [com.google.protobuf/protobuf-java "3.4.0" #_"transitive"]
                                         [com.cognitect/transit-clj "0.8.313" #_"transitive"]
                                         [com.google.errorprone/error_prone_annotations "2.1.3" #_"transitive"]
                                         [com.google.code.findbugs/jsr305 "3.0.2" #_"transitive"]
                                         [org.clojure/tools.reader "1.3.2"]]}

             :check      {:global-vars {*unchecked-math* :warn-on-boxed
                                        ;; avoid warnings that cannot affect production:
                                        *assert*         false}}

             :production {:jvm-opts    ["-Dclojure.compiler.elide-meta=[:doc :file :author :line :added :deprecated :nedap.speced.def/spec :nedap.speced.def/nilable]"
                                        "-Dclojure.compiler.direct-linking=true"
                                        "-Dclojure.read.eval=false"]
                          :global-vars {*assert* false}}

             :test       {:dependencies [[com.nedap.staffing-solutions/utils.test "1.6.2"]]
                          :jvm-opts     ["-Dclojure.core.async.go-checking=true"
                                         "-Duser.language=en-US"]}

             :ci         {:pedantic?    :abort
                          :jvm-opts     ["-Dclojure.main.report=stderr"]
                          :global-vars  {*assert* true} ;; `ci.release-workflow` relies on runtime assertions
                          :dependencies [[com.nedap.staffing-solutions/ci.release-workflow "1.6.0"]]}})
