(ns streamtide.ui.components.verifiers
  "Verifiers connectors for different social networks"
  (:require
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [re-frame.core :refer [subscribe dispatch] :as re-frame]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.ui.config :refer [config-map]]))

(defmulti verify (fn [data]
                   (:social/network data)))

(def oauth-callback-url (str (-> js/window .-location .-origin) "/oauth-callback-verifier"))

(defmethod verify :twitter
  [data]
  ; To verify twitter we need to follow the following process:
  ; 1) request an oauth URL to our server, indicating the callback url (i.e., the URL to redirect after successful log-in)
  ; 2) open a new browser window to open the twitter log-in page (the one generated in step 1)
  ; 3) (the user logs in and authorize our App)
  ; 4) the user is redirected to the callback URL specified in step 1, but also including an oauth_verifier token
  ; 6) sends a BroadcastMessage to the original window to pass the oauth_verifier and closes this one.
  ; 5) send the oauth_verifier to our server, so we can verify it is valid
  (dispatch [::request-twitter-oauth-url (merge data {:callback oauth-callback-url})]))

(defmethod verify :discord
  [data]
  ; To verify discord the process is a bit more simple than twitter as we use oauth, but we don't need to generate the first url:
  ; 1) open a new browser window to open the discord log-in page
  ; 2) (the user logs in and authorize our App)
  ; 3) the user is redirected to the redirect_uri specified in step 1, but also including an oauth_verifier token
  ; 4) sends a BroadcastMessage to the original window to pass the oauth_verifier and closes this one.
  ; 5) send the oauth_verifier to our server, so we can verify it is valid
  (dispatch [::trigger-oauth-authentication
             (merge data {:url (str "https://discord.com/api/oauth2/authorize?response_type=code&scope=identify"
                                    "&client_id=" (get-in config-map [:verifiers :discord :client-id])
                                    "&state=" (shared-utils/network->uuid :discord)
                                    "&redirect_uri=" oauth-callback-url)
                          :social/network :discord})]))

(defmethod verify :eth
  [data]
  ; to check eth balances we don't need user interaction. We can call directly the server to check the balance using web3 libs.
  (dispatch [::verify-code data {:state "eth"}]))


(re-frame/reg-fx
  :verifiers/verify-social
  ; Starts the process to verify a social link
  (fn [{:keys [:social/network :on-success :on-error] :as data}]
    (verify data)))

(re-frame/reg-event-fx
  ::request-twitter-oauth-url
  ; Sends a GraphQL request to the server to generate a twitter oauth url where to authenticate to
  (fn [{:keys [db]} [_ {:keys [:callback] :as data}]]

    (let [query
          {:queries [[:generate-twitter-oauth-url
                      {:callback :$callback}]]
           :variables [{:variable/name :$callback
                        :variable/type :String!}]}]
      {:dispatch [::gql-events/mutation
                  {:query query
                   :variables {:callback callback}
                   :on-success [::request-twitter-oauth-url-success data]
                   :on-error [::request-twitter-oauth-url-error data]}]})))

(re-frame/reg-event-fx
  ::request-twitter-oauth-url-success
  ; After receiving the oauth url, it opens a window to make the log-in
  (fn [{:keys [db]} [_ data response]]
    (let [auth-url (:generate-twitter-oauth-url response)]
      {:dispatch [::trigger-oauth-authentication (merge data {:url auth-url})]})))

(re-frame/reg-event-fx
  ::request-twitter-oauth-url-error
  (fn [{:keys [db]} [_ data error]]
    {:dispatch-n [(when (:on-error data) (conj (:on-error data) {:message "Failed to retrieve twitter oauth URL"}))
                  [::logging/error
                   "Failed to verify oauth verifier"
                   {:error (map :message error)
                    :data data} ::request-twitter-oauth-error]]}))

(re-frame/reg-event-fx
  ::trigger-oauth-authentication
  ; Open a new window to perform external authentication, and a Broadcast channel to receive the response of the authentication
  (fn [{:keys [db]} [_ {:keys [:social/network :url] :as data}]]
    {:open-broadcast-channel data
     :open-window {:url url}}))

(re-frame/reg-fx
  :open-window
  ; Opens URL in a new browser window. Usually to perform authentication
  (fn [{:keys [:url]}]
    (js/window.open
      url
      "_blank" "toolbar=no, location=no, directories=no, status=no, menubar=no, resizable=no, copyhistory=no, width=600, height=800")))

(re-frame/reg-fx
  :open-broadcast-channel
  ; A Broadcast channel is open to listen the message sent when the authentication completes
  (fn [{:keys [:social/network] :as data}]
    (let [channel (js/BroadcastChannel. (str "verifier_" (name network)))]
      (aset channel "onmessage" (fn [msg]
                                  (.close channel)  ; as soon as it receives a message, we can close the channel
                                  (let [msg-data (-> msg .-data (js->clj :keywordize-keys true))]
                                    (if (:code msg-data)
                                      (dispatch [::verify-code data msg-data])
                                      (when (:on-error data) (dispatch (conj (:on-error data) (:error msg-data)))))))))))

(re-frame/reg-event-fx
  ::verify-code
  ; Send a GraphQL request to the server to check if the code resulting from authentication to a given network is valid.
  ; Note this is a mutation because in case the code is valid, it marks the network as valid
  (fn [{:keys [db]} [_ data {:keys [:code :state] :as result}]]
    (let [query
          {:queries [[:verify-social
                      {:code :$code
                       :state :$state}]]
           :variables [{:variable/name :$code
                        :variable/type :String}
                       {:variable/name :$state
                        :variable/type :String!}]}]
      {:dispatch [::gql-events/mutation
                  {:query query
                   :variables {:code code
                               :state state}
                   :on-success [::verify-code-success (merge data result)]
                   :on-error [::verify-code-error (merge data result)]}]})))

(re-frame/reg-event-fx
  ::verify-code-success
  (fn [{:keys [db]} [_ {:keys [:on-success :on-error]} result]]
    (if (:verify-social result)
      {:dispatch on-success}
      {:dispatch (conj on-error {:error "Invalid oauth verifier"})})))

(re-frame/reg-event-fx
  ::verify-code-error
  (fn [{:keys [db]} [_ {:keys [:on-error] :as data} error]]
    {:dispatch-n [(when on-error on-error)
                  [::notification-events/show "[ERROR] An error occurs while verifying oauth verifier"]
                  [::logging/error
                   "Failed to verify oauth verifier"
                   {:error (map :message error)
                    :data data} ::verify-code]]}))
