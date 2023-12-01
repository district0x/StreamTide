(ns streamtide.ui.events
  "Streamtide common events"
  (:require [district.ui.graphql.events :as gql-events]
            [district.ui.logging.events :as logging]
            [district.ui.notification.events :as notification-events]
            [district.ui.web3-accounts.queries :as account-queries]
            [district.ui.web3.queries :as web3-queries]
            [district.ui.web3.events :as web3-events]
            [district.ui.web3-chain.queries :as chain-queries]
            [district.ui.web3-chain.events :as chain-events]
            [goog.string :as gstring]
            [re-frame.core :as re-frame]
            [streamtide.ui.components.error-notification :as error-notification]
            [streamtide.shared.utils :as shared-utils]
            [streamtide.ui.config :refer [config-map]]))

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
  ::menu-mobile-switch
  ; Open or close the mobile menu
  [interceptors]
  (fn [{:keys [db]} _]
    (let [menu-open? (not (:menu-mobile-open? db))]
      {:db (assoc db :menu-mobile-open? menu-open?)})))

(re-frame/reg-event-fx
  ::web3-created
  [interceptors]
  (fn [_]
    {:dispatch [::dispatch-pending-event]}))

(re-frame/reg-event-fx
  ::web3-creation-failed
  [interceptors]
  (fn [{:keys [db]} [_ error]]
    {:dispatch-n
     [[::notification-events/show "Cannot connect Wallet"]
      [::logging/error " Cannot connect Wallet" {:error error}]]}))


(re-frame/reg-event-fx
  ::web3-chain-changed
  [interceptors]
  (fn [{:keys [db]} [[_ {:keys [:new]}]]]
    (when (= new (-> config-map :web3-chain :chain-id))
      {:dispatch [::dispatch-pending-event]})))


(def last-wallet-event (atom nil))

(re-frame/reg-event-fx
  ::dispatch-pending-event
  [interceptors]
  (fn [_]
    (let [{:keys [:event :timestamp]} @last-wallet-event]
      (when event
        (reset! last-wallet-event nil)
        (when (> (+ 30 timestamp) (shared-utils/now-secs))
          {:dispatch event})))))

(def connect-wallet
  ;; interceptor to make sure wallet is unlocked and account is connected to proceed.
  ;; If not connected, it will request the user to unlock wallet and/or connect one of its account.
  ;; Additionally, it stores the current event, so it resumes it when wallet is connected.
  (re-frame.core/->interceptor
    :id :connect-wallet
    :before (fn [context]
              (let [db (-> context :coeffects :db)]
                (if (and (web3-queries/web3 db)
                         (account-queries/has-active-account? db))
                  ; if wallet connected, we just follow the normal flow
                  context
                  (do
                    ; stores the interrupted event to resume it later
                    (reset! last-wallet-event {:event (-> context :coeffects :event)
                                               :timestamp (shared-utils/now-secs)})
                    ; request wallet unlock
                    (re-frame/dispatch [::web3-events/authorize-ethereum-provider])
                    ; interrupt the event processing
                    (assoc context :queue #queue [])))))))


(def check-chain
  ;; interceptor to make sure wallet is connected to the proper network.
  ;; If not in the right network, it will request the user to switch networks.
  ;; Additionally, it stores the current event, so it resumes it when network is switched.
  (re-frame.core/->interceptor
    :id :connect-wallet
    :before (fn [context]
              (let [db (-> context :coeffects :db)
                    chain-id (-> config-map :web3-chain :chain-id)]
                (if (= chain-id (chain-queries/chain db))
                  ; if chain is correct, we just follow the normal flow
                  context
                  (do
                    ; stores the interrupted event to resume it later
                    (reset! last-wallet-event {:event (-> context :coeffects :event)
                                               :timestamp (shared-utils/now-secs)})
                    ; request a change of network
                    (re-frame/dispatch [::chain-events/request-switch-chain
                                        chain-id
                                        {:chain-info (:web3-chain config-map)}])
                    ; interrupt the event processing
                    (assoc context :queue #queue [])))))))

(def wallet-chain-interceptors [connect-wallet check-chain])

(re-frame/reg-event-fx
  :user/sign-in
  ; To log-in, the user first requests an OTP to the server.
  ; Then it sign a message (containing the OTP) with its wallet and send it to the server.
  ; The server will produce a JWT which is stored in the browser to be sent on each request.
  [connect-wallet]
  (fn [{:keys [db]} _]
    (let [active-account (account-queries/active-account db)
          query
          {:queries [[:generate-otp
                      {:user/address :$address}]]
           :variables [{:variable/name :$address
                        :variable/type :ID!}]}]
      {:dispatch [::gql-events/mutation
                  {:query query
                   :variables {:address active-account}
                   :on-success [:user/request-signature]
                   :on-error [::dispatch-n
                              [[::error-notification/show-error "An error occurs while requesting OTP to the server"]
                               [::logging/error " Error Requesting OTP to the server."]]]}]})))

(re-frame/reg-event-fx
  :user/request-signature
  ; Once having the OTP, the user sign a message with its wallet and send it to the server.
  (fn [{:keys [db]} [_ response]]
    (let [otp (:generate-otp response)
          active-account (account-queries/active-account db)
          data-str (gstring/format shared-utils/auth-data-msg otp)]
      {:web3/personal-sign
       {:web3 (web3-queries/web3 db)
        :data-str data-str
        :from active-account
        :on-success [:user/-authenticate {:data-str data-str}]
        :on-error [::dispatch-n
                   [[::error-notification/show-error "Error signing with current account"]
                    [::logging/error "Error Signing with Active Ethereum Account."]]]}})))

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
    {:dispatch-n [[::error-notification/show-error "An error occurs while authenticating" error]
                  [::logging/error "Failed to authenticate" {:error error}]]}))

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

(re-frame/reg-event-fx
  ::trust-domain
  ; Stores a domain to trust
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [_ {:keys [:domain] :as data}]]
    {:db (assoc-in db [:trust-domains domain] true)
     :store (assoc-in store [:trust-domains domain] true)}))

(re-frame/reg-event-fx
  ::set-waiting-wallet
  ; Sets if a transaction is waiting for wallet action
  [interceptors]
  (fn [{:keys [db]} [tx-id waiting]]
    {:db (assoc-in db [:waiting-wallet tx-id] waiting)}))

(re-frame/reg-event-fx
  ::send-tx-started
  ; Event launched when a new tx is triggered
  [interceptors]
  (fn [_ [[_ {:keys [:tx-id]}]]]
    {:dispatch-n [[::set-waiting-wallet tx-id true]
                  [::notification-events/show "Complete transaction with your wallet..."]]}))

(re-frame/reg-event-fx
  ::send-tx-finished
  ; Event launched when a tx finishes. Either fails or completes
  [interceptors]
  (fn [_ [[_ {:keys [:tx-id]}]]]
    {:dispatch [::set-waiting-wallet tx-id false]}))

(re-frame/reg-event-fx
  ::dispatch-n
  (fn [_ [_ evs & args]]
    {:dispatch-n
     (map #(conj % args) evs)}))
