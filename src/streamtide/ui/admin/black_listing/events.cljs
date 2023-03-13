(ns streamtide.ui.admin.black-listing.events
  (:require
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::blacklist
  ; Sends a GraphQL mutation requesto to blacklist or whitelist a user
  ; TODO shall we rather blacklist in the smart contract side?
  (fn [{:keys [db]} [_ {:keys [:user/address :user/blacklisted] :as data}]]
    (let [query
          {:queries [[:blacklist
                      {:user/address :$address
                       :blacklist :$blacklist}
                      [:user/address
                       :user/blacklisted]]]
           :variables [{:variable/name :$address
                        :variable/type :ID!}
                       {:variable/name :$blacklist
                        :variable/type :Boolean!}
                       ]}]
      {:db (assoc-in db [:blacklisting address] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:address address :blacklist blacklisted}
                   :on-success [::blacklist-success]
                   :on-error [::blacklist-error {:user/address address}]}]})))

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
