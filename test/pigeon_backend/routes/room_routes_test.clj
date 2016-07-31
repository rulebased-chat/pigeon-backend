(ns pigeon-backend.routes.room-routes-test
  (:require [clojure.test :refer [deftest]]
            [cheshire.core :as cheshire]
            [midje.sweet :refer :all]
            [pigeon-backend.handler :refer :all]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [pigeon-backend.test-util :refer [empty-and-create-tables
                                              parse-body]]
            [pigeon-backend.services.room-service :as room-service]
            [buddy.sign.jws :as jws]
            [pigeon-backend.test-util :refer [login-as-test-user]]))

(def room-data {:name "Huone"})

(deftest room-routes-test
  (facts "Route: room"
    (with-state-changes [(before :facts (empty-and-create-tables))]
      (fact "Create"
        (let [{status :status body :body}
              ((app-with-middleware)
               (mock/content-type
                (mock/body
                  (login-as-test-user (mock/request :post "/api/v0/room"))
                  (json/write-str room-data))
                "application/json"))]
          status => 200
          (parse-body body) => (contains {:name "Huone"})))
      (fact "Read"
        (dotimes [n 2]
          (room-service/room-create! {:name (str "Huone " n)}))
        (let [{status :status body :body}
              ((app-with-middleware)
               (mock/content-type
                (mock/body
                  (login-as-test-user (mock/request :get "/api/v0/room"))
                  (json/write-str {:name "Huone 1"}))
                "application/json"))]
          status => 200
          (parse-body body) => (contains [(contains {:name "Huone 1"})])))
      (fact "Update"
        (let [{id :id} (room-service/room-create! room-data)
              {status :status body :body}
              ((app-with-middleware)
               (mock/content-type
                (mock/body
                  (login-as-test-user (mock/request :put "/api/v0/room"))
                  (json/write-str {:id id
                                   :name "Huone 2"}))
                "application/json"))]
          status => 200
          (parse-body body) => (contains {:name "Huone 2"})))
      (fact "Delete"
        (let [{id :id} (room-service/room-create! room-data)
              {status :status body :body}
              ((app-with-middleware)
               (mock/content-type
                (mock/body
                  (login-as-test-user (mock/request :delete "/api/v0/room"))
                  (json/write-str {:id id}))
                "application/json"))]
          status => 200
          (parse-body body) => (contains {:name "Huone"}
                                         {:deleted true}))))))