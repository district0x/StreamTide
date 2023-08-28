(ns streamtide.ui.admin.rounds.events
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
    [streamtide.shared.utils :as shared-utils]))

(re-frame/reg-event-fx
  ::start-round
  ; TX to start a new round
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :duration :matching-pool] :as data}]]
    (let [tx-name (str "Starting a new round with duration " duration " seconds")
          active-account (account-queries/active-account db)
          matching-pool-wei (if matching-pool (web3/to-wei (shared-utils/safe-number-str matching-pool) :ether) 0)]
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn :start-round
                                       :args [duration]
                                       :tx-opts (build-tx-opts {:from active-account :value matching-pool-wei})
                                       :tx-id {:streamtide/start-round id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/rounds}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::start-round]
                                                        [::notification-events/show (str "New round successfully started")]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :duration duration}
                                                     ::start-round]}]})))
