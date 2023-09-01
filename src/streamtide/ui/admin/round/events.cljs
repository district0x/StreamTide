(ns streamtide.ui.admin.round.events
  (:require
    [cljs-web3-next.core :as web3]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [re-frame.core :as re-frame]
    [streamtide.ui.events :refer [wallet-chain-interceptors]]
    [streamtide.ui.utils :refer [build-tx-opts]]
    [taoensso.timbre :as log]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-fx
  ::set-multiplier
  ; Sets the multiplier factor for a receiver
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [{:keys [:id :factor]}]]
    {:store (assoc-in store [:multipliers id] factor)
     :db (assoc-in db [:multipliers id] factor)}))

(re-frame/reg-event-fx
  ::enable-donation
  ; Enables or disables a donation for a matching round
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [{:keys [:id :enabled?]}]]
    {:store (assoc-in store [:donations id] enabled?)
     :db (assoc-in db [:donations id] enabled?)}))


(re-frame/reg-event-fx
  ::distribute
  ; TX to distribute matching pool for last round
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :round :matchings] :as data}]]
    (let [tx-name (str "Distribute matching pool for round " round)
          active-account (account-queries/active-account db)
          matchings (filter #(> (last %) 0) matchings)
          [receivers amounts] ((juxt #(map key %) #(map val %)) matchings)
          amounts (map str amounts)]
      (log/debug "matchings" matchings)
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn :distribute
                                       :args [receivers amounts]
                                       :tx-opts (build-tx-opts {:from active-account})
                                       :tx-id {:streamtide/distribute id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::distribute]
                                                        [::notification-events/show (str "Matching pool successfully distributed for round " round)]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :matchings matchings}
                                                     ::distribute]}]})))

(re-frame/reg-event-fx
  ::fill-matching-pool
  ; TX to fill up matching pool
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :amount :round] :as data}]]
    (let [tx-name (str "Filling up matching pool with " amount " ETH")
          active-account (account-queries/active-account db)
          amount-wei (-> amount str (web3/to-wei :ether))]
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :args []
                                       :tx-opts (build-tx-opts {:from active-account :value amount-wei})
                                       :tx-id {:streamtide/fill-matching-pool id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::fill-matching-pool]
                                                        [::notification-events/show (str "Matching pool successfully filled with " amount " ETH")]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :round round
                                                      :amount amount}
                                                     ::fill-matching-pool]}]})))

(re-frame/reg-event-fx
  ::close-round
  ; TX to close an ongoing round
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :round] :as data}]]
    (let [tx-name "Close round"
          active-account (account-queries/active-account db)]
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn :close-round
                                       :args []
                                       :tx-opts (build-tx-opts {:from active-account})
                                       :tx-id {:streamtide/close-round id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::close-round]
                                                         [::notification-events/show "Round successfully closed"]
                                                         [::round-closed]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :round round}
                                                     ::close-round]}]})))

(re-frame/reg-event-fx
  ::round-closed
  ; Event triggered when a round is closed. This is an empty event just aimed for subscription
  (constantly nil))
