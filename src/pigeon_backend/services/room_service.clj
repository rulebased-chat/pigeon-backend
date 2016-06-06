(ns pigeon-backend.services.room-service
  (:require [pigeon-backend.dao.room-dao :as room-dao]
            [clojure.java.jdbc :as jdbc]
            [pigeon-backend.db.config :refer [db-spec]]
            [buddy.hashers :as hashers]
            [schema.core :as s]
            [pigeon-backend.dao.model :as model]
            [schema-tools.core :as st]
            [pigeon-backend.dao.room-dao :refer [New Model Existing]]))

(s/defn room-create! [data :- New] {:post [(s/validate Model %)]}
  (jdbc/with-db-transaction [tx db-spec]
    (room-dao/create! tx data)))

(s/defn room-update! [room :- Existing]
  {:post [(s/validate Model %)]}
  (jdbc/with-db-transaction [tx db-spec]
    (room-dao/update! tx room)))