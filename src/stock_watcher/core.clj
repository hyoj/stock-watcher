(ns stock-watcher.core
  (:gen-class)
  (:require [stock-watcher.quotes :refer :all]
            [stock-watcher.monger :refer :all]
            [morse.handlers :as h]
            [morse.api :as api]
            [morse.polling :as p]
            [beicon.core :as rx]
            [java-time :as time]))

(def token (System/getenv "TELEGRAM_TOKEN"))

;; check stock code is valid
(defn check-stock-code [stock-code]
  (not (nil? (re-seq #"^\d{6}$" stock-code))))

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
                               (register-subscription stock-code id)
                               (api/send-text token id
                                              (str "주식종목코드 " stock-code " 알림이 시작되었습니다.\n"
                                                   "매 30분 마다 현재가 알림을 받게됩니다.")))
                             (api/send-text token id "유효하지 않은 주식종목코드 입니다."))))

              (h/message {{id :id :as chat} :chat text :text :as message}
                         (println "Intercepted message:" message)
                         (when (check-stock-code text)
                           (api/send-text token id (:tradePrice (get-stock-info text))))))


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
(declare sender-subscriber)
(defn get-sender-subscriber []
  (rx/subscribe (->> (rx/interval 1000)
                     (rx/map (fn [_] (time/local-date-time)))
                     ;(rx/map #(do (println %)
                     ;             %))
                     (rx/filter #(when (not-empty @all-stocks) %))
                     (rx/filter #(time/weekday? %))
                     (rx/filter #(and (>= (time/as % :second-of-day)
                                          (:start-time-as-second working-time))
                                      (<= (time/as % :second-of-day)
                                          (:end-time-as-second working-time))))
                     (rx/filter #(= 0
                                    (mod (time/as % :second-of-day)
                                         (:interval-as-second working-time))))
                     (rx/map #(do (println (str % "[core] "))
                                  %)))
                (fn [v]
                  (let [all-subscription (fetch-all-subscription)]
                    (doseq [subscription all-subscription]
                      (doseq [chat-id (:chatIds subscription)]
                        (api/send-text token chat-id {:parse_mode "html"}
                                       (make-stock-msg subscription)))
                      (prn subscription)))
                  (println "[core] on-value:" v))
                #(println "[core] on-error:" %)
                #(println "[core] on-end")))

(defn -main []
  (alter-var-root #'fetcher (fn [_] (get-fetcher)))
  (connect-to-mongo)
  (start-bot)
  (alter-var-root #'sender-subscriber (fn [_] (get-sender-subscriber)))
  (while true (Thread/sleep 10000)))

