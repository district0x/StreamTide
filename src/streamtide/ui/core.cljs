(ns streamtide.ui.core
  "Main entry point of the Frontend.
  Loads the config and load all required modules.
  It also load the content of the persistent storage into the re-frame db"
  (:require [akiroz.re-frame.storage :as storage]
            [cljsjs.jquery]
            [cljsjs.jwt-decode]
            [district.cljs-utils :as cljs-utils]
            [district.shared.error-handling :refer [try-catch]]
            [district.ui.component.router :refer [router]]
            [district.ui.graphql.events :as gql-events]
            [district.ui.graphql]
            [district.ui.notification]
            [district.ui.reagent-render]
            [district.ui.router]
            [district.ui.router.events :as router-events]
            [district.ui.smart-contracts]
            [district.ui.web3-accounts.events :as web3-accounts-events]
            [district.ui.web3-accounts.queries :as web3-accounts-queries]
            [district.ui.web3-accounts]
            [district.ui.web3-chain]
            [district.ui.web3-chain.events :as web3-chain-events]
            [district.ui.web3-tx.events :as tx-events]
            [district.ui.web3-tx-id]
            [district.ui.web3]
            [district.ui.web3.events :as web3-events]
            [district.ui.window-size]
            [mount.core :as mount]
            [re-frame.core :as re-frame]
            [streamtide.shared.graphql-schema :refer [graphql-schema]]
            [streamtide.shared.utils :as shared-utils]
            [streamtide.ui.about.page]
            [streamtide.ui.admin.announcements.page]
            [streamtide.ui.admin.black-listing.page]
            [streamtide.ui.admin.grant-approval-feed.page]
            [streamtide.ui.admin.round.page]
            [streamtide.ui.admin.rounds.page]
            [streamtide.ui.config :as config]
            [streamtide.ui.config :refer [config-map]]
            [streamtide.ui.effects]
            [streamtide.ui.events :as st-events]
            [streamtide.ui.feeds.page]
            [streamtide.ui.grants.page]
            [streamtide.ui.home.page]
            [streamtide.ui.leaderboard.page]
            [streamtide.ui.my-content.page]
            [streamtide.ui.my-settings.page]
            [streamtide.ui.oauth-callback.verifier]
            [streamtide.ui.profile.page]
            [streamtide.ui.routes :refer [routes]]
            [streamtide.ui.send-support.page]
            [taoensso.timbre :as log]))

(def interceptors [re-frame/trim-v])

(storage/reg-co-fx! :my-app         ;; local storage key
                    {:fx :store     ;; re-frame fx ID
                     :cofx :store}) ;; re-frame cofx ID

(defn dev-setup []
  (when (:debug? @config/config)
    (enable-console-print!)))


(re-frame/reg-event-fx
  ::set-graphql-auth
  ; if the current account has an active session, it sets the JWT for GrahpQL
  interceptors
  (fn [{:keys [:db]} [[_ {:keys [:old]}]]]
    (let [{:keys [:user/address :jwt]} (-> db :active-session)
          jwt (when (and (some? address)
                         (= address (web3-accounts-queries/active-account db)))
                jwt)]
      {:dispatch-n [[::gql-events/set-authorization-token jwt]
                    (when (not-empty old) [::router-events/navigate :route/home])]})))

(re-frame/reg-event-fx
  ::close-mobile-menu
  ; Closes the mobile menu, if open, when navigating to another page
  interceptors
  (fn [{:keys [:db]}]
    (when (:menu-mobile-open? db)
      {:dispatch [::st-events/menu-mobile-switch]})))

(defn check-session [session]
  (try-catch
    (let [jwt (:jwt session)
          expire (when jwt (:exp (js->clj (js/jwt_decode jwt) :keywordize-keys true)))]
      (when (and expire
                 (> expire (+ (shared-utils/now-secs) 7200))) ;; when jwt is about to expire we don't use it
      session))))

(re-frame/reg-event-db
  ::init-defaults
  ; Loads into the re-frame DB the entries persisted in the browser local store.
  interceptors
  (fn [db [store]]
    (-> db
        (assoc :day-night-switch (:day-night-switch store))
        (assoc :active-session (check-session (:active-session store)))
        (assoc :cart (:cart store))
        (assoc :trust-domains (:trust-domains store))
        (assoc :multipliers (:multipliers store))
        (assoc :donations (:donations store))
        (assoc :my-settings (:my-settings store)))))

(re-frame/reg-event-fx
  ::init
  ; on init, it loads the browser local store into the re-frame db
  [(re-frame/inject-cofx :store)]
  (fn [{:keys [db store]}]
    {:dispatch [::init-defaults store]
     ; when the account is changed, it sets the GraphQL JWT associated the current account, if any
     :forward-events [{:register    :accounts-changed
                       :events      #{::web3-accounts-events/accounts-changed}
                       :dispatch-to [::set-graphql-auth]}
                      {:register :on-navigate
                       :events #{::router-events/navigate}
                       :dispatch-to [::close-mobile-menu]}
                      {:register :web3-created
                       :events #{::web3-events/web3-created}
                       :dispatch-to [::st-events/web3-created]}
                      {:register :web3-creation-failed
                       :events #{::web3-events/web3-creation-failed}
                       :dispatch-to [::st-events/web3-creation-failed]}
                      {:register :chain-changed
                       :events #{::web3-chain-events/chain-changed}
                       :dispatch-to [::st-events/web3-chain-changed]}
                      {:register :send-tx-started
                       :events #{::tx-events/send-tx}
                       :dispatch-to [::st-events/send-tx-started]}
                      {:register :send-tx-finished
                       :events #{::tx-events/tx-hash-error ::tx-events/tx-success ::tx-events/tx-error}
                       :dispatch-to [::st-events/send-tx-finished]}]}))

(defn ^:export init []
  (dev-setup)
  (let [full-config (cljs-utils/merge-in
                      config-map
                      {:web3 {:authorize-on-init? false
                              :connect-on-init? false}
                       :web3-accounts {:eip55? true
                                       :disable-loading-at-start? true
                                       :disable-polling? true}
                       :smart-contracts {:format :truffle-json
                                         :contracts-path "/contracts/build/"}
                       :web3-tx {:disable-loading-recommended-gas-prices? true}
                       :web3-tx-log {:open-on-tx-hash? true}
                       :reagent-render {:id "app"
                                        :component-var #'router}
                       :router {:routes routes
                                :default-route :route/home
                                :scroll-top? true
                                :html5? true}
                       :notification {:default-show-duration 10000
                                      :default-hide-duration 1000}
                       :graphql {:disable-default-middlewares? true
                                 :schema graphql-schema}})]
    (js/console.log "Entire config:" (clj->js full-config))
    (-> (mount/with-args full-config)
        (mount/start)))
  (re-frame/dispatch-sync [::init]))
