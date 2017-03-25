(ns pigeon-backend.services.participant-service-test
  (:require [clojure.test :refer [deftest]]
            [cheshire.core :as cheshire]
            [midje.sweet :refer :all]
            [pigeon-backend.handler :refer :all]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [pigeon-backend.test-util :refer :all]
            [pigeon-backend.services.user-service :as user-service]
            [buddy.sign.jws :as jws]
            [clj-time.core :as t]
            [environ.core :refer [env]]
            [clojure.test :refer [deftest]]
            [pigeon-backend.migrations_test :refer [drop-all-tables]]
            [pigeon-backend.db.config :refer [db-spec]]
            [pigeon-backend.db.migrations :as migrations]
            [midje.sweet :refer :all]
            [pigeon-backend.services.participant-service :as service]
            [schema.core :as s]
            [buddy.hashers :as hashers]
            [pigeon-backend.dao.participant-dao :as participant-dao]
            [schema-generators.generators :as g]
            [schema-generators.complete :as c]
            [pigeon-backend.services.room-service :as room-service]
            [pigeon-backend.dao.room-dao-test :as room-dao-test]
            [pigeon-backend.dao.participant-dao-test :as participant-dao-test]
            [pigeon-backend.util :as util]
            [pigeon-backend.dao.user-dao-test :as user-dao-test]))

;; todo: get rid of mocking service tests and do proper integration tests instead

(deftest participant-test
  (comment (facts "User should be able to add himself to room"
             (with-state-changes [(before :facts (empty-and-create-tables))]
               (fact
                 (let [input (g/generate service/AddParticipant)
                       output (c/complete input service/Model)
                       expected output]
                   (with-redefs [participant-dao/create! (fn [_ _] output)]
                     (service/add-participant! input) => expected))))))
  (comment (facts "User should be able to list all participants in a room"
             (with-state-changes [(before :facts (empty-and-create-tables))]
               (fact
                 (let [room-id (g/generate String)
                       output (c/complete [{:room_id room-id}] participant-dao/QueryResult)
                       expected output]
                   (with-redefs [participant-dao/get-by (fn [_ _] output)
                                 service/authorize (fn [_ _] nil)]
                     (service/get-by-room room-id (create-test-login-token)) => expected))))))
  (facts "Simple authorization"
    (with-state-changes [(before :facts (empty-and-create-tables))]
      (fact "Doesn't authorize"
        (with-redefs [participant-dao/get-auth (fn [_ _] false)]
          (service/authorize anything (create-test-login-token)) => (throws Exception)))
      (fact "Authorizes"
        (with-redefs [participant-dao/get-auth (fn [_ _] true)]
          (service/authorize anything (create-test-login-token)) => nil))))
  (facts "Authorization against room & participant"
    (with-state-changes [(before :facts (empty-and-create-tables))]
      (fact "Does not authorize"
        (let [_ (user-dao-test/user)
              {room-id :id} (room-dao-test/room)
              {other-room-id :id} (room-dao-test/room {:name "Pigeon room 2"})
              {participant-id :id} (participant-dao-test/participant {:room_id  room-id
                                                                      :name     test-user
                                                                      :username test-user})]
          (service/authorize-by-participant other-room-id participant-id (create-test-login-token)) => (throws Exception)))
      (fact "Authorizes"
        (let [_ (user-dao-test/user)
              {room-id :id} (room-dao-test/room)
              {participant-id :id} (participant-dao-test/participant {:room_id  room-id
                                                                      :name     test-user
                                                                      :username test-user})]
          (service/authorize-by-participant room-id participant-id (create-test-login-token)) => nil)))))