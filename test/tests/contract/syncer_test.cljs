(ns tests.contract.syncer-test
  (:require [cljs-web3-next.eth :as web3-eth]
            [cljs-web3-next.evm :as web3-evm]
            [cljs-web3-next.helpers :refer [zero-address]]
            [cljs.core.async :refer [<! go timeout]]
            [cljs.test :refer-macros [deftest is testing async]]
            [district.server.db :as district.server.db]
            [district.server.logging]
            [district.server.smart-contracts :as smart-contracts]
            [district.server.web3 :refer [web3]]
            [district.server.web3-events]
            [mount.core :as mount]
            [streamtide.server.constants :as constants]
            [streamtide.server.db :as db]
            [streamtide.server.syncer]
            [streamtide.shared.smart-contracts-dev :as smart-contracts-dev]))

(def waited-events-counter (atom {}))

(def chain-id 1337)

(defn wait-event [event]
  (let [current-counter (swap! waited-events-counter update event inc)
        current-counter (get current-counter event)]
    (go
      (while (< (or (:event/count (db/get-last-event "streamtide" (name event) chain-id)) 0) current-counter)
        (<! (timeout 250))))))


(deftest syncer-test
  (testing "Testing syncer"
    (async done
      (-> (mount/with-args {:web3 {:url "ws://127.0.0.1:9545"}
                            :smart-contracts {:contracts-var #'smart-contracts-dev/smart-contracts
                                              :contracts-build-path "./resources/public/contracts/build"}
                            :db {:path ":memory:"}
                            :web3-events {:events constants/web3-events
                                          :from-block "latest"
                                          :checkpoint-file nil
                                          :block-step 1000
                                          :fetch-parallel false}
                            :logging {:level :debug
                                      :console? true}
                            })
          (mount/only [#'district.server.logging/logging
                       #'district.server.db/db
                       #'district.server.smart-contracts/smart-contracts
                       #'district.server.web3-events/web3-events
                       #'district.server.web3/web3
                       #'streamtide.server.db/streamtide-db
                       #'streamtide.server.syncer/syncer])
          (mount/start))

      (web3-evm/snapshot!
        @web3
        (fn [_ snapshot-id]
          (go
          (let [[owner admin user user2] (<! (web3-eth/accounts @web3))]
            (<! (smart-contracts/contract-send :streamtide-fwd :add-admin [admin] {:from owner}))
            (<! (wait-event :admin-added))
            (is (= (:role/role (first (db/get-roles admin))) "admin"))

            (<! (smart-contracts/contract-send :streamtide-fwd :remove-admin [admin] {:from owner}))
            (<! (wait-event :admin-removed))
            (is (not= (:role/role (first (db/get-roles admin))) "admin"))

            (<! (smart-contracts/contract-send :streamtide-fwd :add-admin [admin] {:from owner}))
            (<! (wait-event :admin-added))
            (is (= (:role/role (first (db/get-roles admin))) "admin"))

            (<! (smart-contracts/contract-send :streamtide-fwd :add-blacklisted [user2] {:from admin}))
            (<! (wait-event :blacklisted-added))
            (is (db/blacklisted? {:user/address user2}))

            (<! (smart-contracts/contract-send :streamtide-fwd :remove-blacklisted [user2] {:from admin}))
            (<! (wait-event :blacklisted-removed))
            (is (not (db/blacklisted? {:user/address user2})))

            (is (not= (:grant/status (db/get-grant user)) (name :grant.status/approved)))
            (<! (smart-contracts/contract-send :streamtide-fwd :add-patrons [[user]] {:from admin}))
            (<! (wait-event :patrons-added))
            (is (= (:grant/status (db/get-grant user)) (name :grant.status/approved)))

            (<! (smart-contracts/contract-send :streamtide-fwd :close-round [] {:from admin}))
            (<! (wait-event :round-closed))

            (<! (smart-contracts/contract-send :streamtide-fwd :start-round [1000] {:from admin :value "1000"}))
            (<! (wait-event :round-started))
            (<! (wait-event :matching-pool-donation))

            (let [rounds (:items (db/get-rounds {:first 6}))
                  round (first rounds)
                  round-id (:round/id round)
                  matching-pool (db/get-matching-pool round-id zero-address chain-id)]
              (is (= (count rounds) 1))
              (is (> round-id 0))
              (is (= (:matching-pool/amount matching-pool) "1000"))
              (is (or (= (:matching-pool/distributed matching-pool) "0")
                      (= (:matching-pool/distributed matching-pool) nil)))
              (is (> (:round/start round) 0))

              (<! (web3-eth/send-transaction! @web3 {:from admin :to (smart-contracts/contract-address :streamtide-fwd) :value 1000}))
              (<! (wait-event :matching-pool-donation))
              (let [rounds (:items (db/get-rounds {:first 6}))
                    round (first rounds)
                    matching-pool (db/get-matching-pool round-id zero-address chain-id)]
                (is (= (count rounds) 1))
                (is (= round-id (:round/id round)))
                (is (= (:matching-pool/amount matching-pool) "2000"))))

            (<! (web3-eth/send-transaction! @web3 {:from admin :to (smart-contracts/contract-address :streamtide-fwd) :value 1500}))
            (<! (wait-event :matching-pool-donation))
            (is (= (-> (db/get-rounds {:first 6})
                       :items
                       first
                       :round/id
                       (db/get-matching-pool zero-address chain-id)
                       :matching-pool/amount)
                   "3500"))

            (db/upsert-user-info! {:user/address user
                                   :user/min-donation "300"})

            (<! (smart-contracts/contract-send :streamtide-fwd :donate [[user]["250"]] {:from user2 :value "250"}))
            (<! (wait-event :donate))
            (let [donations (:items (db/get-donations {:sender user2 :receiver user :first 6}))
                  donation (first donations)
                  round-id (:round/id (first (:items (db/get-rounds {:first 1}))))
                  permissions (db/get-user-content-permissions {:user/source-user user2 :user/target-user user})]
              (is (= (count permissions) 0))
              (is (= (count donations) 1))
              (is (= (:donation/sender donation) user2))
              (is (= (:donation/receiver donation) user))
              (is (= (str (:donation/amount donation)) "250"))
              (is (> (:donation/date donation) 0))
              (is (= (:donation/coin donation) zero-address))
              (is (= (:round/id donation) round-id)))

            (web3-evm/increase-time!
              @web3
              [1001]
              (fn [_ _]
                (go
                  (<! (smart-contracts/contract-send :streamtide-fwd :donate [[user]["500"]] {:from user2 :value "500"}))
                  (<! (wait-event :donate))
                  (let [donations (:items (db/get-donations {:sender user2 :receiver user :first 6}))
                        donation (last donations)
                        permissions (db/get-user-content-permissions {:user/source-user user2 :user/target-user user})]
                    (is (= (count permissions) 1))
                    (is (= (count donations) 2))
                    (is (= (:donation/sender donation) user2))
                    (is (= (:donation/receiver donation) user))
                    (is (= (str (:donation/amount donation)) "500"))
                    (is (> (:donation/date donation) 0))
                    (is (= (:donation/coin donation) zero-address))
                    (is (nil? (:round/id donation))))

                  (<! (smart-contracts/contract-send :streamtide-fwd :distribute [[user] ["1000"] zero-address] {:from admin}))
                  (<! (wait-event :distribute))

                  (let [matchings (:items (db/get-matchings {:sender user2 :receiver user :first 6}))
                        matching (last matchings)
                        round (first (:items (db/get-rounds {:first 1})))
                        round-id (:round/id round)
                        matching-pool (db/get-matching-pool round-id zero-address chain-id)]
                    (is (= (count matchings) 1))
                    (is (= (:matching/receiver matching) user))
                    (is (= (str (:matching/amount matching)) "1000"))
                    (is (> (:matching/date matching) 0))
                    (is (= (:matching/coin matching) zero-address))
                    (is (= (:round/id matching) round-id))
                    (is (= (:matching-pool/distributed matching-pool) "1000")))

                  (<! (smart-contracts/contract-send :streamtide-fwd :start-round [1000] {:from admin :value "1000"}))
                  (<! (wait-event :round-started))

                  (<! (smart-contracts/contract-send :streamtide-fwd :close-round [] {:from admin}))
                  (<! (wait-event :round-closed))

                  (let [round (first (:items (db/get-rounds {:first 1 :order-by :rounds.order-by/id :order-dir :desc})))]
                    (is (< (int (:round/duration round)) 1000)))

                  (web3-evm/revert!
                    @web3
                    [snapshot-id]
                    (fn [_ _]
                      (done)))))))))))))
