(ns streamtide.ui.send-support.events
  (:require
    [bignumber.core :as bn]
    [cljsjs.bignumber]
    [cljs-web3-next.core :as web3]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [re-frame.core :as re-frame]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.ui.events :as st-events]))

(re-frame/reg-event-fx
  ::send-support
  ; Make transaction to send donations to patrons
  (fn [{:keys [db]} [_ {:keys [:donations :send-tx/id] :as data}]]
    (let [tx-name (str "Sending donations to patrons")
          active-account (account-queries/active-account db)
          [receivers amounts] (apply map vector donations)
          amounts (map #(web3/to-wei (shared-utils/safe-number-str (:amount %)) :ether) amounts)
          total-amount-wei (->> amounts
                                (map js/BigNumber.)
                                (reduce bn/+)
                                bn/fixed)]
      {:dispatch (if (some #{"0"} amounts)
                   [::logging/error (str "amount cannot be zero")
                    {:user {:id active-account}
                     :donations donations}
                    ::send-support]
                   [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                         :fn :donate
                                         :args [receivers amounts]
                                         :tx-opts {:from active-account :value total-amount-wei}
                                         :tx-id {:streamtide/donate id}
                                         :tx-log {:name tx-name
                                                  :related-href {:name :route.send-support/index}}
                                         :on-tx-success-n [[::logging/info (str tx-name " tx success") ::send-support]
                                                           [::notification-events/show "You successfully make donations "]
                                                           [::send-support-success]]
                                         :on-tx-error [::logging/error (str tx-name " tx error")
                                                       {:user {:id active-account}
                                                        :donations donations}
                                                       ::send-support]}])})))

(re-frame/reg-event-fx
  ::send-support-success
  (fn [{:keys [db]} [_]]
    {:dispatch [::st-events/clean-cart]}))
