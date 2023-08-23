(ns streamtide.ui.components.grant-approved-notification
  (:require [district.graphql-utils :as gql-utils]
            [district.ui.graphql.subs :as gql]
            [district.ui.router.subs :as router-subs]
            [district.ui.web3-accounts.subs :as accounts-subs]
            [re-frame.core :refer [dispatch subscribe]]
            [streamtide.ui.components.general :refer [nav-anchor]]
            [streamtide.ui.my-content.events :as mc-events]
            [streamtide.ui.subs :as st-subs]
            [streamtide.ui.utils :refer [build-grant-status-query]]))

(defn build-user-has-content-query [{:keys [:user/address]}]
  [:search-contents
   {:first 1
    :user/address address}
   [:total-count]])

(defn grant-approved-notification [{:keys [:class]}]
  (let [active-page (subscribe [::router-subs/active-page])
        active-session? (subscribe [::st-subs/active-account-has-session?])
        active-account (subscribe [::accounts-subs/active-account])]
    (fn [{:keys [:class]}]
      (when (and @active-account @active-session? (not= (:name @active-page) :route.my-content/index))
        (let [grant-status-query (subscribe [::gql/query {:queries [(build-grant-status-query {:user/address @active-account})]}])
              grant-status (-> @grant-status-query :grant :grant/status gql-utils/gql-name->kw)]
          (when (= grant-status :grant.status/approved)
            (let [user-has-content-query (subscribe [::gql/query {:queries [(build-user-has-content-query {:user/address @active-account})]}
                                                     {:refetch-on [::mc-events/upload-content-success]}])
                  user-has-content? (-> @user-has-content-query :search-contents :total-count (> 0))]
              (when-not user-has-content?
                [:div.add-content-notification
                 (when class {:class class})
                 [:div.notification-message
                  "Your Grant has been approved. You can now"]
                 [nav-anchor {:route :route.my-content/index} "Add Content"]]))))))))
