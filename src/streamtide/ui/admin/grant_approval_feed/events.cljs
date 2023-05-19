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
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::review-grant
  ; If the grant is approved, this sends a TX to include the user as a patron.
  ; If the grant is rejected, this send a GraphQL mutation request to change the status of a pending grant to rejected
  (fn [{:keys [db]} [_ {:keys [:user/address :grant/status :send-tx/id] :as data}]]
    (if (= status :grant.status/approved)
      (let [tx-name (str "Approving grant for user: " address)
            active-account (account-queries/active-account db)]
        {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                         :fn :add-patron
                                         :args [address]
                                         :tx-opts {:from active-account}
                                         :tx-id {:streamtide/add-patron id}
                                         :tx-log {:name tx-name
                                                  :related-href {:name :route.admin/grant-approval-feed}}
                                         :on-tx-success-n [[::logging/info (str tx-name " tx success") ::review-grant]
                                                           [::notification-events/show (str "You successfully approve grant for user: " address)]]
                                         :on-tx-error [::logging/error (str tx-name " tx error")
                                                       {:user {:id active-account}
                                                        :patron address}
                                                       ::review-grant]}]})

      (let [query
            {:queries [[:reviewGrant
                        {:user/address :$address
                         :grant/status :$status}
                        [:grant/status
                         [:grant/user [:user/address]]]]]
             :variables [{:variable/name :$address
                          :variable/type :ID!}
                         {:variable/name :$status
                          :variable/type :GrantStatus!}]}]
        {:db (assoc-in db [:reviewing-grant address] true)
         :dispatch [::gql-events/mutation
                    {:query query
                     :variables {:address address
                                 :status (gql-utils/kw->gql-name status)}
                     :on-success [::review-grant-success]
                     :on-error [::review-grant-error {:user/address address}]}]}))))


(re-frame/reg-event-fx
  ::review-grant-success
  (fn [{:keys [db]} [_ result]]
    ;; TODO Show message to user
    (js/console.log "GRANT REVIEWED")
    (let [address (-> result :review-grant :grant/user :user/address)
          status (gql-utils/gql-name->kw (-> result :review-grant :grant/status))]
      {:db (-> db
               (update :reviewing-grant dissoc address)
               (assoc-in [:reviewed-grant address] status))})))

(re-frame/reg-event-fx
  ::review-grant-error
  (fn [{:keys [db]} [_ {:keys [:user/address]} error]]
    {:db (update db :reviewing-grant dissoc address)
     :dispatch [::logging/error
                (str "Failed to modify grant status for user " address)
                ;; TODO proper error handling
                {:error (map :message error)} ::review-grant]}))
