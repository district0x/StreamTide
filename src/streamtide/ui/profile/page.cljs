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
    [streamtide.ui.components.masonry :refer [infinite-scroll-masonry click-to-load-masonry]]
    [streamtide.ui.components.media-embed :as embed]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo-profile social-links avatar-placeholder]]
    [streamtide.ui.profile.events :as p-events]
    [streamtide.ui.subs :as st-subs]))

(def page-size 6)

(defn build-user-info-query [{:keys [:user/address :active-session]}]
  [:user
   {:user/address address}
   (cond-> [:user/name
            :user/description
            :user/tagline
            :user/handle
            :user/url
            :user/photo
            :user/bg-photo
            :user/blacklisted
            :user/has-private-content
            [:user/socials [:social/network
                            :social/url]]
            [:user/grant [:grant/status]]]
           active-session (conj :user/perks :user/unlocked))])

(defn build-user-content-query [{:keys [:user/address :pinned]} after]
  [:search-contents
   (cond-> {:first page-size
            :user/address address
            :order-by :contents.order-by/creation-date
            :order-dir :desc}
           after (assoc :after after)
           (some? pinned) (assoc :content/pinned pinned))
   [:total-count
    :end-cursor
    :has-next-page
    [:items [:content/id
             :content/public
             :content/type
             :content/url
             :content/creation-date]]]])

(defn user-header [{:keys [:user/socials :user/handle :user/url :user/tagline :user/name :user/photo :user/bg-photo :user/unlocked] :as user-info}]
  [:div.headerProfile
   [:div.bgProfile
    [:img {:src bg-photo}]]
   [:div.contentHeaderProfile
    [user-photo-profile (merge {:src photo} (when unlocked {:class "star"}))]
    [:div.dataProfile
     [:h1 name]
     [:p tagline]
     [:div.links
      [:span handle]
      (when (and (not (blank? handle) ) (not (blank? url))) [:span "|"])
      [embed/safe-external-link url]]
     [social-links {:socials socials}]]]])

(defn content-card [{:keys [:content/id :content/public :content/type :content/url :content/creation-date]}]
  (let [type (gql-utils/gql-name->kw type)]
    [:div.media
     {:class (case type
               :content-type/image "photo"
               :content-type/video "video"
               :content-type/audio "audio"
               :content-type/other "other"
               "")}
     (case type
       :content-type/image [embed/embed-image url]
       :content-type/video [embed/embed-video url]
       :content-type/audio [embed/embed-audio url]
       :content-type/other [embed/embed-other url]
       :default "")]))

(defn contents []
  (let []
    (fn [user-account user-info]
      (let [active-account @(subscribe [::accounts-subs/active-account])
            active-session? @(subscribe [::st-subs/active-account-has-session?])
            user-content-pinned (subscribe [::gql/query {:queries [(build-user-content-query {:user/address user-account :pinned true} nil)]}
                                     {:id {:user-content user-account :active-account active-account :active-session? active-session? :pinned true}}])
            user-content-unpinned (subscribe [::gql/query {:queries [(build-user-content-query {:user/address user-account :pinned false} nil)]}
                                     {:id {:user-content user-account :active-account active-account :active-session? active-session? :pinned false}}])
            loading-pinned? (:graphql/loading? (last @user-content-pinned))
            loading-unpinned? (:graphql/loading? (last @user-content-unpinned))
            all-content-pinned (->> @user-content-pinned
                          (mapcat (fn [r] (-> r :search-contents :items))))
            all-content-unpinned (->> @user-content-unpinned
                          (mapcat (fn [r] (-> r :search-contents :items))))
            has-more-pinned? (-> (last @user-content-pinned) :search-contents :has-next-page)
            has-more-unpinned? (-> (last @user-content-unpinned) :search-contents :has-next-page)
            hidden-content? (and (:user/has-private-content user-info) (not (:user/unlocked user-info)))]
         (if (and (empty? all-content-unpinned)
                  (empty? all-content-pinned)
                  (not loading-unpinned?)
                  (not loading-pinned?)
                  (not hidden-content?))
           [no-items-found {:message "No content found"}]
           [:<>
            (when (not-empty all-content-pinned)
              [:div.pinned-container
               {:class (when has-more-pinned? "has-more")}
               [click-to-load-masonry {:class "medias pinned"
                                       :loading? loading-pinned?
                                       :has-more? has-more-pinned?
                                       :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @user-content-pinned)))]
                                                   (dispatch [::graphql-events/query
                                                              {:query {:queries [(build-user-content-query {:user/address user-account :pinned true} end-cursor)]}
                                                               :id {:user-content user-account :active-account active-account :pinned true}}]))
                                       :loading-spinner-delegate (fn [] [:div.spinner-container [spinner/spin]])
                                       :load-more-content [:img]}
                (when-not (:graphql/loading? (first @user-content-pinned))
                  (doall
                    (for [{:keys [:content/id] :as content} all-content-pinned]
                      ^{:key id}
                      [content-card content])))]])
            (when hidden-content?
              [:span.additional-content
               {:on-click #(dispatch [::p-events/add-to-cart {:user/address user-account}])}
               "Unlock additional content by supporting this creator"])
            [infinite-scroll-masonry {:class "medias unpinned"
                                      :loading? loading-unpinned?
                                      :has-more? has-more-unpinned?
                                      :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @user-content-unpinned)))]
                                                  (dispatch [::graphql-events/query
                                                             {:query {:queries [(build-user-content-query {:user/address user-account :pinned false} end-cursor)]}
                                                              :id {:user-content user-account :active-account active-account :pinned false}}]))
                                      :loading-spinner-delegate (fn [] [:div.spinner-container [spinner/spin]])}
             (when-not (:graphql/loading? (first @user-content-unpinned))
               (doall
                 (for [{:keys [:content/id] :as content} all-content-unpinned]
                   ^{:key id}
                   [content-card content])))]])))))

(defmethod page :route.profile/index []
  (let [active-account (subscribe [::accounts-subs/active-account])
        active-page-sub (re-frame/subscribe [::router-subs/active-page])
        url-account (-> @active-page-sub :params :address)
        user-account (match [(nil? url-account) (nil? @active-account)]
                            [true _] @active-account
                            [false _] url-account
                            [true true] nil)]
    (fn []
      (when user-account
        (let [active-session (subscribe [::st-subs/active-session])
              user-info-query (subscribe [::gql/query {:queries [(build-user-info-query {:user/address user-account :active-session @active-session})]}
                                          {:id {:user-content user-account :active-account @active-account :active-session @active-session}}])
              loading? (or (nil? user-info-query) (:graphql/loading? (last @user-info-query)))
              user-info (:user (last @user-info-query))]
          [app-layout
           [:main.pageSite.pageProfile
            {:id "profile"}
            (if loading?
              [spinner/spin]
              [:div.container
               (if (and user-info (not (:user/blacklisted user-info)))
                 [:<>
                  [user-header user-info]
                  [:div.contentProfile
                   [support-seal]
                   (when (not (blank? (:user/description user-info)))
                     [:div.aboutProfile
                      [:h2 "A Little About Me"]
                      [:p (:user/description user-info)]])
                   [:div.btsProfile
                    (when (and (= (-> user-info :user/grant :grant/status gql-utils/gql-name->kw) :grant.status/approved)
                               (not= @active-account user-account))
                      [:button.btBasic.btBasic-light {:on-click #(dispatch [::p-events/add-to-cart {:user/address user-account}])}
                       "SUPPORT THIS CREATOR"])
                    (when (not (blank? (:user/perks user-info)))
                      [embed/safe-external-link (:user/perks user-info) {:class "btBasic btBasic-light" :text "PERKS"} ])]
                   [contents user-account user-info]]]
                 [:div.not-found "User Not Found"])])]
           [embed/safe-link-popup user-account]])))))
