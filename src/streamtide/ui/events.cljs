(ns streamtide.ui.events
  "Streamtide common events"
  (:require [district.ui.graphql.events :as gql-events]
            [district.ui.logging.events :as logging]
            [district.ui.web3-accounts.queries :as account-queries]
            [district.ui.web3.queries :as web3-queries]
            [re-frame.core :as re-frame]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-fx
  ::day-night-switch
  ; Enable or disable the dark mode
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} _]
    (let [day-or-night (not (:day-night-switch db))]
      {:store (assoc store :day-night-switch day-or-night)
       :db (assoc db :day-night-switch day-or-night)})))

(re-frame/reg-event-fx
  :user/sign-in
  ; To log-in, the user needs to sign a message with its wallet and send it to the server.
  ; The server will produce a JWT which is stored in the browser to be sent on each request.
  (fn [{:keys [db]} _]
    (let [active-account (account-queries/active-account db)
          ;; TODO for extra security, the data to sign should include an expiration timestamp or one-time-token
          data-str " Sign in to Streamtide! "]
      {:web3/personal-sign
       {:web3 (web3-queries/web3 db)
        :data-str data-str
        :from active-account
        :on-success [:user/-authenticate {:data-str data-str}]
        :on-error [::logging/error " Error Signing with Active Ethereum Account. "]}})))

(re-frame/reg-event-fx
  :user/-authenticate
  ; Having the data signed with the user's wallet, it sends a GraphQL mutation request with the data signed
  ; so the server can validate it and produce a JWT
  (fn [_ [_ {:keys [data-str]} data-signature]]
    (let [query
          {:queries [[:sign-in {:data-signature :$dataSignature
                                :data           :$data}
                      [:jwt
                       :user/address]]]
           :variables [{:variable/name :$dataSignature
                        :variable/type :String!}
                       {:variable/name :$data
                        :variable/type :String!}]}]
      {:dispatch
       [::gql-events/mutation
        {:query query
         :variables {:dataSignature data-signature
                     :data data-str}
         :on-success [:authentication-success]
         :on-error [:authentication-error]}]})))

(re-frame/reg-event-fx
  :authentication-success
  ; when the authentication success, the JWT produced by the server is stored in the browser. Additionally,
  ; the authorization token for GraphQL is set, it will be used on subsequents GraphQL requests
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [_ {session-info :sign-in}]]
    {:dispatch-n [[::logging/info "Authentication succeeded" {:user (:user/address session-info)}]
                  [::gql-events/set-authorization-token (:jwt session-info)]]
     :db (assoc db :active-session session-info)
     :store (assoc store :active-session session-info)}))

(re-frame/reg-event-fx
  :authentication-error
  (fn [_ [_ error]]
    ;; TODO show error to the user
    {:dispatch [::logging/error "Failed to authenticate" {:error error}]}))

(re-frame/reg-event-fx
  :user/sign-out
  ; Sing out just deletes the JWT from the browser, and clean up the token for GraphQL.
  ; There is no enforcement in the server side.
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]}]
    {:db (dissoc db :active-session)
     :store (dissoc store :active-session)
     :dispatch [::gql-events/set-authorization-token nil]}))

(re-frame/reg-event-fx
  ::add-to-cart
  ; Add the address of a user to the cart for making donations
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [_ {:keys [:user/address] :as data}]]
    {:db (assoc-in db [:cart address] true)
     :store (assoc-in store [:cart address] true)}))

(re-frame/reg-event-fx
  ::remove-from-cart
  ; Removes the address of a user from the cart for making donations
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [_ {:keys [:user/address] :as data}]]
    {:db (update db :cart dissoc address)
     :store (update store :cart dissoc address)}))

(re-frame/reg-event-fx
  ::clean-cart
  ; Removes all the addresses from the cart for making donations
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [_]]
    {:db (dissoc db :cart)
     :store (dissoc store :cart)}))


