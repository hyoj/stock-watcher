(ns stock-watcher.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [morse.handlers :as h]
            [morse.api :as api]
            [morse.polling :as p]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.jobs :refer [defjob]]
            [clojurewerkz.quartzite.schedule.daily-interval :refer [schedule monday-through-friday starting-daily-at time-of-day ending-daily-at with-interval-in-minutes with-interval-in-seconds]]))

(defn zip-str [s]
  (zip/xml-zip
    (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(def token (System/getenv "TELEGRAM_TOKEN"))
(def base-url "http://asp1.krx.co.kr/servlet/krx.asp.XMLSise?code=")
(def base-en-url "http://asp1.krx.co.kr/servlet/krx.asp.XMLSiseEng?code=")

;; get stock price
(defn get-stock-price [stock-code]
  (zip-str
    (string/trim
      (:body (client/get (str base-en-url stock-code))))))

;; get current stock price
(defn get-current-stock-price [stock-code]
  (-> (get-stock-price stock-code)
      first
      :content
      (get 2)
      :attrs
      :CurJuka))

;; check stock code is valid
(defn check-stock-code [stock-code]
  (not (nil? (re-seq #"^\d{6}$" stock-code))))

; {"stock-code1" ("telegram-chat-id1" "telegram-chat-id2")}
(def data (atom {}))

(h/defhandler bot-api
              (h/command "start" {{username :username} :from {id :id :as chat} :chat}
                         (println "User" username "joined")
                         (api/send-text token id {:parse_mode "html"}
                                        (str "안녕하세요. <b><i>" username "</i></b> 님\n"
                                             "개발중인 bot 입니다.")))

              (h/command "hello" {{id :id :as chat} :chat}
                         (println "hello was requested in " chat)
                         (api/send-text token id {:parse_mode "Markdown"}
                                        "*Hello*, fellows :)"))

              (h/command "subscribe" {{id :id :as chat} :chat text :text}
                         (println "subscribe was requested in " chat text)
                         (let [stock-code (first (re-seq #"\d{6}" text))]
                           (if (check-stock-code stock-code)
                             (do
                               (swap! data update stock-code conj id)
                               (api/send-text token id
                                              (str "주식종목코드 " stock-code " 알림이 시작되었습니다.\n"
                                                   "매 30분 마다 현재가 알림을 받게됩니다.")))
                             (api/send-text token id "유효하지 않은 주식종목코드 입니다."))))

              (h/message {{id :id :as chat} :chat text :text :as message}
                         (println "Intercepted message:" message)
                         (when (check-stock-code text)
                           (api/send-text token id (get-current-stock-price text)))))


(declare channel)

(defn start-bot []
  (println "Starting bot...")
  (alter-var-root #'channel (constantly (p/start token bot-api))))

(defn stop-bot []
  (when channel
    (println "Stopping bot..." channel)
    (alter-var-root #'channel (constantly (p/stop channel)))))

(defn restart-bot []
  (stop-bot)
  (start-bot)
  (println "Current channel: " channel))

;(restart-bot)

(defjob send-current-stock-price-to-bot
        [ctx]
        (doseq [[stock-code chat-ids] @data]
          (doseq [chat-id chat-ids]
            (api/send-text token chat-id {:parse_mode "html"}
                           (str "주식종목코드 <b>" stock-code "</b>\n"
                                "현재가: <i>"(get-current-stock-price stock-code) "</i>" )))
          (prn stock-code chat-ids))
        (println "`send-current-stock-price-to-bot` working..")
        )

(defn -main [& m]
  (start-bot)
  (let [s   (-> (qs/initialize) qs/start)
        job (j/build
              (j/of-type send-current-stock-price-to-bot)
              (j/with-identity (j/key "jobs.noop.1")))
        trigger (t/build
                  (t/with-identity (t/key "triggers.1"))
                  (t/start-now)
                  (t/with-schedule (schedule
                                     (with-interval-in-seconds 5)
                                     (monday-through-friday)
                                     (starting-daily-at (time-of-day 9 00 00))
                                     (ending-daily-at (time-of-day 16 00 00)))))]
    (qs/schedule s job trigger))
  )

