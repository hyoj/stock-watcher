(ns stock-watcher.core
  (:require [clj-http.client :as client]))

(let [resp (client/get "http://asp1.krx.co.kr/servlet/krx.asp.XMLSise?code=035420")
      body (:body resp)]
  (println body))

