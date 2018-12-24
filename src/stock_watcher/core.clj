(ns stock-watcher.core
  (:require [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [morse.handlers :as h]
            [morse.api :as api]
            [morse.polling :as p]))

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

;(get-stock-price "000830")                                  ;
;(get-stock-price "036460")                                  ; 한국가스공사

;; get current stock price
(defn get-current-stock-price [stock-code]
  (-> (get-stock-price stock-code)
      first
      :content
      (get 2)
      :attrs
      :CurJuka))

;(get-current-stock-price "036460")

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

              (h/message {{id :id :as chat} :chat text :text :as message}
                         (println "Intercepted message:" message)
                         (when (check-stock-code text)
                           (api/send-text token id (get-current-stock-price text)))))

(def channel (p/start token bot-api))

;stop created background processes
;(p/stop channel)
