(ns streamtide.ui.admin.grant-approval-feed.events
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::review-grant
  ; Send a GraphQL mutation request to change the status of a pending grant to either approve or rejected
  ; TODO we should rather send a TX so the grant is approved in the smart contract side
  (fn [{:keys [db]} [_ {:keys [:user/address :grant/status] :as data}]]
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
                   :on-error [::review-grant-error {:user/address address}]}]})))


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
