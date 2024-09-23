(ns streamtide.server.graphql.graphql-resolvers
  "Defines the resolvers aimed to handle the GraphQL requests.
  This namespace does not perform any authorization or any complex logic, but it is mainly an entry point which
  delegates the GraphQL calls to the business logic layer."
  (:require [clojure.string :as string]
            [district.graphql-utils :as graphql-utils]
            [district.shared.async-helpers :refer [safe-go <?]]
            [district.shared.error-handling :refer [try-catch-throw]]
            [eip55.core :as eip55]
            [streamtide.server.business-logic :as logic]
            [streamtide.server.graphql.authorization :as authorization]
            [streamtide.server.utils :refer [wrap-as-promise]]
            [taoensso.timbre :as log]))


(def enum graphql-utils/kw->gql-name)

(defn- user-id [user]
  (:user/address user))

(defn- db-name->gql-enum [kw-ns db-name]
  (enum (keyword kw-ns db-name)))

(defn- gql-name->db-name [gql-name]
  (-> gql-name graphql-utils/gql-name->kw name))

(defn user-query-resolver [_ {:keys [:user/address] :as args} {:keys [:current-user]}]
  (log/debug "user args" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (let [user (<? (logic/get-user (user-id current-user) address))]
          (when-not (empty? user)
            user))))))

(defn user->socials-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]} ]
  (log/debug "user->socials-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-user-socials (user-id current-user) address))))

(defn user->perks-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->perks-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (:user/perks (<? (logic/get-user-perks (user-id current-user) {:user/address address})))))))

(defn user->grant-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->grant-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-grant (user-id current-user) address))))

(defn user->blacklisted-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->blacklisted-resolver args" user)
  (if (contains? user :user/blacklisted)
    (:user/blacklisted user)
    (try-catch-throw
      (wrap-as-promise
        (logic/blacklisted? (user-id current-user) address)))))

(defn user->last-seen-resolver [{:keys [:timestamp/last-seen :user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->last-seen-resolver args" user)
  (if last-seen
    last-seen
    (try-catch-throw
      (wrap-as-promise
        (safe-go
          (:timestamp/last-seen (<? (logic/get-user-timestamps (user-id current-user) address))))))))

(defn user->last-modification-resolver [{:keys [:timestamp/last-modification :user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->last-modification-resolver args" user)
  (if last-modification
    last-modification
    (try-catch-throw
      (wrap-as-promise
        (safe-go
          (:timestamp/last-modification (<? (logic/get-user-timestamps (user-id current-user) address))))))))

(defn user->has-private-content-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->has-private-content-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (logic/has-private-content? (user-id current-user) address))))

(defn user->unlocked-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->unlocked-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (logic/unlocked? (user-id current-user) address))))

(defn user->notification-categories-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->notification-categories-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (let [categories (<? (logic/get-notification-categories (user-id current-user) address))]
          (map #(-> %
                    (update :notification/category (fn [c] (db-name->gql-enum :notification-category c)))
                    (update :notification/type (fn [t] (db-name->gql-enum :notification-type t))))
               categories))))))

(defn user->notification-types-resolver [{:keys [:user/address] :as user} _ {:keys [:current-user]}]
  (log/debug "user->notification-types-resolver args" user)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (let [notification-types (<? (logic/get-notification-types (user-id current-user) address))]
          (map #(update % :notification/type (fn [t] (db-name->gql-enum :notification-type t)))
               notification-types))))))

(defn grant->user-resolver [{:keys [:grant/user] :as user-grant}]
  (log/debug "grant->user-resolver args" user-grant)
  user-grant)

(defn content->user-resolver [{:keys [:content/user] :as user-content}]
  (log/debug "content->user-resolver args" user-content)
  user-content)

(defn grant->status-resolver [{:keys [:grant/status]}]
  (if status
    (db-name->gql-enum :grant.status status)
    (enum :grant.status/unrequested)))

(defn content->type-resolver [{:keys [:content/type]}]
  (db-name->gql-enum :content-type type))

(defn donation->sender-resolver [{:keys [:donation/sender] :as user-donation} _ {:keys [:current-user]}]
  (log/debug "donation->sender-resolver args" user-donation)
  (wrap-as-promise
    (logic/get-user (user-id current-user) sender)))

(defn donation->coin-resolver [{:keys [:donation/coin] :as donation-coin} _ {:keys [:current-user]}]
  (log/debug "donation->coin-resolver args" donation-coin)
  (wrap-as-promise
    (logic/get-coin (user-id current-user) coin)))

(defn donation->receiver-resolver [{:keys [:donation/receiver] :as user-donation}]
  (log/debug "donation->receiver-resolver args" user-donation)
  user-donation)

(defn matching->receiver-resolver [{:keys [:matching/receiver] :as user-donation}]
  (log/debug "matching->receiver-resolver args" user-donation)
  user-donation)

(defn matching->coin-resolver [{:keys [:matching/coin] :as matching-coin} _ {:keys [:current-user]}]
  (log/debug "matching->coin-resolver args" matching-coin)
  (wrap-as-promise
    (logic/get-coin (user-id current-user) coin)))

(defn matching-pool->coin-resolver [{:keys [:matching-pool/coin] :as matching-pool-coin} _ {:keys [:current-user]}]
  (log/debug "matching-pool->coin-resolver args" matching-pool-coin)
  (wrap-as-promise
    (logic/get-coin (user-id current-user) coin)))

(defn coin-amount->coin-resolver [{:keys [:coin] :as coin-amount} _ {:keys [:current-user]}]
  (log/debug "coin-amount->coin-resolver args" coin-amount)
  (wrap-as-promise
    (logic/get-coin (user-id current-user) coin)))

(defn campaign->user-resolver [{:keys [:user/address] :as user-campaign} _ {:keys [:current-user]}]
  (log/debug "campaign->user-resolver args" user-campaign)
  (if (contains? user-campaign :user/name)
    user-campaign
    (wrap-as-promise
      (logic/get-user (user-id current-user) address))))

(defn leader->receiver-resolver [{:keys [:leader/receiver] :as user-donation}]
  (log/debug "leader->receiver-resolver args" user-donation)
  user-donation)

(defn grant-query-resolver [_ {:keys [:user/address] :as args} {:keys [:current-user]}]
  (log/debug "grant args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-grant (user-id current-user) address))))

(defn round-query-resolver [_ {:keys [:round/id] :as args} {:keys [:current-user]}]
  (log/debug "round args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-round (user-id current-user) id))))

(defn campaign-query-resolver [_ {:keys [:campaign/id] :as args} {:keys [:current-user]}]
  (log/debug "campaign args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-farcaster-campaign (user-id current-user) id))))

(defn search-grants-query-resolver [_ {:keys [:statuses :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "search grants args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-grants (user-id current-user) (cond-> args
                                                       (:statuses args)
                                                       (update :statuses (fn [statuses]
                                                                           (map gql-name->db-name statuses)))
                                                       (:order-by args)
                                                       (update :order-by graphql-utils/gql-name->kw))))))

(defn search-contents-query-resolver [_ {:keys [:user/address :only-public :content/pinned :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "search contents args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-contents (user-id current-user) (cond-> args
                                                         (:order-by args)
                                                         (update :order-by graphql-utils/gql-name->kw))))))

(defn search-donations-query-resolver [_ {:keys [:sender :receiver :round :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "donations args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-donations (user-id current-user) (cond-> args
                                                          (:order-by args)
                                                          (update :order-by graphql-utils/gql-name->kw))))))

(defn search-matchings-query-resolver [_ {:keys [:receiver :round :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "matchings args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-matchings (user-id current-user) (cond-> args
                                                          (:order-by args)
                                                          (update :order-by graphql-utils/gql-name->kw))))))

(defn search-leaders-query-resolver [_ {:keys [:round :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "leaders args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-leaders (user-id current-user) (cond-> args
                                                        (:order-by args)
                                                        (update :order-by graphql-utils/gql-name->kw))))))

(defn search-rounds-query-resolver [_ {:keys [:order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "rounds args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-rounds (user-id current-user) (cond-> args
                                                       (:order-by args)
                                                       (update :order-by graphql-utils/gql-name->kw))))))

(defn search-campaigns-query-resolver [_ {:keys [:order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "campaigns args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-farcaster-campaigns (user-id current-user) (cond-> args
                                                                    (:order-by args)
                                                                    (update :order-by graphql-utils/gql-name->kw))))))

(defn search-users-query-resolver [_ {:keys [:user/name :user/address :user/blacklisted :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "search users args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-users (user-id current-user) (cond-> args
                                                      (:order-by args)
                                                      (update :order-by graphql-utils/gql-name->kw))))))

(defn announcements-query-resolver [_ {:keys [:first :after] :as args} {:keys [:current-user]}]
  (log/debug "announcements args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/get-announcements (user-id current-user) args))))


(defn adjust-notification-categories [categories]
  (map (fn [category-setting]
         (-> category-setting
             (update :notification/category gql-name->db-name)
             (update :notification/type gql-name->db-name))) categories))

(defn adjust-notification-types [types]
  (map (fn [type-setting]
         (update type-setting :notification/type gql-name->db-name)) types))


(defn update-user-info-mutation [_ {:keys [:input] :as args} {:keys [:current-user :config]}]
  (log/debug "update-user-info-mutation" {:input input
                                          :current-user current-user})
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (let [user-id (user-id current-user)
              input (:input args)
              input (cond-> input
                            (:user/notification-categories input)
                            (update :user/notification-categories adjust-notification-categories)
                            (:user/notification-types input)
                            (update :user/notification-types adjust-notification-types))]
          (<? (logic/update-user-info! user-id input config))
          (<? (logic/get-user user-id user-id)))))))

(defn sign-in-mutation [_ {:keys [:signature :payload] :as args} {:keys [config]}]
  (log/debug "sign-in-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (let [{:keys [sign-in-secret expires-in]} (-> config :graphql)
              address (eip55/address->checksum (:address payload))]
          (<? (authorization/validate-signature payload signature))
          (authorization/validate-payload payload sign-in-secret)
          (<? (logic/validate-sign-in address))
          (let [jwt (authorization/create-jwt address sign-in-secret expires-in)]
            {:jwt jwt :user/address address}))))))

(defn generate-login-payload-mutation [_ {:keys [:user/address :chain-id] :as args} {:keys [config]}]
  (log/debug "generate-login-payload-mutation" args)
  (try-catch-throw
    (when (not= (str chain-id) (str (-> config :siwe-payload :chain-id)))
      (throw (js/Error. (str "Invalid chain id. Expected: " (-> config :siwe-payload :chain-id) ". Found: " chain-id))))
    (let [nonce (authorization/generate-otp (-> config :graphql :sign-in-secret) address)
          now (.toISOString (js/Date.))
          expire (.toISOString (js/Date. (+ (* authorization/otp-time-step 1000) (.getTime (js/Date.)))))]
      (merge (-> config :siwe-payload)
        {:address address
         :nonce nonce
         :issued-at now
         :expiration-time expire}))))

(defn roles-query-resolver [_ _ {:keys [:current-user]}]
  (log/debug "roles resolver" current-user)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (map enum (<? (logic/get-roles (user-id current-user))))))))

(defn request-grant-mutation [_ args {:keys [:current-user]}]
  (log/debug "request-grant-mutation" args)
  (let [user-address (user-id current-user)]
    (try-catch-throw
      (wrap-as-promise
        (safe-go
          (<? (logic/request-grant! user-address))
          true)))))

(defn review-grants-mutation [_ {:keys [:user/addresses :grant/status] :as args} {:keys [:current-user]}]
  (log/debug "review-grants-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/review-grants! (user-id current-user)
                              {:user/addresses addresses
                               :grant/status (gql-name->db-name status)}))
        true))))

(defn add-announcement-mutation [_ {:keys [:announcement/text] :as args} {:keys [:current-user]}]
  (log/debug "add-announcement-mutation" args)
  (try-catch-throw
    (when (string/blank? text)
      (throw (js/Error. "Announcement cannot be empty")))
    (wrap-as-promise
      (safe-go
        (<? (logic/add-announcement! (user-id current-user) (select-keys args [:announcement/text])))
        true))))


(defn remove-announcement-mutation [_ {:keys [:announcement/id] :as args} {:keys [:current-user]}]
  (log/debug "remove-announcement-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/remove-announcement! (user-id current-user) (select-keys args [:announcement/id])))
        true))))

(defn add-content-mutation [_ {:keys [:content/url :content/type :content/public :content/pinned] :as args} {:keys [:current-user]}]
  (log/debug "add-content-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/add-content! (user-id current-user) (cond-> args
                                                       true (select-keys [:content/url :content/type :content/public :content/pinned])
                                                       (not (:content/public args)) (assoc :content/public false)
                                                       (not (:content/pinned args)) (assoc :content/pinned false)
                                                       (:content/type args) (update :content/type gql-name->db-name))))
        true))))

(defn remove-content-mutation [_ {:keys [:content/id] :as args} {:keys [:current-user]}]
  (log/debug "remove-content-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/remove-content! (user-id current-user) (select-keys args [:content/id])))
        true))))

(defn set-content-visibility-mutation [_ {:keys [:content/id] :as args} {:keys [:current-user]}]
  (log/debug "set-content-visibility-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/set-content-visibility! (user-id current-user) (select-keys args [:content/id :content/public])))
        true))))

(defn set-content-pinned-mutation [_ {:keys [:content/id] :as args} {:keys [:current-user]}]
  (log/debug "set-content-pinned-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/set-content-pinned! (user-id current-user) (select-keys args [:content/id :content/pinned])))
        true))))

(defn add-campaign-mutation [_ {:keys [:user/address :campaign/image :campaign/start-date :campaign/end-date] :as args} {:keys [:current-user :config]}]
  (log/debug "add-campaign-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/add-farcaster-campaign! (user-id current-user)
                                           (select-keys args [:user/address :campaign/image :campaign/start-date :campaign/end-date])
                                           config))
        true))))

(defn gql-date->secs [date]
  (js/parseInt (/ (.getTime (graphql-utils/gql-date->date date)) 1000)))

(defn update-campaign-mutation [_ {:keys [:campaign/id :user/address :campaign/image :campaign/start-date :campaign/end-date] :as args} {:keys [:current-user :config]}]
  (log/debug "update-campaign-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/update-farcaster-campaign! (user-id current-user)
                                              (cond-> (select-keys args [:campaign/id :user/address :campaign/image :campaign/start-date :campaign/end-date])
                                                      (:campaign/start-date args) (update :campaign/start-date gql-date->secs)
                                                      (:campaign/end-date args) (update :campaign/end-date gql-date->secs))
                                              config))
        true))))

(defn remove-campaign-mutation [_ {:keys [:campaign/id] :as args} {:keys [:current-user]}]
  (log/debug "remove-campaign-mutation" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/remove-farcaster-campaign! (user-id current-user) (select-keys args [:campaign/id])))
        true))))

(defn verify-social-mutation [_ {:keys [:code :state] :as args} {:keys [:current-user]}]
  (log/debug "verify-social args" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (try
          (let [{:keys [:valid? :url :message]}
                (<? (logic/verify-social! (user-id current-user) args))]
            {:is-valid valid?
             :url url
             :message message})
          (catch :default _
            {:is-valid false
             :message "Internal Error"}))))))

(defn generate-twitter-oauth-url-mutation [_ {:keys [:callback] :as args} {:keys [:current-user]}]
  (log/debug "request-twitter-oauth args" args)
  (try-catch-throw
    (wrap-as-promise
      (logic/generate-twitter-oauth-url (user-id current-user) args))))

(defn add-notification-type-mutation [_ {:keys [:notification/type] :as args} {:keys [:current-user]}]
  (log/debug "add-notification-type-mutation args" args)
  (try-catch-throw
    (wrap-as-promise
      (safe-go
        (<? (logic/add-notification-type (user-id current-user) (update type :notification/type graphql-utils/gql-name->kw)))
        true))))

;; Map GraphQL types to the functions handling them
(def resolvers-map
  {:Query {:user user-query-resolver
           :grant grant-query-resolver
           :round round-query-resolver
           :campaign campaign-query-resolver
           :search-grants search-grants-query-resolver
           :search-contents search-contents-query-resolver
           :search-donations search-donations-query-resolver
           :search-matchings search-matchings-query-resolver
           :search-leaders search-leaders-query-resolver
           :search-rounds search-rounds-query-resolver
           :search-campaigns search-campaigns-query-resolver
           :roles roles-query-resolver
           :search-users search-users-query-resolver
           :announcements announcements-query-resolver}
   :Mutation {:update-user-info update-user-info-mutation
              :request-grant request-grant-mutation
              :review-grants review-grants-mutation
              :add-announcement add-announcement-mutation
              :remove-announcement remove-announcement-mutation
              :add-content add-content-mutation
              :remove-content remove-content-mutation
              :set-content-visibility set-content-visibility-mutation
              :set-content-pinned set-content-pinned-mutation
              :add-campaign add-campaign-mutation
              :update-campaign update-campaign-mutation
              :remove-campaign remove-campaign-mutation
              :sign-in sign-in-mutation
              :generate-login-payload generate-login-payload-mutation
              :verify-social verify-social-mutation
              :generate-twitter-oauth-url generate-twitter-oauth-url-mutation
              :add-notification-type add-notification-type-mutation}
   :User {:user/socials user->socials-resolver
          :user/perks user->perks-resolver
          :user/grant user->grant-resolver
          :user/blacklisted user->blacklisted-resolver
          :user/last-seen user->last-seen-resolver
          :user/last-modification user->last-modification-resolver
          :user/has-private-content user->has-private-content-resolver
          :user/unlocked user->unlocked-resolver
          :user/notification-categories user->notification-categories-resolver
          :user/notification-types user->notification-types-resolver}
   :Grant {:grant/user grant->user-resolver
           :grant/status grant->status-resolver}
   :Content {:content/user content->user-resolver
             :content/type content->type-resolver}
   :Donation {:donation/receiver donation->receiver-resolver
              :donation/sender donation->sender-resolver
              :donation/coin donation->coin-resolver}
   :Matching {:matching/receiver matching->receiver-resolver
              :matching/coin matching->coin-resolver}
   :Leader {:leader/receiver leader->receiver-resolver}
   :MatchingPool {:matching-pool/coin matching-pool->coin-resolver}
   :CoinAmount {:coin coin-amount->coin-resolver}
   :Campaign {:campaign/user campaign->user-resolver}
   })
