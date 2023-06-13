(ns streamtide.server.business-logic
  "Layer implementing the server business logic.
  This is an intermediate layer between the GraphQL endpoint (or any other API which may come in the future)
  and the database functionality to enforce authorization and to enrich or validate input data."
  (:require [cljs.core.async :refer [go <!]]
            [cljs.nodejs :as nodejs]
            [clojure.string :as string]
            [district.shared.async-helpers :refer [safe-go]]
            [district.shared.error-handling :refer [try-catch-throw]]
            [fs]
            [streamtide.server.db :as stdb]
            [streamtide.server.verifiers.twitter-verifier :as twitter]
            [streamtide.server.verifiers.verifiers :as verifiers]
            [streamtide.shared.utils :as shared-utils]))

(def path (nodejs/require "path"))

(defn require-auth [current-user]
  "Check if the request comes from an authenticated user. Throws an error otherwise"
  (when-not current-user
    (throw (js/Error. "Authentication required"))))

(defn get-roles [current-user]
  "Gets the roles of the user sending the request"
  (require-auth current-user)

  (map #(keyword :role (:role/role %)) (stdb/get-roles current-user)))

(defn require-role [current-user role]
  "Checks the user performing the request has the given role. Throws an error otherwise"
  (when-not (contains? (set (get-roles current-user)) role)
    (throw (js/Error. "Unauthorized"))))

(defn require-admin [current-user]
  "Checks the user performing the request is an administrator (i.e., has the 'admin' role). Throws an error otherwise"
  (require-role current-user :role/admin))

(defn require-not-blacklisted [current-user]
  "Checks the user performing the request is not blacklisted. Throws an error otherwise"
  (when (stdb/blacklisted? {:user/address current-user})
    (throw (js/Error. "Unauthorized - address blacklisted"))))

(defn require-grant-approved [current-user]
  "Checks the grant of the user performing the request has been already approved. Throws an error otherwise"
  (when-not (= (name :grant.status/approved) (:grant/status (stdb/get-grant current-user)))
    (throw (js/Error. "Unauthorized - require approved grant to perform operation"))))

(defn get-user [_current-user address]
  "Gets user data from its ETH address"
  (stdb/get-user address))

(defn get-user-socials [_current-user address]
  "Gets the social links of a user from its address"
  (stdb/get-user-socials address))

(defn get-grant [_current-user user-address]
  "Gets the grant info of a given user"
  (stdb/get-grant user-address))

(defn get-round [_current-user round-id]
  "Gets the info of a given round"
  (stdb/get-round round-id))

(defn get-grants [_current-user args]
  "Gets all the grants"
  (stdb/get-grants args))

(defn get-users [_current-user args]
  "Gets all users"
  (stdb/get-users args))

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

(defn verify-social! [current-user {:keys [:state] :as args}]
  "Verify a social network, for example checking the authentication code coming from user authentication is valid.
  This returns a channel"
  (require-auth current-user)
  (require-not-blacklisted current-user)

  (safe-go
    (let [network (shared-utils/uuid->network state)
          {:keys [:valid? :url]} (<! (verifiers/verify network (merge args
                                                                      {:user/address current-user})))]
      (when valid?
        (stdb/remove-user-socials! {:social/url url})  ; removes social network from any other users
        (stdb/upsert-user-info! {:user/address current-user})
        (stdb/upsert-user-socials! [{:user/address current-user
                                     :social/network (name network)
                                     :social/url url
                                     :social/verified true}]))
      valid?)))

(defn generate-twitter-oauth-url [current-user args]
  "Requests a twitter oauth URL"
  (require-auth current-user)
  (require-not-blacklisted current-user)

  (twitter/generate-twitter-oauth-url args))

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

(defn update-user-info! [current-user {:keys [:user/socials :user/photo :user/bg-photo] :as args} config]
  "Sets the user info"
  (require-auth current-user)
  (require-not-blacklisted current-user)

  ;; TODO images come encoded in base64 directly in the request. They should come separated from the API
  (let [args (cond-> args
                     bg-photo (update :user/bg-photo upload-photo current-user :bg-photo config)
                     photo (update :user/photo upload-photo current-user :photo config))]
    (stdb/upsert-user-info! (merge args {:user/address current-user}))
    (when socials
      (let [[to-delete to-update] ((juxt filter remove) #(clojure.string/blank? (:social/url %)) socials)]
        (when (seq to-update) (stdb/upsert-user-socials! (map #(merge % {:user/address current-user
                                                                         :social/verified false})
                                                              (filter #(not (contains?
                                                                               #{:twitter :discord :eth}
                                                                               (keyword (:social/network %))))
                                                                      to-update))))
        (when (seq to-delete) (stdb/remove-user-socials! {:user/address current-user :social/networks (map :social/network to-delete)}))))))

(defn validate-sign-in [current-user]
  "Validates a user can sign in. That is, if not blacklisted"
  (require-not-blacklisted current-user))

(defn request-grant! [current-user]
  "Request a grant for a user if she does not requested it already"
  (require-auth current-user)
  (require-not-blacklisted current-user)

  (stdb/upsert-grants! {:user/addresses [current-user] :grant/status (name :grant.status/requested)}))

(defn review-grants! [current-user {:keys [:user/addresses :grant/status] :as args}]
  "Approves or reject a grant request"
  (require-auth current-user)
  (require-admin current-user)

  ; TODO check grant status is not nil and valid value
  ;(when (contains? grant-statuses decision))
  (stdb/upsert-grants! (merge (select-keys args [:user/addresses :grant/status]) {:grant/decision-date (shared-utils/now-secs)})))

(defn blacklist! [current-user {:keys [:user/address :blacklist] :as args}]
  "blacklists/whitelist a user"
  (require-auth current-user)
  (require-admin current-user)

  (if blacklist
    (stdb/add-to-blacklist! (select-keys args [:user/address]))
    (stdb/remove-from-blacklist! (select-keys args [:user/address]))))

(defn blacklisted? [_current-user user-address]
  "Checks if a user is currently blacklisted"
  (stdb/blacklisted? {:user/address user-address}))

(defn add-announcement! [current-user {:keys [:announcement/text] :as args}]
  "Adds an announcement to show to all users"
  (require-auth current-user)
  (require-admin current-user)

  (stdb/add-announcement! (select-keys args [:announcement/text])))

(defn remove-announcement! [current-user {:keys [:announcement/id] :as args}]
  "Removes an existing announcement"
  (require-auth current-user)
  (require-admin current-user)

  (stdb/remove-announcement! (select-keys args [:announcement/id])))

(defn add-content! [current-user {:keys [:content/url :content/type :content/public] :as args}]
  "Adds (a link to) content for the logged in user"
  (require-auth current-user)
  (require-grant-approved current-user)
  (require-not-blacklisted current-user)

  (stdb/add-content! (merge {:user/address current-user} (select-keys args [:content/type :content/url :content/public]))))

(defn remove-content! [current-user {:keys [:content/id] :as args}]
  "Removes content of the logged in user"
  (require-auth current-user)
  (require-not-blacklisted current-user)

  (stdb/remove-content! (merge {:user/address current-user} (select-keys args [:content/id]))))

(defn set-content-visibility! [current-user {:keys [:content/id :content/public] :as args}]
  "Updates the visibility (public/private) of a given content"
  (require-auth current-user)
  (require-not-blacklisted current-user)

  (stdb/set-content-visibility! (merge {:user/address current-user} (select-keys args [:content/id :content/public]))))
