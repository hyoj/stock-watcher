(defproject stock-watcher "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/test.check "0.9.0"]
                 [clj-http "3.9.1"]
                 [morse "0.4.0"]
                 [org.clojure/data.json "0.2.6"]
                 [funcool/beicon "4.1.0"]
                 [clojure.java-time "0.3.2"]
                 [com.novemberain/monger "3.1.0"]]
  :jvm-opts ["-Dfile.encoding=utf-8"]
  :main ^:skip-aot stock-watcher.core
  :profiles {:uberjar {:aot :all}})
