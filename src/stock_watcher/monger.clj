(ns stock-watcher.monger
  (:require [monger.core :as mg]
            [monger.collection :as mc]))

(declare conn)
(declare db)
(defonce coll "subscribers")

(defn connect-to-mongo
  []
  (alter-var-root #'conn (fn [_] (mg/connect {:host (System/getenv "MONGO_ADDR")})))
  (alter-var-root #'db (fn [_] (mg/get-db conn "stock-watcher"))))

(defn register-subscription
  [stock-code chat-id]
  (let [found (mc/find-one-as-map db coll {:stockCode stock-code})]
    (mc/update db "subscribers" {:stockCode stock-code}
               {:stockCode stock-code :chatIds (into [chat-id] (set (:chatIds found)))} {:upsert true})))

(defn cancel-subscription
  [stock-code chat-id]
  (let [found (mc/find-one-as-map db coll {:stockCode stock-code})]
    (when (some? found)
      (mc/update db "subscribers" {:stockCode stock-code}
                 {:stockCode stock-code :chatIds (into [] (disj (set (:chatIds found)) chat-id))}))))

(defn clear-empty-subscription
  []
  (mc/remove db coll {:chatIds []}))

(defn fetch-all-subscription
  []
  (mc/find-maps db coll))