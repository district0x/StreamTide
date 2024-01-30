(ns streamtide.ui.feeds.page
  "Page showing highlighted content of different users in a single page"
  (:require
    [cljs-time.coerce :as tc]
    [cljs.core.match :refer-macros [match]]
    [cljsjs.timeago-react]
    [district.graphql-utils :as gql-utils]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.masonry :refer [infinite-scroll-masonry click-to-load-masonry]]
    [streamtide.ui.components.media-embed :as embed]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo avatar-placeholder]]
    [streamtide.ui.subs :as st-subs]))

(def timeago (r/adapt-react-class js/timeago_react))

(def page-size 6)

(defn build-feeds-content-query [after]
  [:search-contents
   (cond-> {:first page-size
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
             :content/creation-date
             [:content/user [:user/address
                             :user/name
                             :user/photo
                             :user/unlocked]]]]]])


(defn content-card [{:keys [:content/id :content/public :content/type :content/url :content/creation-date :content/user]}]
  (let [type (gql-utils/gql-name->kw type)
        {:keys [:user/address :user/name :user/photo :user/unlocked]} user
        nav (partial nav-anchor {:route :route.profile/index :params {:address address}})
        creation-date (-> creation-date gql-utils/gql-date->date tc/to-date)]
    [:div.content
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
        :default "")]
     [:div.contentFoot
      [:div.userProfile
       [nav
        [user-photo {:class (when unlocked "star") :src photo}]
        [:h3 name]]]
      [:div.date
       [timeago {:title (.toUTCString creation-date)
                 :datetime creation-date}]]]]))

(defmethod page :route.feeds/index []
  (let [active-account (subscribe [::accounts-subs/active-account])
        active-session? (subscribe [::st-subs/active-account-has-session?])]
    (fn []
      (let [user-content (subscribe [::gql/query {:queries [(build-feeds-content-query nil)]}
                                     {:id {:active-account @active-account :active-session? @active-session?}}])
            loading? (:graphql/loading? (first @user-content))
            all-content (->> @user-content
                             (mapcat (fn [r] (-> r :search-contents :items))))
            has-more? (-> (last @user-content) :search-contents :has-next-page)]
        [app-layout
         [:main.pageSite.pageFeeds
          {:id "feeds"}
          ;[:div.headerFeeds
          ; [:div.container
          ;  [:h1.titlePage "Feeds"]]]
          (if loading?
            [spinner/spin]
            [:div.container
             [:div.contentFeeds
              (if (and (empty? all-content)
                       (not loading?))
                [no-items-found {:message "No content found"}]
                [infinite-scroll-masonry {:class "medias"
                                          :loading? loading?
                                          :has-more? has-more?
                                          :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @user-content)))]
                                                      (dispatch [::graphql-events/query
                                                                 {:query {:queries [(build-feeds-content-query end-cursor)]}
                                                                  :id {:active-account @active-account :active-session? @active-session?}}]))
                                          :loading-spinner-delegate (fn [] [:div.spinner-container [spinner/spin]])}
                 (when-not (:graphql/loading? (first @user-content))
                   (doall
                     (for [{:keys [:content/id] :as content} all-content]
                       ^{:key id}
                       [content-card content])))])]])]
         [embed/safe-link-popup]]))))
