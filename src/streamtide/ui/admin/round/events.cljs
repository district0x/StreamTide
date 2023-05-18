(ns streamtide.ui.admin.round.events
  (:require
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [re-frame.core :as re-frame]
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
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :round :matchings] :as data}]]
    (let [tx-name (str "Distribute matching pool for round " round)
          active-account (account-queries/active-account db)
          matchings (filter #(> (last %) 0) matchings)
          [receivers amounts] (apply map vector matchings)
          amounts (map str amounts)]
      (log/debug "matchings" matchings)
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn :distribute
                                       :args [receivers amounts]
                                       :tx-opts {:from active-account}
                                       :tx-id {:streamtide/distribute id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::distribute]
                                                        [::notification-events/show (str "You successfully distribute matching pool for round " round)]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :matchings matchings}
                                                     ::distribute]}]})))