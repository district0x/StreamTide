(ns streamtide.ui.profile.page
  "Page showing the details of a given (or current) user.
  It shows the users profile info, their content and allows making donations"
  (:require
    [cljs.core.match :refer-macros [match]]
    [clojure.string :refer [blank?]]
    [district.graphql-utils :as gql-utils]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.subs :as router-subs]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [no-items-found support-seal]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.media-embed :as embed]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo-profile social-links]]
    [streamtide.ui.profile.events :as p-events]))

(def page-size 6)


;; TODO maybe move this to some common ns
(defn build-user-info-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [:user/name
    :user/description
    :user/tagline
    :user/handle
    :user/url
    :user/perks
    :user/photo
    :user/bg-photo
    [:user/socials [:social/network
                    :social/url]]
    [:user/grant [:grant/status]]]])

(defn build-user-content-query [{:keys [:user/address]} after]
  [:search-contents
   (cond-> {:first page-size
            :user/address address
            :order-by :contents.order-by/creation-date
            :order-dir :desc}
           after (assoc :after after))
   [:total-count
    :end-cursor
    :has-next-page
    [:items [:content/id
             :content/public
             :content/type
             :content/url
             :content/creation-date]]]])

(defn user-header [{:keys [:user/socials :user/handle :user/url :user/tagline :user/name :user/photo :user/bg-photo] :as user-info}]
  [:div.headerProfile
   [:div.bgProfile
    [:img {:src bg-photo}]]
   [:div.contentHeaderProfile
    [user-photo-profile {:src photo}]
    [:div.dataProfile
     [:h1 name]
     [:p tagline]
     [:div.links
      [:span handle]
      (when (and (not (blank? handle) ) (not (blank? url))) [:span "|"])
      [:a {:src url :target "_blank" } url ]]
     [social-links {:socials socials}]]]])

(defn content-card [{:keys [:content/id :content/public :content/type :content/url :content/creation-date]}]
  (let [type (gql-utils/gql-name->kw type)]
    [:div.midia
     {:class (case type
               :content-type/image "photo"
               :content-type/video "video"
               "")}
     (case type
       :content-type/image [:img {:src url}]
       :content-type/video [embed/embed-video url]
       :default "")]))

(defn contents [user-account]
  (let []
    (fn [user-account]
      (let [active-account @(subscribe [::accounts-subs/active-account])
            user-content (subscribe [::gql/query {:queries [(build-user-content-query {:user/address user-account} nil)]}
                                     {:id {:user-content user-account :active-account active-account}}])
            loading? (:graphql/loading? (last @user-content))
            all-content (->> @user-content
                          (mapcat (fn [r] (-> r :search-contents :items))))
            has-more? (-> (last @user-content) :search-contents :has-next-page)]
        ;[:div.midias
        ; {:id "midias"}
         (if (and (empty? all-content)
                  (not loading?))
           [no-items-found]
           [infinite-scroll {:class "midias"
                             :fire-tutorial-next-on-items? true
                             :element-height 439
                             :loading? loading?
                             :has-more? has-more?
                             :loading-spinner-delegate (fn []
                                                         [:div.spinner-container [spinner/spin]])
                             :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @user-content)))]
                                         (dispatch [::graphql-events/query
                                                    {:query {:queries [(build-user-content-query {:user/address user-account} end-cursor)]}
                                                     :id {:user-content user-account :active-account active-account}}]))}
            (when-not (:graphql/loading? (first @user-content))
              (doall
                (for [{:keys [:content/id] :as content} all-content]
                  ^{:key id}
                  [content-card content])))])))))

(defmethod page :route.profile/index []
  (let [active-account @(subscribe [::accounts-subs/active-account])
        active-page-sub (re-frame/subscribe [::router-subs/active-page])
        url-account (-> @active-page-sub :params :address)
        user-account (match [(nil? url-account) (nil? active-account)]
                            [true _] active-account
                            [false _] url-account
                            [true true] nil)]
    (fn []
      (when user-account
        (let [user-info-query (subscribe [::gql/query {:queries [(build-user-info-query {:user/address user-account})]}])
              loading? (or (nil? user-info-query) (:graphql/loading? (last @user-info-query)))
              user-info (:user @user-info-query)]
          [app-layout
           [:main.pageSite.pageProfile
            {:id "profile"}
            [:div.container
             [user-header user-info]
             [:div.contentProfile
              [support-seal]
              [:div.aboutProfile
               [:h2 "A Little About Me"]
               [:p (:user/description user-info)]]
              [:div.btsProfile
               (when (and (= (-> user-info :user/grant :grant/status gql-utils/gql-name->kw) :grant.status/approved)
                          (not= active-account user-account))
                 [:a.btBasic.btBasic-light {:on-click #(dispatch [::p-events/add-to-cart {:user/address user-account}])}
                  "DONATE / SUPPORT"])
               (when (not (blank? (:user/perks user-info)))
                 [:a.btBasic.btBasic-light {:href (:user/perks user-info) :target "_blank"} "PERKS"])]
              [contents user-account]
              ]
             ;[:div.page-load-status]
             ]]])))))
