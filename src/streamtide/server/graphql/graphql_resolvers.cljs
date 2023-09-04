(ns streamtide.server.graphql.graphql-resolvers
  "Defines the resolvers aimed to handle the GraphQL requests.
  This namespace does not perform any authorization or any complex logic, but it is mainly an entry point which
  delegates the GraphQL calls to the business logic layer."
  (:require [cljs.core.async :refer [<!]]
            [district.graphql-utils :as graphql-utils]
            [district.shared.async-helpers :refer [safe-go <?]]
            [district.shared.error-handling :refer [try-catch-throw]]
            [streamtide.server.business-logic :as logic]
            [streamtide.server.graphql.authorization :as authorization]
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
    (let [user (logic/get-user (user-id current-user) address)]
      (when-not (empty? user)
        user))))

(defn user->socials-resolver [{:keys [:user/address] :as user} {:keys [:current-user]}]
  (log/debug "user->socials-resolver args" user)
  (try-catch-throw
    (logic/get-user-socials (user-id current-user) address)))

(defn user->grant-resolver [{:keys [:user/address] :as user} {:keys [:current-user]}]
  (log/debug "user->grant-resolver args" user)
  (try-catch-throw
    (logic/get-grant (user-id current-user) address)))

(defn user->blacklisted-resolver [{:keys [:user/address] :as user} {:keys [:current-user]}]
  (log/debug "user->blacklisted-resolver args" user)
  (if (contains? user :user/blacklisted)
    (:user/blacklisted user)
    (try-catch-throw
      (logic/blacklisted? (user-id current-user) address))))

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
  (logic/get-user (user-id current-user) sender))

(defn donation->receiver-resolver [{:keys [:donation/receiver] :as user-donation}]
  (log/debug "donation->receiver-resolver args" user-donation)
  user-donation)

(defn matching->receiver-resolver [{:keys [:matching/receiver] :as user-donation}]
  (log/debug "matching->receiver-resolver args" user-donation)
  user-donation)

(defn leader->receiver-resolver [{:keys [:leader/receiver] :as user-donation}]
  (log/debug "leader->receiver-resolver args" user-donation)
  user-donation)

(defn grant-query-resolver [_ {:keys [:user/address] :as args} {:keys [:current-user]}]
  (log/debug "grant args" args)
  (try-catch-throw
    (logic/get-grant (user-id current-user) address)))

(defn round-query-resolver [_ {:keys [:round/id] :as args} {:keys [:current-user]}]
  (log/debug "round args" args)
  (try-catch-throw
    (logic/get-round (user-id current-user) id)))

(defn search-grants-query-resolver [_ {:keys [:statuses :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "search grants args" args)
  (try-catch-throw
    (logic/get-grants (user-id current-user) (cond-> args
                                                     (:statuses args)
                                                     (update :statuses (fn [statuses]
                                                                         (map gql-name->db-name statuses)))
                                                     (:order-by args)
                                                     (update :order-by graphql-utils/gql-name->kw)
                                                     ))))

(defn search-contents-query-resolver [_ {:keys [:user/address :only-public :content/pinned :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "search contents args" args)
  (try-catch-throw
    (logic/get-contents (user-id current-user) (cond-> args
                                                       (:order-by args)
                                                       (update :order-by graphql-utils/gql-name->kw)))))

(defn search-donations-query-resolver [_ {:keys [:sender :receiver :round :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "donations args" args)
  (try-catch-throw
    (logic/get-donations (user-id current-user) (cond-> args
                                                        (:order-by args)
                                                        (update :order-by graphql-utils/gql-name->kw)))))

(defn search-matchings-query-resolver [_ {:keys [:receiver :round :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "matchings args" args)
  (try-catch-throw
    (logic/get-matchings (user-id current-user) (cond-> args
                                                        (:order-by args)
                                                        (update :order-by graphql-utils/gql-name->kw)))))

(defn search-leaders-query-resolver [_ {:keys [:round :search-term :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "leaders args" args)
  (try-catch-throw
    (logic/get-leaders (user-id current-user) (cond-> args
                                                        (:order-by args)
                                                        (update :order-by graphql-utils/gql-name->kw)))))

(defn search-rounds-query-resolver [_ {:keys [:order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "rounds args" args)
  (try-catch-throw
    (logic/get-rounds (user-id current-user) (cond-> args
                                                        (:order-by args)
                                                        (update :order-by graphql-utils/gql-name->kw)))))

(defn search-users-query-resolver [_ {:keys [:user/name :user/address :user/blacklisted :order-by :order-dir :first :after] :as args} {:keys [:current-user]}]
  (log/debug "search users args" args)
  (try-catch-throw
    (logic/get-users (user-id current-user) (cond-> args
                                                    (:order-by args)
                                                    (update :order-by graphql-utils/gql-name->kw)))))

(defn announcements-query-resolver [_ {:keys [:first :after] :as args} {:keys [:current-user]}]
  (log/debug "announcements args" args)
  (try-catch-throw
    (logic/get-announcements (user-id current-user) args)))


(defn update-user-info-mutation [_ {:keys [:input] :as args} {:keys [:current-user :config]}]
  (log/debug "update-user-info-mutation" {:input input
                                          :current-user current-user})
  (try-catch-throw
    (let [user-id (user-id current-user)]
      (logic/update-user-info! user-id (:input args) config)
      (logic/get-user user-id user-id))))

(defn sign-in-mutation [_ {:keys [:data-signature :data] :as args} {:keys [config]}]
  (log/debug "sign-in-mutation" args)
  (try-catch-throw
    (let [{:keys [sign-in-secret expires-in]} (-> config :graphql)
          user-address (authorization/recover-personal-signature data data-signature)]
      (authorization/validate-data data sign-in-secret user-address)
      (logic/validate-sign-in user-address)
      (let [jwt (authorization/create-jwt user-address sign-in-secret expires-in)]
        {:jwt jwt :user/address user-address}))))

(defn generate-otp-mutation [_ {:keys [:user/address] :as args} {:keys [config]}]
  (log/debug "generate-otp-mutation" args)
  (try-catch-throw
    (authorization/generate-otp (-> config :graphql :sign-in-secret) address)))

(defn roles-query-resolver [_ _ {:keys [:current-user]}]
  (log/debug "roles resolver" current-user)
  (try-catch-throw
    (map enum (logic/get-roles (user-id current-user)))))

(defn request-grant-mutation [_ args {:keys [:current-user]}]
  (log/debug "request-grant-mutation" args)
  (let [user-address (user-id current-user)]
    (try-catch-throw
      (logic/request-grant! user-address)
      true)))

(defn review-grants-mutation [_ {:keys [:user/addresses :grant/status] :as args} {:keys [:current-user]}]
  (log/debug "review-grants-mutation" args)
  (try-catch-throw
    (logic/review-grants! (user-id current-user)
                          {:user/addresses addresses
                           :grant/status (gql-name->db-name status)})
    true))

;(defn blacklist-mutation [_ {:keys [:user/address :blacklist] :as args} {:keys [:current-user]}]
;  (log/debug "blacklist-mutation" args)
;  (try-catch-throw
;    (logic/blacklist! (user-id current-user) (select-keys args [:user/address :blacklist]))
;    (logic/get-user (user-id current-user) address)))

(defn add-announcement-mutation [_ {:keys [:announcement/text] :as args} {:keys [:current-user]}]
  (log/debug "add-announcement-mutation" args)
  (try-catch-throw
    (logic/add-announcement! (user-id current-user) (select-keys args [:announcement/text]))
    true))


(defn remove-announcement-mutation [_ {:keys [:announcement/id] :as args} {:keys [:current-user]}]
  (log/debug "remove-announcement-mutation" args)
  (try-catch-throw
    (logic/remove-announcement! (user-id current-user) (select-keys args [:announcement/id]))
    true))

(defn add-content-mutation [_ {:keys [:content/url :content/type :content/public :content/pinned] :as args} {:keys [:current-user]}]
  (log/debug "add-content-mutation" args)
  (try-catch-throw
    (logic/add-content! (user-id current-user) (cond-> args
                                                       true (select-keys [:content/url :content/type :content/public :content/pinned])
                                                       (not (:content/public args)) (assoc :content/public false)
                                                       (not (:content/pinned args)) (assoc :content/pinned false)
                                                       (:content/type args) (update :content/type gql-name->db-name)))
    true))

(defn remove-content-mutation [_ {:keys [:content/id] :as args} {:keys [:current-user]}]
  (log/debug "remove-content-mutation" args)
  (try-catch-throw
    (logic/remove-content! (user-id current-user) (select-keys args [:content/id]))
    true))

(defn set-content-visibility-mutation [_ {:keys [:content/id] :as args} {:keys [:current-user]}]
  (log/debug "set-content-visibility-mutation" args)
  (try-catch-throw
    (logic/set-content-visibility! (user-id current-user) (select-keys args [:content/id :content/public]))
    true))

(defn set-content-pinned-mutation [_ {:keys [:content/id] :as args} {:keys [:current-user]}]
  (log/debug "set-content-pinned-mutation" args)
  (try-catch-throw
    (logic/set-content-pinned! (user-id current-user) (select-keys args [:content/id :content/pinned]))
    true))

(defn wrap-as-promise
  [chanl]
  (js/Promise. (fn [resolve _]
                    (safe-go (resolve (<! chanl))))))

(defn verify-social-mutation [_ {:keys [:code :state] :as args} {:keys [:current-user]}]
  (log/debug "verify-social args" args)
  (try-catch-throw
    (wrap-as-promise (safe-go
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
    (logic/generate-twitter-oauth-url (user-id current-user) args)))

;; Map GraphQL types to the functions handling them
(def resolvers-map
  {:Query {:user user-query-resolver
           :grant grant-query-resolver
           :round round-query-resolver
           :search-grants search-grants-query-resolver
           :search-contents search-contents-query-resolver
           :search-donations search-donations-query-resolver
           :search-matchings search-matchings-query-resolver
           :search-leaders search-leaders-query-resolver
           :search-rounds search-rounds-query-resolver
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
              :sign-in sign-in-mutation
              :generate-otp generate-otp-mutation
              :verify-social verify-social-mutation
              :generate-twitter-oauth-url generate-twitter-oauth-url-mutation}
   :User {:user/socials user->socials-resolver
          :user/grant user->grant-resolver
          :user/blacklisted user->blacklisted-resolver}
   :Grant {:grant/user grant->user-resolver
           :grant/status grant->status-resolver}
   :Content {:content/user content->user-resolver
             :content/type content->type-resolver}
   :Donation {:donation/receiver donation->receiver-resolver
              :donation/sender donation->sender-resolver}
   :Matching {:matching/receiver matching->receiver-resolver}
   :Leader {:leader/receiver leader->receiver-resolver}

   })
