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

; TODO 메세지 뭉쳐서 보내기
; TODO ret overload 기능 or 하나만 있을 때는 종목코드 / 두개 있을 때는 종목코드 알림 분단위 설정

(h/defhandler bot-api
              (h/command "start" {{username :username} :from {id :id :as chat} :chat}
                         (println "User" username "joined")
                         (api/send-text token id {:parse_mode "Markdown"}
                                        (str "안녕하세요. *" username "* 님\n"
                                             "KOSPI 현재가 알림 bot 입니다.\n\n"
                                             "사용방법은 /help 를 참고해주세요.")))

              (h/command "help" {{id :id :as chat} :chat}
                         (println "help was requested in " chat)
                         (api/send-text token id {:parse_mode "Markdown"}
                                        (str "/s 종목코드 - 현재가 알림 설정하기\n"
                                             "/u 종목코드 - 현재가 알림 설정 해지하기\n"
                                             "종목코드 - 현재가 확인하기")))

              (h/command "s" {{id :id :as chat} :chat text :text}
                         (println "subscribe was requested in " chat text)
                         (let [stock-code (first (re-seq #"\d{6}" text))]
                           (if (check-stock-code stock-code)
                             (do
                               (register-subscription stock-code id)
                               (let [stock-name (:name (get-stock-info stock-code))]
                                 (api/send-text token id
                                                (str stock-name "(" stock-code ") 알림이 설정되었습니다.\n"
                                                     "평일 9시 ~ 16시, 10분 마다 현재가를 알려드립니다.\n"))))
                             (api/send-text token id "유효하지 않은 종목코드 입니다."))))

              (h/command "u" {{id :id :as chat} :chat text :text}
                         (println "unsubscribe was requested in " chat text)
                         (let [stock-code (first (re-seq #"\d{6}" text))]
                           (if (check-stock-code stock-code)
                             (do
                               (cancel-subscription stock-code id)
                               (let [stock-name (:name (get-stock-info stock-code))]
                                 (api/send-text token id
                                                (str stock-name "(" stock-code ") 알림이 해지되었습니다.\n"))))
                             (api/send-text token id "유효하지 않은 종목코드 입니다."))))

              (h/message {{id :id :as chat} :chat text :text :as message}
                         (println "Intercepted message:" message)
                         (when (check-stock-code text)
                           (api/send-text token id {:parse_mode "html"}
                                          (str (make-stock-msg text))))))


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
                                         (:send-interval-time-as-second working-time))))
                     (rx/map #(do (println (str % "[core] "))
                                  %)))
                (fn [v]
                  (let [all-subscription (fetch-all-subscription)]
                    (doseq [subscription all-subscription]
                      (doseq [chat-id (:chatIds subscription)]
                        (api/send-text token chat-id {:parse_mode "html"}
                                       (make-stock-msg (:stockCode subscription))))
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
