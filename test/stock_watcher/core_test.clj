(ns stock-watcher.core-test
  (:require [clojure.test :refer :all]
            [stock-watcher.core :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
