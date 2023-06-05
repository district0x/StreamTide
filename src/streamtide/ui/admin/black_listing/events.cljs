(ns streamtide.ui.admin.black-listing.events
  (:require
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::blacklist
  ; Sends a transaction to blacklist or whitelist a user
  (fn [{:keys [db]} [_ {:keys [:user/address :user/blacklisted? :send-tx/id] :as data}]]
    (let [tx-name (str (if blacklisted? "Blacklisting" "Whitelisting") " address " address)
          active-account (account-queries/active-account db)]
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn (if blacklisted? :add-blacklisted :remove-blacklisted)
                                       :args [address]
                                       :tx-opts {:from active-account}
                                       :tx-id {:streamtide/blacklist id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/black-listing}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::blacklist]
                                                         [::notification-events/show (str "You successfully " (if blacklisted? "blacklisted" "whitelisted") " address " address)]]
                                       :on-tx-error [::logging/error (str tx-name " tx error")
                                                     {:user {:id active-account}
                                                      :address address}
                                                     ::blacklist]}]})))

(re-frame/reg-event-fx
  ::blacklist-success
  (fn [{:keys [db]} [_ result]]
    ;; TODO Show message to user
    (let [address (-> result :blacklist :user/address)]
      {:db (-> db
               (update :blacklisting dissoc address))})))

(re-frame/reg-event-fx
  ::blacklist-error
  (fn [{:keys [db]} [_ {:keys [:user/address]} error]]
    {:db (update db :blacklisting dissoc address)
     :dispatch [::logging/error
                "Failed to save blacklist"
                ;; TODO proper error handling
                {:user/address address
                 :error (map :message error)} ::save-settings]}))
