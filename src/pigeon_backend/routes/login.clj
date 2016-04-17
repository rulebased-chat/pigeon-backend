(ns pigeon-backend.routes.login
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.util.http-status :as status]
            [schema.core :as s]
            [pigeon-backend.services.user-service :as user-service]
            [buddy.sign.jws :as jws]
            [clj-time.core :as t]
            [environ.core :refer [env]]
            [pigeon-backend.middleware :refer [wrap-authentication]]))

(def login-routes
  (context "/user" []
    :tags ["registration"]

    (POST "/login" []
      :body-params [username :- String,
                    password :- String]
      :summary "Logs user in iff username and password match the database"
      (if-let [has-access? (user-service/check-credentials
                            {:username username
                             :password password})]
        (let [token (jws/sign {:user username :roles ["app-frontpage"]} (env :jws-shared-secret))
              response (ok {:token token})]
          (-> response
              (assoc-in [:cookies "token" :value] token)
              (assoc-in [:cookies "token" :max-age] 14400)
              (assoc-in [:cookies "token" :http-only] true)
              (assoc-in [:cookies "token" :path] "/")))
        (let [response (unauthorized)]
          (-> response
              (assoc-in [:cookies :max-age] 0)))))))