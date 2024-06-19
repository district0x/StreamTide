(ns streamtide.server.business-logic
  "Layer implementing the server business logic.
  This is an intermediate layer between the GraphQL endpoint (or any other API which may come in the future)
  and the database functionality to enforce authorization and to enrich or validate input data."
  (:require
            [cljs.core.async :refer [go]]
            [cljs.nodejs :as nodejs]
            [clojure.string :as str]
            [clojure.string :as string]
            [district.shared.async-helpers :refer [safe-go <?]]
            [district.shared.error-handling :refer [try-catch-throw]]
            [fs]
            [streamtide.server.db :as stdb]
            [streamtide.server.notifiers.notifiers :as notifiers]
            [streamtide.server.verifiers.twitter-verifier :as twitter]
            [streamtide.server.verifiers.discord-verifier]
            [streamtide.server.verifiers.eth-verifier]
            [streamtide.server.verifiers.twitter-verifier :as twitter]
            [streamtide.server.verifiers.verifiers :as verifiers]
            [streamtide.server.notifiers.discord-notifier]
            [streamtide.server.notifiers.email-notifier]
            [streamtide.server.notifiers.web-push-notifier]
            [streamtide.shared.utils :as shared-utils]))

(def path (nodejs/require "path"))

(defn require-auth [current-user]
  "Check if the request comes from an authenticated user. Throws an error otherwise"
  (when-not current-user
    (throw (js/Error. "Authentication required"))))

(defn get-roles [current-user]
  "Gets the roles of the user sending the request"
  (require-auth current-user)
  (go
    (map #(keyword :role (:role/role %)) (<? (stdb/get-roles current-user)))))

(defn require-role [current-user role]
  "Checks the user performing the request has the given role. Throws an error otherwise"
  (go
    (when-not (contains? (set (<? (get-roles current-user))) role)
      (throw (js/Error. "Unauthorized")))))

(defn require-admin [current-user]
  "Checks the user performing the request is an administrator (i.e., has the 'admin' role). Throws an error otherwise"
  (require-role current-user :role/admin))

(defn require-not-blacklisted [current-user]
  "Checks the user performing the request is not blacklisted. Throws an error otherwise"
  (go
    (when (<? (stdb/blacklisted? {:user/address current-user}))
      (throw (js/Error. "Unauthorized - address blacklisted")))))

(defn require-grant-approved [current-user]
  "Checks the grant of the user performing the request has been already approved. Throws an error otherwise"
  (go
    (when-not (= (name :grant.status/approved) (:grant/status (<? (stdb/get-grant current-user))))
      (throw (js/Error. "Unauthorized - require approved grant to perform operation")))))

(defn require-same-user [current-user address]
  "Checks the requesting address is the same as the current user. Throws an error otherwise"
  (when-not (= current-user address)
    (throw (js/Error. "Unauthorized - User not allowed to fetch this info for another user"))))

(defn get-user [_current-user address]
  "Gets user data from its ETH address"
  (stdb/get-user address))

(defn get-user-socials [_current-user address]
  "Gets the social links of a user from its address"
  (stdb/get-user-socials {:user/address address}))

(defn get-user-perks [current-user args]
  "Gets the social links of a user from its address"
  (stdb/get-user-perks current-user args))

(defn get-grant [_current-user user-address]
  "Gets the grant info of a given user"
  (stdb/get-grant user-address))

(defn get-round [_current-user round-id]
  "Gets the info of a given round"
  (stdb/get-round round-id))

(defn get-grants [_current-user args]
  "Gets all the grants"
  (stdb/get-grants args))

(defn get-users [current-user args]
  "Gets all users"
  (go
    (when (or (:users.order-by/last-seen args)
              (:users.order-by/last-modification args))
      (require-auth current-user)
      (<? (require-admin current-user)))

    (stdb/get-users args)))

(defn get-announcements [_current-user args]
  "Gets defined announcements"
  (stdb/get-announcements args))

(defn get-contents [current-user args]
  "Gets content available for the current user"
  (stdb/get-contents current-user args))

(defn get-donations [_current-user args]
  "Gets all the donations info"
  (stdb/get-donations args))

(defn get-matchings [_current-user args]
  "Gets all the matchings info"
  (stdb/get-matchings args))

(defn get-leaders [_current-user args]
  "Gets all the leaders info"
  (stdb/get-leaders args))

(defn get-rounds [_current-user args]
  "Gets all the rounds info"
  (stdb/get-rounds args))

(defn get-coin [_current-user address]
  "Gets coin info from its ETH address"
  (stdb/get-coin address))

(defn verify-social! [current-user {:keys [:state] :as args}]
  "Verify a social network, for example checking the authentication code coming from user authentication is valid.
  This returns a channel"
  (go
    (require-auth current-user)
    (<? (require-not-blacklisted current-user))

    (let [network (shared-utils/uuid->network state)
          {:keys [:valid? :url :message] :as validation-result}
          (<? (verifiers/verify network (merge args {:user/address current-user})))]
      (when valid?
        (<? (stdb/remove-user-socials! {:social/url url}))  ; removes social network from any other users
        (<? (stdb/upsert-user-info! {:user/address current-user}))
        (<? (stdb/upsert-user-socials! [{:user/address current-user
                                       :social/network (name network)
                                       :social/url url
                                       :social/verified true}])))
        validation-result)))

(defn generate-twitter-oauth-url [current-user args]
  "Requests a twitter oauth URL"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (twitter/generate-twitter-oauth-url args)))

(defn- add-notification-types [current-user notification-type-input]
  (go
    (doall
      (for [{:keys [:user/address :notification/user-id :notification/type]}
            (map (fn [user-id]
                   {:user/address current-user
                    :notification/user-id user-id
                    :notification/type (-> notification-type-input :notification/type name keyword)})
                 (:notification/user-ids notification-type-input))]
        (<? (notifiers/store-id type address user-id))))))

(defn add-notification-type [current-user notification-type-input]
  "Adds notification details for a given user"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (<? (add-notification-types current-user notification-type-input))))

(defn- upload-photo [url-data user-addr photo-type config]
  (let [matcher (re-matches #"data:image/(\w+);base64,(.+)" url-data)
        filetype (get matcher 1)
        base64-image (get matcher 2)
        filename (str user-addr "-" (name photo-type) "." filetype)
        filepath (.join path (-> config :avatar-images :fs-path) filename)
        config-url (-> config :avatar-images :url-path)
        urlpath (str config-url (when-not (string/ends-with? config-url "/") "/") filename)]
    (if (and filetype base64-image)
      (try-catch-throw
        (fs/writeFileSync filepath base64-image #js {:encoding "base64"})
        urlpath)
      (throw (js/Error. "Invalid file encoded")))))

(defn- check-socials [socials]
  (let [invalid-socials (filter (fn [{:keys [:social/url :social/network] :as opts}]
                                  (and (not-empty url)
                                       (not (shared-utils/expected-root-domain? url
                                                                                ((keyword network) shared-utils/social-domains))) ))
                                socials)]
    (when (not-empty invalid-socials) (throw (str "invalid social links: "
                                         (map (fn [{:keys [:social/url :social/network]}]
                                                (str network ": " url)) invalid-socials))))))

(defn- check-user-urls [user]
  (let [invalid-urls (filter (fn [field]
                               (and (not-empty (field user))
                                    (not (shared-utils/valid-url? (field user)))))
                             [:user/url :user/perks])]
    (when (not-empty invalid-urls) (throw (str "invalid URLs: "
                                                  (map (fn [field]
                                                         (str (name field) ": " (field user))) invalid-urls))))))

(defn- check-content-url [url]
  (when (or (string/blank? url)
            (not (shared-utils/valid-url? url)))
    (throw (str "invalid URL: " url))))

(defn update-user-info! [current-user {:keys [:user/socials :user/perks :user/photo :user/bg-photo :user/notification-categories :user/notification-types] :as args} config]
  "Sets the user info"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (check-user-urls args)
    (check-socials socials)

    ;; TODO images come encoded in base64 directly in the request. They should come separated from the API
    (let [args (cond-> args
                       bg-photo (update :user/bg-photo upload-photo current-user :bg-photo config)
                       photo (update :user/photo upload-photo current-user :photo config))]
      (<? (stdb/upsert-user-info! (merge args {:user/address current-user})))
      (<? (stdb/set-user-timestamp! {:user-addresses [current-user]
                                     :timestamp/last-modification (shared-utils/now-secs)}))
      (when socials
        (let [[to-delete to-update] ((juxt filter remove) #(clojure.string/blank? (:social/url %)) socials)]
          (when (seq to-update) (<? (stdb/upsert-user-socials! (map #(merge % {:user/address current-user
                                                                           :social/verified false})
                                                                (filter #(not (contains?
                                                                                 #{:twitter :discord :eth}
                                                                                 (keyword (:social/network %))))
                                                                        to-update)))))
          (when (seq to-delete) (<? (stdb/remove-user-socials! {:user/address current-user :social/networks (map :social/network to-delete)})))))
      (when perks
        (if (str/blank? perks)
          (<? (stdb/remove-user-perks! {:user/address current-user}))
          (<? (stdb/upsert-user-perks! {:user/address current-user :user/perks perks}))))

      (when notification-categories
        (<? (stdb/upsert-notification-categories! (map #(merge {:user/address current-user} %) notification-categories))))
      (when notification-types
        (doall
          (for [notification-type-input notification-types]
            (<? (add-notification-types current-user notification-type-input))))))))

(defn get-notification-categories [current-user address]
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))
    (require-same-user current-user address)

    (<? (stdb/get-notification-categories {:user/address address}))))

(defn get-notification-types [current-user address]
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))
    (require-same-user current-user address)

    (let [notification-types (map #(-> %
                                       (merge {:notification/user-ids [(:notification/user-id %)]})
                                       (dissoc :notification/user-id))
                                  (<? (stdb/get-notification-types {:user/address address})))
          notification-types-many (->> (<? (stdb/get-notification-types-many {:user/address address}))
                                       (group-by :notification/type)
                                       vals
                                       (map (fn [user-ids]
                                              {:notification/type (:notification/type (first user-ids))
                                               :notification/user-ids (map :notification/user-id user-ids)})))]
      (concat notification-types notification-types-many))))

(defn validate-sign-in [current-user]
  "Validates a user can sign in. That is, if not blacklisted"
  (require-not-blacklisted current-user))

(defn request-grant! [current-user]
  "Request a grant for a user if she does not requested it already"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (<? (stdb/upsert-grants! {:user/addresses [current-user] :grant/status (name :grant.status/requested)}))))

(defn review-grants! [current-user {:keys [:user/addresses :grant/status] :as args}]
  "Approves or reject a grant request"
  (require-auth current-user)
  (go
    (<? (require-admin current-user))

    ; Grant approval needs to be done through smart contract, rejection from here
    (when-not (contains? #{(name :grant.status/rejected) (name :grant.status/unrequested) (name :grant.status/requested)} status)
      (throw (js/Error. (str "Invalid status: " status))))

    (let [grants (merge (select-keys args [:user/addresses :grant/status]) {:grant/decision-date (shared-utils/now-secs)})]
      (<? (stdb/upsert-grants! grants))
      (<? (notifiers/notify-grants-statuses grants)))))

(defn blacklisted? [_current-user user-address]
  "Checks if a user is currently blacklisted"
  (stdb/blacklisted? {:user/address user-address}))

(defn get-user-timestamps [current-user user-address]
  "Gets the timestamps of a user"
  (require-auth current-user)
  (go
    (<? (require-admin current-user))

    (<? (stdb/get-user-timestamps {:user/address user-address}))))

(defn has-private-content? [_current-user user-address]
  "Checks if a user is currently blacklisted"
  (stdb/has-private-content? {:user/address user-address}))

(defn unlocked? [current-user user-address]
  "Checks if a user is currently blacklisted"
  (go
    (if current-user
      (<? (stdb/has-permission? {:user/source-user current-user :user/target-user user-address}))
      false)))

(defn add-announcement! [current-user {:keys [:announcement/text] :as args}]
  "Adds an announcement to show to all users"
  (require-auth current-user)
  (go
    (<? (require-admin current-user))

    (<? (stdb/add-announcement! (select-keys args [:announcement/text])))
    (<? (notifiers/notify-announcement args))))

(defn remove-announcement! [current-user {:keys [:announcement/id] :as args}]
  "Removes an existing announcement"
  (require-auth current-user)
  (go
    (<? (require-admin current-user))

    (<? (stdb/remove-announcement! (select-keys args [:announcement/id])))))

(defn add-content! [current-user {:keys [:content/url :content/type :content/public :content/pinned] :as args}]
  "Adds (a link to) content for the logged in user"
  (require-auth current-user)
  (go
    (<? (require-grant-approved current-user))
    (<? (require-not-blacklisted current-user))

    (check-content-url url)

    (<? (stdb/add-content! (merge {:user/address current-user}
                                  (select-keys args [:content/type :content/url :content/public :content/pinned]))))

    (<? (notifiers/notify-new-content (stdb/get-user current-user)))))

(defn remove-content! [current-user {:keys [:content/id] :as args}]
  "Removes content of the logged in user"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (<? (stdb/remove-content! (merge {:user/address current-user} (select-keys args [:content/id]))))))

(defn set-content-visibility! [current-user {:keys [:content/id :content/public] :as args}]
  "Updates the visibility (public/private) of a given content"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (<? (stdb/set-content-visibility! (merge {:user/address current-user} (select-keys args [:content/id :content/public]))))))

(defn set-content-pinned! [current-user {:keys [:content/id :content/pinned] :as args}]
  "Set a content as pinned or not"
  (require-auth current-user)
  (go
    (<? (require-not-blacklisted current-user))

    (<? (stdb/set-content-pinned! (merge {:user/address current-user} (select-keys args [:content/id :content/pinned]))))))
