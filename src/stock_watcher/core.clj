(ns stock-watcher.core
  (:require [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]))

(defn zip-str [s]
  (zip/xml-zip
    (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(def base-url "http://asp1.krx.co.kr/servlet/krx.asp.XMLSise?code=")

;; get stock price
(defn get-stock-price [stock-code]
  (zip-str
    (string/trim
      (:body (client/get (str base-url stock-code))))))

;; get current stock price
(defn get-current-stock-price [stock-code]
  (-> (get-stock-price stock-code)
      first
      :content
      (get 2)
      :attrs
      :CurJuka))