(ns stock-watcher.quotes
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [java-time :as time]
            [beicon.core :as rx]
            [clojure.pprint :as pprint]))

(def http-req-info {:url     "http://finance.daum.net/api/quotes/sectors?market=KOSPI&changes=UPPER_LIMIT%2CRISE%2CEVEN%2CFALL%2CLOWER_LIMIT"
                    :headers {:referer    "http://finance.daum.net/domestic/all_stocks"
                              :user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3651.0 Safari/537.36"}})

(def working-time {:start-time-as-second (time/as (time/local-time 9 00) :second-of-day)
                   :end-time-as-second   (time/as (time/local-time 16 00) :second-of-day)
                   :fetch-interval-time-as-second 30
                   :send-interval-time-as-second (* 60 10)})

(def all-stocks (atom {}))

;; check stock code is valid
(defn check-stock-code
  [stock-code]
  (not (nil? (re-seq #"^\d{6}$" stock-code))))

(defn get-all-stocks
  []
  (:data (json/read-str (:body (client/get (:url http-req-info)
                                           {:headers (:headers http-req-info)}))
                        :key-fn keyword)))

(defn get-stock-info
  [stock-code]
  (first (distinct
           (filter #(= 0
                       (compare (:symbolCode %)
                                (str "A" stock-code)))
                   @all-stocks))))

(defn format-krw
  ([number]
   (pprint/cl-format nil "~:d" number))
  ([number leading-sign]
   (if leading-sign
     (pprint/cl-format nil "~:@d" number)
     (format-krw number))))

(defn format-change-rate
  [changeRate]
  (clojure.pprint/cl-format nil "~,2f" (* changeRate 100)))

(defn make-stock-msg
  [stock-code]
  (let [{:keys [name symbolCode tradePrice change changePrice changeRate]}
        (get-stock-info stock-code)]
    (str "<b>" name "</b> (" (subs symbolCode 1) ")\n"
         "<i>" (format-krw tradePrice) "</i>  "
         (case change "RISE" "▲"
                      "EVEN" "-"
                      "FALL" "▼") (format-krw changePrice) "  "
         (format-change-rate changeRate) "%")))

(defn reset-all-stocks
  []
  (reset! all-stocks (flatten (map :includedStocks (get-all-stocks)))))

(declare fetcher)
(defn get-fetcher []
  (reset-all-stocks)
  (rx/subscribe (->> (rx/interval 1000)
                     (rx/map (fn [_] (time/local-date-time)))
                     (rx/filter #(time/weekday? %))
                     (rx/filter #(and (>= (time/as % :second-of-day)
                                          (:start-time-as-second working-time))
                                      (<= (time/as % :second-of-day)
                                          (:end-time-as-second working-time))))
                     (rx/filter #(= 0
                                    (mod (time/as % :second-of-day)
                                         (:fetch-interval-time-as-second working-time)))))
                (fn [v]
                  (reset-all-stocks)
                  (println "[quotes] on-value:" v))
                #(println "[quotes] on-error:" %)
                #(println "[quotes] on-end")))