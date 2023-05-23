(ns streamtide.ui.admin.rounds.events
  (:require
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::start-round
  ; TX to start a new round
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :duration] :as data}]]
    (let [tx-name (str "Starting a new round with duration " duration " seconds")
          active-account (account-queries/active-account db)]
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn :start-round
                                       :args [duration]
                                       :tx-opts {:from active-account}
                                       :tx-id {:streamtide/start-round id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/rounds}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::start-round]
                                                        [::notification-events/show (str "You successfully started a new round with duration " duration)]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :duration duration}
                                                     ::start-round]}]})))
