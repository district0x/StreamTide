(ns streamtide.ui.my-content.page
  ; Page to manage user content
  (:require
    [cljs-time.coerce :as tc]
    [district.graphql-utils :as gql-utils]
    [district.ui.component.form.input :refer [text-input get-by-path assoc-by-path file-drag-input checkbox-input radio-group]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [no-items-found support-seal nav-anchor]]
    [streamtide.ui.components.infinite-scroll-masonry :refer [infinite-scroll-masonry]]
    [streamtide.ui.components.media-embed :as embed]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.my-content.events :as mc-events]
    [streamtide.ui.my-content.subs :as mc-subs]
    [streamtide.ui.utils :refer [switch-popup]]))


(def page-size 6)

(defn build-grant-status-query [{:keys [:user/address]}]
  [:grant
   {:user/address address}
   [:grant/status]])

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

(defn content-card [{:keys [:content/id :content/type :content/url :content/public]}]
  (let [type (gql-utils/gql-name->kw type)
        form-data (r/atom {:public public})
        hide (r/atom false)]
    (fn []
      (when-not @hide
        (let [setting-visibility? (subscribe [::mc-subs/setting-visibility? id])
              removing? (subscribe [::mc-subs/removing-content? id])
              removed? (subscribe [::mc-subs/removed-content? id])]
        [:div.yourContent
         {:class (cond-> (case type
                           :content-type/image "photo"
                           :content-type/video "video"
                           :content-type/audio "audio"
                           :content-type/other "other"
                           "")
                         @removing? (str " removing")
                         @removed? (str " remove"))
          :on-transition-end (fn [e]
                               (when (clojure.string/includes? (-> e .-target .-className) "remove")
                                 (reset! hide true)
                                 (dispatch [::mc-events/content-removed])))}
         [:div.header
          [:label.checkField.checkPublicPrivate
           {:class (when @setting-visibility? "loading")}
           [checkbox-input {:form-data form-data
                            :id :public
                            :on-change #(dispatch [::mc-events/set-visibility {:content/id     id
                                                                               :content/public (:public @form-data)
                                                                               :on-error       (fn [] (swap! form-data (fn [f]
                                                                                                                   (update f :public not))))}])}]
           [:span.checkmark
            {:class (when-not (:public @form-data) "checked")}]
           [:span.public "Free to the public"]
           [:span.private "For Supporters Only"]
           ]
          [:div.buttons
           ;[:button.btPin]
           ;[:hr]
           [:button.btRemove {:on-click #(dispatch [::mc-events/remove-content {:content/id id
                                                                                ;:on-success [::ms-events/increase-removed-count removed-content-count]
                                                                                }])}]]]
         [:div.content
          (case type
            :content-type/image [embed/embed-image url]
            :content-type/video [embed/embed-video url]
            :content-type/audio [embed/embed-audio url]
            :content-type/other [embed/embed-other url]
            :default "")]])))))

(defn contents []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (when @active-account
    (fn []
      (let [user-content (when @active-account (subscribe [::gql/query {:queries [(build-user-content-query {:user/address @active-account} nil)]}
                                                           {:id :user-content
                                                            :refetch-on [::mc-events/content-added ::mc-events/content-removed]}]))
            loading? (or (nil? user-content) (:graphql/loading? (last @user-content)))
            all-content (when @active-account (->> @user-content
                             (mapcat (fn [r] (-> r :search-contents :items)))
                                                   distinct
                                                   (sort-by #(tc/to-long (:content/creation-date %)))
                                                   reverse))
            has-more? (when @active-account (-> (last @user-content) :search-contents :has-next-page))]
        [:div.containerContents
        (if (and (empty? all-content)
                 (not loading?))
          [no-items-found]
          [infinite-scroll-masonry {:class "yourContents"
                                    :loading? loading?
                                    :has-more? has-more?
                                    :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @user-content)))]
                                                 (dispatch [::graphql-events/query
                                                            {:query {:queries [(build-user-content-query {:user/address @active-account} end-cursor)]}
                                                             :id :user-content
                                                             :refetch-on [::mc-events/content-added ::mc-events/content-removed]}]))
                                    :loading-spinner-delegate (fn [] [:div.spinner-container [spinner/spin]])}
             (when @active-account
               (doall
                 (for [{:keys [:content/id] :as content} all-content]
                   ^{:key id}
                   [content-card content]
                   )))])])))))


(defn popup-add-content [add-content-popup-open? show-add-content-popup-fn]
  (let [init-form {:type :image}
        form-data (r/atom init-form)
        close-popup (fn [ev]
                      (show-add-content-popup-fn ev false)
                      (reset! form-data init-form))]
    (fn []
      (let [loading? (subscribe [::mc-subs/uploading-content?])]
        [:div {:id "popUpAddContent" :style {"display" (if @add-content-popup-open? "flex" "none")}}
         [:div.bgPopUp {:on-click #(close-popup %)}]
         [:div.popUpContent.add
          [:button.btClose {:on-click #(close-popup %)} "Close" ]
          [:div.content
           [:div.form
            [:div.block.block-1
             (when @loading?
               [spinner/spin])
             [:div.input-wrapper
              [:span.titleEdit "Add a Link"]
              [:div.type
               [:label.radio-group-label "type:"]
               [radio-group {:id :type :form-data form-data :options [{:key :image :label "image"}
                                                                      {:key :video :label "video"}
                                                                      {:key :audio :label "audio"}
                                                                      {:key :other :label "other"}]}]]
              [:div.uploadContent
               [:label {:for "addLink"}
                [text-input (merge {:form-data form-data
                                    :type :url
                                    :id :url
                                    :dom-id "addLink"
                                    :placeholder "https://youtu.be/a1b2c3d4e5f"
                                    :class "inputField"}
                                   (when @loading?
                                     {:disabled true}))]]
               [:button.btBasic.btBasic-light
                {:class (when @loading? "loading")
                 :on-click #(dispatch [::mc-events/upload-content
                                       (merge @form-data
                                              {:on-success (fn []
                                                             (close-popup nil)
                                                             (dispatch [::mc-events/content-added]))})])}
                "ADD LINK"]]]
             ]]]]]))))

(defmethod page :route.my-content/index []
  (let [active-account (subscribe [::accounts-subs/active-account])
        add-content-popup-open? (r/atom false)
        show-add-content-popup-fn (fn [e show]
                                    (when e (.stopPropagation e))
                                (switch-popup add-content-popup-open? show))]
    (fn []
      (let [grant-status-query (when @active-account (subscribe [::gql/query {:queries [(build-grant-status-query {:user/address @active-account})]}
                                                                 {:refetch-on [::mc-events/request-grant-success]}]))
            loading? (or (nil? grant-status-query) (:graphql/loading? @grant-status-query))
            grant-status (when grant-status-query (-> @grant-status-query :grant :grant/status gql-utils/gql-name->kw))]
        [app-layout
         [:main.pageSite.pageContent.pageEditContent
          {:id "content"}
          [:div.headerContent
           [:div.container
            [:h1.titlePage "Manage Content"]]]
          (if loading?
            [spinner/spin]
            [:div.sectionYourContent
             [:div.container
             (if (= :grant.status/approved grant-status)
               [:<>
                [:button.btBasic.btBasic-light.btAdd
                 {:on-click #(show-add-content-popup-fn % true)}
                 "ADD CONTENT"]
                [contents]]
               [:div.grantPending
                [:p "To upload content you first need to get a Grant."]
                [:p [nav-anchor {:route :route.my-settings/index} "Update your Profile and Apply for a Grant to start sharing your content"]]])]])]
         [popup-add-content add-content-popup-open? show-add-content-popup-fn]]))))
