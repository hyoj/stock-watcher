(ns stock-watcher.quotes
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [beicon.core :as rx]))

(def http-req-info {:url     "http://finance.daum.net/api/quotes/sectors?market=KOSPI&changes=UPPER_LIMIT%2CRISE%2CEVEN%2CFALL%2CLOWER_LIMIT"
                    :headers {:referer    "http://finance.daum.net/domestic/all_stocks"
                              :user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3651.0 Safari/537.36"}})

(defn get-all-stocks-kospi
  []
  (:data (json/read-str (:body (client/get (:url http-req-info)
                                           {:headers (:headers http-req-info)}))
                        :key-fn keyword)))

(def all-stocks (atom {}))

(def disposable
  (rx/subscribe (rx/interval 2000)
                (fn [v]
                  (reset! all-stocks (flatten (map :includedStocks (get-all-stocks-kospi))))
                  (println "on-value:" v))
                #(println "on-error:" %)
                #(println "on-end")))

(.dispose disposable)

(defn get-stock-info
  [stock-code]
  (first (distinct
     (filter #(= 0
                 (compare (:symbolCode %)
                          (str "A" stock-code)))
             @all-stocks))))