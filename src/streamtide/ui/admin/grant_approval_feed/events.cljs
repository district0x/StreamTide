(ns streamtide.ui.admin.grant-approval-feed.events
  (:require
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.graphql-utils :as gql-utils]
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [re-frame.core :as re-frame]
    [streamtide.ui.events :refer [wallet-chain-interceptors]]
    [streamtide.ui.utils :refer [build-tx-opts]]))

(re-frame/reg-event-fx
  ::review-grant
  ; If the grant is approved, this sends a TX to include the user as a patron.
  ; If the grant is rejected, this send a GraphQL mutation request to change the status of a pending grant to rejected
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:user/addresses :grant/status :send-tx/id] :as data}]]
    (if (= status :grant.status/approved)
      (let [tx-name (str "Approving grant for " (count addresses) " users")
            active-account (account-queries/active-account db)]
        {:db (assoc-in db [:reviewing-grants?] true)
         :dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                         :fn :add-patrons
                                         :args [addresses]
                                         :tx-opts (build-tx-opts {:from active-account})
                                         :tx-id {:streamtide/add-patron id}
                                         :tx-log {:name tx-name
                                                  :related-href {:name :route.admin/grant-approval-feed}}
                                         :on-tx-success-n [[::logging/info (str tx-name " tx success") ::review-grant]
                                                           [::review-grant-success data]]
                                         :on-tx-error-n [[::logging/error (str tx-name " tx error")
                                                          {:user {:id active-account}
                                                           :patron addresses}
                                                          ::review-grant]
                                                         [::review-grant-error data]]
                                         :on-tx-hash-error [::review-grant-error data]}]})

      (let [query
            {:queries [[:review-grants
                        {:user/addresses :$addresses
                         :grant/status :$status}]]
             :variables [{:variable/name :$addresses
                          :variable/type (keyword "[ID!]!")}
                         {:variable/name :$status
                          :variable/type :GrantStatus!}]}]
        {:db (assoc-in db [:reviewing-grants?] true)
         :dispatch [::gql-events/mutation
                    {:query query
                     :variables {:addresses addresses
                                 :status (gql-utils/kw->gql-name status)}
                     :on-success [::review-grant-success data]
                     :on-error [::review-grant-error {:user/addresses addresses}]}]}))))


(re-frame/reg-event-fx
  ::review-grant-success
  (fn [{:keys [db]} [_ args]]
    (let [{:keys [:user/addresses :grant/status]} args]
      {:db (-> db
               (dissoc :reviewing-grants?)
               (update :reviewed-grant? merge (reduce (fn [col address] (conj col {address status})) {} addresses)))
       :dispatch [::notification-events/show (str "Grants successfully " (if (= :grant.status/approved status) "approved" "rejected")  )]})))

(re-frame/reg-event-fx
  ::review-grant-error
  (fn [{:keys [db]} [_ {:keys [:user/addresses]} error]]
    {:db (dissoc db :reviewing-grants?)
     :dispatch-n [[::notification-events/show "[ERROR] An error occurs while reviewing grants"]
                  [::logging/error
                   (str "Failed to modify grant status for users " addresses)
                   {:error (map :message error)} ::review-grant]]}))
