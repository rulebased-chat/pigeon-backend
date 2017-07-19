(ns pigeon-backend.websocket
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [ring.server.standalone :as ring]
            [environ.core :refer [env]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [buddy.sign.jws :as jws]
            [immutant.web :as immutant]
            [immutant.web.async :as async]
            [immutant.web.middleware :refer [wrap-websocket]]
            [cognitect.transit :as transit])
  (:import  [java.io
             ByteArrayOutputStream]))

(defonce channels (atom {}))

(defn async-send! [channels message]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)
        _ (transit/write writer message)
        message (.toString out)]
    (prn "[Websocket OUT]" (keys channels) message)
    (doseq [[_ channel] channels]
      (async/send! channel message))))

(defn ws-app
  "For passing information when to reload messages,
                                   reload turns,
                                   or to get notifications of new messages"
  [request username]
  (async/as-channel request
    {:on-open    (fn [channel]
                   (swap! channels assoc username channel)
                   (async-send! {username channel} "Async ready")
                   (prn "channel open" @channels))
     :on-message (fn [channel m]
                   ;; todo: enable support
                   ;; (and don't allow broadcasting, unless moderator-restricted)
                   ;; (doseq [channel @channels]
                   ;;   (async/send! channel m))
                   )
     :on-close   (fn [channel {:keys [code reason]}]
                   (prn "close code:" code "reason:" reason)
                   (swap! channels dissoc username))}))