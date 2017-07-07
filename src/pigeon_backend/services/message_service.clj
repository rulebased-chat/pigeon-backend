(ns pigeon-backend.services.message-service
  (:require [clojure.java.jdbc :as jdbc]
            [pigeon-backend.db.config :refer [db-spec]]
            [schema.core :as s]
            [pigeon-backend.services.model :as model]
            [schema-tools.core :as st]
            [pigeon-backend.services.util :refer [initialize-query-data]]
            [jeesql.core :refer [defqueries]]))

(s/defschema New {:sender String
                  :recipient String
                  :message String})
(s/defschema Model (merge model/Model New {:actual_recipient String
                                           :turn s/Int}))
(s/defschema Get {:sender String
                  :recipient String})

(s/defschema Rule {:ref String
                   :value s/Any
                   :realized_value s/Any
                   :from_node (s/maybe String)
                   :to_node String
                   :type String ;; todo: as enum
                   :short_circuit_rule_chain_if_true Boolean
                   :short_circuit_rule_chain_if_false Boolean
                   :order_no s/Int
                   :if_satisfied_then_direct_to_nodes [String]
                   :if_satisfied_then_duplicate_to_nodes [String]
                   :if_satisfied_then_duplicate_from_nodes [String]})

(defqueries "sql/message.sql")
(defqueries "sql/send_limit.sql")
(defqueries "sql/turn.sql")
(defqueries "sql/rule.sql")

(s/defn determine-applicable-rules [data :- [Rule]]
  {:post [(s/validate [Rule] %)]}

  (let [realized-result
        (map #(case (:type %)
               "rule_if_six_sided_dice_fuzzy"
               (assoc % :realized_value (rand-nth [0.167
                                                   0.333
                                                   0.5
                                                   0.667
                                                   0.833
                                                   1]))) data)

        take-count
        (reduce (fn [a {:keys [realized_value
                               value
                               type
                               short_circuit_rule_chain_if_true
                               short_circuit_rule_chain_if_false]}]
                  (case type
                    "rule_if_six_sided_dice_fuzzy"
                    (if (<= realized_value value)
                      (if (not short_circuit_rule_chain_if_true)  (inc a) a)
                      (if (not short_circuit_rule_chain_if_false) (inc a) a))))
          0 realized-result)

        take-last-element?
        (let [[{:keys [type realized_value value]} & _] (drop take-count realized-result)]
          (case type
                "rule_if_six_sided_dice_fuzzy" (<= realized_value value)))

        applicable-rules
        (filter (fn [{:keys [type
                             realized_value
                             value]}]
                  (case type
                        "rule_if_six_sided_dice_fuzzy"
                        (<= realized_value value)))
          (take (if take-last-element?
            (inc take-count)
            take-count) realized-result))]

    applicable-rules))

(s/defn message-create! [{:keys [sender recipient] :as data} :- New]
  ;;{:post [(s/validate Model %)]}
  (jdbc/with-db-transaction [tx db-spec]
    (let [active-turn (->> (sql-turn-get tx)
                           (filter #(true? (:active %)))
                           first)
          ;; todo: some duplicate code below...
          send-limit-rules (->> (sql-get-send-limit tx)
                                (filter #(and (= (:from_node %) sender)
                                          (some #{recipient} (:to_nodes %))
                                          (= (:type %) "send_limit"))))
          send-limit-rules (doall (map (fn [{:keys [from_node to_nodes] :as value}]
                                         (assoc value :messages
                                                      (sql-conversations tx
                                                        {:sender from_node
                                                         :recipient to_nodes
                                                         :turn (:id active-turn)})))
                                       send-limit-rules))
          all-rule-limits-exceeded? (every? true?
                                            (for [entry send-limit-rules
                                                  :let [messages-counted-by-recipient
                                                          (into {}
                                                            (for [[k v] (group-by :recipient (:messages entry))]
                                                              {k (count v)}))]]
                                              (>= (get messages-counted-by-recipient recipient 0) (:value entry))))
          shared-send-limit-rules (->> (sql-get-send-limit tx)
                                       (filter #(and (= (:from_node %) sender)
                                                 (some #{recipient} (:to_nodes %))
                                                 (= (:type %) "shared_send_limit"))))
          shared-send-limit-rules (doall (map (fn [{:keys [from_node to_nodes] :as value}]
                                                (assoc value :messages
                                                             (sql-conversations tx
                                                               {:sender from_node
                                                                :recipient to_nodes
                                                                :turn (:id active-turn)})))
                                              shared-send-limit-rules))
          all-shared-rule-limits-exceeded? (every? true?
                                                   (for [entry shared-send-limit-rules]
                                                     (>= (count (:messages entry)) (:value entry))))
          rules (sql-get-rule tx {:recipient recipient})]

      (when (and all-rule-limits-exceeded?
                 all-shared-rule-limits-exceeded?)
        (throw (ex-info "Message quota exceeded" data)))

      (sql-message-attempt-create<! tx data)

        ;; todo: log applicable-rules

      (if (not-empty rules)
        (doseq [{:keys [if_satisfied_then_direct_to_nodes
                        if_satisfied_then_duplicate_to_nodes
                        if_satisfied_then_duplicate_from_nodes]} (determine-applicable-rules rules)]
          (doseq [to_node if_satisfied_then_direct_to_nodes]
            (sql-message-create<! tx (assoc data :actual_recipient to_node)))
          (doseq [duplicate-sender if_satisfied_then_duplicate_from_nodes
                  duplicate-recipient if_satisfied_then_duplicate_to_nodes]
            (sql-message-create<! tx (assoc data :sender duplicate-sender
                                                 :recipient duplicate-recipient
                                                 :actual_recipient duplicate-recipient))))
        (sql-message-create<! tx (assoc data :actual_recipient recipient))))))

(s/defn message-get [data :- Get] {:post [(s/validate [(assoc Model :is_from_sender Boolean
                                                                    :turn_name String
                                                                    :sender_name String)] %)]}
  (jdbc/with-db-transaction [tx db-spec]
    (sql-message-get tx data)))