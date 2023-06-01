(ns streamtide.ui.my-settings.page
  ; Page to edit the user profile or upload content
  (:require
    [cljs-time.coerce :as tc]
    [cljsjs.tinymce-react]
    [district.graphql-utils :as gql-utils]
    [district.ui.component.form.input :refer [text-input get-by-path assoc-by-path file-drag-input checkbox-input radio-group]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [no-items-found support-seal]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.media-embed :as embed]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.my-settings.events :as ms-events]
    [streamtide.ui.my-settings.subs :as ms-subs]
    [streamtide.ui.utils :refer [switch-popup]]))


; TODO maybe use a simpler editor. A textarea should be good enough
(def editor (r/adapt-react-class js/editor.Editor))

(def page-size 6)

(defn build-user-settings-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [:user/name
    :user/description
    :user/tagline
    :user/handle
    :user/url
    :user/perks
    :user/photo
    :user/bg-photo]])

(defn build-user-socials-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [[:user/socials [:social/network
                    :social/url
                    :social/verified]]]])

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


(defn initializable-text-input
  [{:keys [:form-values :id] :as opts}]
  [text-input (merge {:value (get-by-path form-values id)}
                     (apply dissoc opts [:form-values]))])

(defn social-link-edit [{:keys [:id :form-data :form-values :icon-src :read-only]}]
  (let [value-id (conj id :url)
        verified? (get-by-path form-values (conj id :verified))
        network (last id)]
    [:div.social
     [:div.header
      [:div.icon
       [:img.icon {:src icon-src}]]
      (if verified?
        [:div.verified
         {:title "verified"}]
        [:button.btVerify
         {:on-click #(dispatch [::ms-events/verify-social
                                {:social/network network
                                 :on-success (fn []
                                               (swap! form-data (fn [data]
                                                                  (let [path (butlast id)]
                                                                    (if (get-in data path)
                                                                      (update-in data path dissoc network)
                                                                      data)))))}])}
         "VERIFY"])
      (when-not read-only
        [:button.btRemove
         {:on-click (fn []
                      (swap! form-data assoc-by-path value-id ""))}
         "REMOVE"])]
     [:label.editField
      [:span "URL"]
      [initializable-text-input
       (merge {:form-data form-data
               :form-values form-values
               :id value-id
               :type :url}
              (when read-only
                {:disabled true}))]]]))


(defn- remove-ns [entries]
  (reduce-kv (fn [m k v]
               (assoc m (keyword (name k)) v))
             {} entries ))

(defn- socials-gql->kw [socials]
  (reduce (fn [socials social]
            (let [network (:social/network social)
                  url (:social/url social)
                  verified (:social/verified social)]
              (assoc socials (keyword network) {:url url :verified verified})))
          {}
          socials))

(defn- socials-kw->gql [socials]
  (reduce-kv (fn [socials k v]
               (conj socials (gql-utils/clj->gql {:social/network (name k)
                                                  :social/url (:url v)})))
          []
          socials))

(defn- photo->gql [photo]
  (-> photo :selected-file :url-data))

(defn- select-photos [entries]
  (cond-> entries
          (:bg-photo entries)
          (update :bg-photo (fn [val] {:selected-file {:url-data val}}))

          (:photo entries)
          (update :photo (fn [val] {:selected-file {:url-data val}}))))

(defn- profile-picture-edit [{:keys [:form-data :form-values :id]}]
  [file-drag-input {:form-data form-data
                    :group-class "photo"
                    :id id
                    :img-attributes {:src (-> form-values id :selected-file :url-data)}
                    :label nil
                    :accept "image/*"
                    :file-accept-pred (fn [{:keys [name type size] :as props}]
                                        (and (#{"image/png" "image/gif" "image/jpeg" "image/svg+xml"} type)
                                             (< size 1500000)))}])

(defn- grant-info [grant-status show-popup-fn]
  (let []
  [:div.apply
  [:h2.titleEdit "Apply for a Grant"]
  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."]
  (case grant-status
    :grant.status/unrequested [:button.btBasic.btBasic-light.btApply {:on-click #(show-popup-fn % true)} "APPLY FOR A GRANT"]
    :grant.status/requested [:div.requested "Your Grant has been requested and is pending for approval"]
    :grant.status/approved [:div.approved "Your Grant has been approved"]
    :grant.status/rejected [:div.rejected "Your Grant has been rejected"]
    [:div.spinner-container [spinner/spin]])]))

(defn content-card [{:keys [:content/id :content/type :content/url :content/public]}]
  (let [type (gql-utils/gql-name->kw type)
        form-data (r/atom {:public public})
        hide (r/atom false)]
    (fn []
      (when-not @hide
        (let [setting-visibility? (subscribe [::ms-subs/setting-visibility? id])
              removing? (subscribe [::ms-subs/removing-content? id])
              removed? (subscribe [::ms-subs/removed-content? id])]
        [:div.yourContent
         {:class (cond-> (case type
                           :content-type/image "photo"
                           :content-type/video "video"
                           "")
                         @removing? (str " removing")
                         @removed? (str " remove"))
          :on-transition-end (fn [e]
                               (when (clojure.string/includes? (-> e .-target .-className) "remove")
                                 (reset! hide true)
                                 (dispatch [::ms-events/content-removed])))}
         [:div.header
          [:label.checkField.checkPublicPrivate
           {:class (when @setting-visibility? "loading")}
           [checkbox-input {:form-data form-data
                            :id :public
                            :on-change #(dispatch [::ms-events/set-visibility {:content/id id
                                                                               :content/public (:public @form-data)
                                                                               :on-error (fn [] (swap! form-data (fn [f]
                                                                                                                   (update f :public not))))}])}]
           [:span.checkmark
            {:class (when-not (:public @form-data) "checked")}]
           [:span.public "Free to the public"]
           [:span.private "For Supporters Only"]
           ]
          [:div.buttons
           ;[:button.btPin]
           ;[:hr]
           [:button.btRemove {:on-click #(dispatch [::ms-events/remove-content {:content/id id
                                                                                ;:on-success [::ms-events/increase-removed-count removed-content-count]
                                                                                }])}]]]
         [:div.content
          (case type
            :content-type/image [:img {:src url}]
            :content-type/video [embed/embed-video url]
            :default "")]])))))

(defn contents []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (when @active-account
    (fn []
      (let [user-content (when @active-account (subscribe [::gql/query {:queries [(build-user-content-query {:user/address @active-account} nil)]}
                                                           {:id :user-content
                                                            :refetch-on [::ms-events/content-added ::ms-events/content-removed]}]))
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
          [infinite-scroll {:class "yourContents"
                            ;:dom-id "yourContents"
                            :fire-tutorial-next-on-items? true
                            :element-height 439
                            :loading? loading?
                            :has-more? has-more?
                            :loading-spinner-delegate (fn []
                                                        [:div.spinner-container [spinner/spin]])
                            :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @user-content)))]
                                        (dispatch [::graphql-events/query
                                                   {:query {:queries [(build-user-content-query {:user/address @active-account} end-cursor)]}
                                                    :id :user-content
                                                    :refetch-on [::ms-events/content-added ::ms-events/content-removed]}]))}
           (when @active-account
             (doall
               (for [{:keys [:content/id] :as content} all-content]
                 ^{:key id}
                 [content-card content]
                 )))])])))))

(defn popup-request-grant [grant-popup-open? show-grant-popup-fn settings-form-data]
  (let [form-data (r/atom {})
        close-popup (fn [ev]
                      (show-grant-popup-fn ev false)
                      (reset! form-data {}))]
    (fn [_ _ settings-form-data]
      (let [checked? (:agreed @form-data)
            loading? @(subscribe [::ms-subs/requesting-grant?])]
        [:div {:id "popUpGrant" :style {"display" (if @grant-popup-open? "flex" "none")}}
         [:div.bgPopUp {:on-click #(close-popup %)}]
         [:div.popUpGrant
          [:button.btClose {:on-click #(close-popup %)} "Close" ]
          [:div.content
           [:h3 "Apply for a Grant"]
           [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."]
           [:div.form
            (when loading? [spinner/spin])
            [:label.checkField.simple
             [checkbox-input {:id :agreed
                              :form-data form-data}]
             [:span.checkmark {:class (when checked? "checked")}]
             [:span.text "I agree to the terms of service"]]
            [:input.btBasic.btBasic-light {:disabled (or loading? (not checked?))
                                           :type "submit"
                                           :value "SUBMIT FOR APPROVAL"
                                           :on-click #(dispatch [::ms-events/save-and-request-grant
                                                                 {:form-data settings-form-data
                                                                  :on-success
                                                                  (fn [] (close-popup nil))}])}]]]]]))))

(defn popup-add-content [add-content-popup-open? show-add-content-popup-fn]
  (let [init-form {:type :image}
        form-data (r/atom init-form)
        close-popup (fn [ev]
                      (show-add-content-popup-fn ev false)
                      (reset! form-data init-form))]
    (fn []
      (let [loading? (subscribe [::ms-subs/uploading-content?])]
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
                                                                      {:key :video :label "video"}]}]]
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
                 :on-click #(dispatch [::ms-events/upload-content
                                       (merge @form-data
                                              {:on-success (fn []
                                                             (close-popup nil)
                                                             (dispatch [::ms-events/content-added]))})])}
                "ADD LINK"]]]
             ]]]]]))))

(defn social-links []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (fn [{:keys [:form-data]}]
      (let [user-socials-query (when @active-account (subscribe [::gql/query {:queries [(build-user-socials-query {:user/address @active-account})]}
                                                                 {:refetch-on [::ms-events/verify-social-success]}]))
            loading? (or (nil? user-socials-query) (:graphql/loading? (last @user-socials-query)))
            initial-values (when user-socials-query (-> @user-socials-query
                                                   :user
                                                   (update :user/socials socials-gql->kw)
                                                   remove-ns
                                                   (select-keys [:socials])))
            form-values (merge-with #(if (map? %2) (merge %1 %2) %2) initial-values @form-data)
            input-params {:read-only loading?
                          :form-values form-values
                          :form-data form-data}]
        [:div.socialLinks
         [:h2.titleEdit "Social Links"]
         [social-link-edit (merge input-params
                                  {:id [:socials :facebook]
                                   :icon-src "/img/layout/ico_facebook.svg"})]
         [social-link-edit (merge input-params
                                  {:id [:socials :twitter]
                                   :icon-src "/img/layout/ico_twitter.svg"})]
         [social-link-edit (merge input-params
                                  {:id [:socials :linkedin]
                                   :icon-src "/img/layout/ico_linkedin.svg"})]
         [social-link-edit (merge input-params
                                  {:id [:socials :instagram]
                                   :icon-src "/img/layout/ico_instagram.svg"})]
         [social-link-edit (merge input-params
                                  {:id [:socials :pinterest]
                                   :icon-src "/img/layout/ico_pinterest.svg"})]
         [social-link-edit (merge input-params
                                  {:id [:socials :discord]
                                   :icon-src "/img/layout/ico_discord.svg"})]
         [social-link-edit (merge input-params
                                  {:id [:socials :eth]
                                   :icon-src "/img/layout/ico_eth.svg"
                                   :read-only true})]]))))

(defn clean-form-data [form-data form-values initial-values]
  (cond-> form-values
          true (select-keys (keys (filter (fn [[key val]]
                                            (or (= key :name)
                                                (not= (key initial-values) val))) form-values)))
          (:socials @form-data) (update :socials socials-kw->gql)
          (:photo @form-data) (update :photo photo->gql)
          (:bg-photo @form-data) (update :bg-photo photo->gql)))

(defmethod page :route.my-settings/index []
  (let [active-account (subscribe [::accounts-subs/active-account])
        grant-popup-open? (r/atom false)
        add-content-popup-open? (r/atom false)
        show-grant-popup-fn (fn [e show]
                              (when e (.stopPropagation e))
                     (switch-popup grant-popup-open? show))
        show-add-content-popup-fn (fn [e show]
                                    (when e (.stopPropagation e))
                                (switch-popup add-content-popup-open? show))
        form-data (r/atom {})]
    (fn []
      (let [user-settings (when @active-account (subscribe [::gql/query {:queries [(build-user-settings-query {:user/address @active-account})]}]))
            grant-status-query (when @active-account (subscribe [::gql/query {:queries [(build-grant-status-query {:user/address @active-account})]}
                                                            {:refetch-on [::ms-events/request-grant-success]}]))
            grant-status (when grant-status-query (-> @grant-status-query :grant :grant/status gql-utils/gql-name->kw))
            loading? (or (nil? user-settings) (:graphql/loading? (last @user-settings)))
            initial-values (when user-settings (-> @user-settings
                                                   :user
                                                   remove-ns
                                                   (select-keys [:name :description :tagline :handle :url :photo :bg-photo :perks])
                                                   select-photos))
            form-values (merge-with #(if (map? %2) (merge %1 %2) %2) initial-values @form-data)
            input-params {:read-only loading?
                          :form-values form-values
                          :form-data form-data}]
        [app-layout
         [:main.pageSite.pageProfile.pageEditProfile
          {:id "profile"}
          [:div.form
           [:div.headerGrants
            [:div.container
             [:h1.titlePage "Edit Profile"]]]
           [:div.container
            [:div.containerEdit
             [support-seal]
             [:div.headerProfile
              [:div.bgProfile
               [profile-picture-edit {:form-data form-data
                                      :id :bg-photo
                                      :form-values form-values}]]
              [:div.photoProfile
               [profile-picture-edit {:form-data form-data
                                      :id :photo
                                      :form-values form-values}]
               ;[:button.btEdit.btEditPhoto "Edit Photo"]
               ;[:div.photo
               ; [:img {:src "/img/sample/profile-fpo.jpg"}]]
               ]]
             [:hr]
             [:div.contentEditProfile
              [:div.generalInfo
               [:h2.titleEdit "General Info"]
               [:div.block
                [:label.inputField
                 [:span "Name"]
                 [initializable-text-input
                  (merge input-params
                    {:id :name})]]
                [:label.inputField
                 [:span "Tagline"]
                 [initializable-text-input
                  (merge input-params
                         {:id :tag-line})]]]
               [:div.block
                [:label.inputField
                 [:span "Handle"]
                 [initializable-text-input
                  (merge input-params
                         {:id :handle})]]
                [:label.inputField
                 [:span "URL"]
                 [initializable-text-input
                  (merge input-params
                         {:id :tag-line
                          :type :url})]]]]
              [:div.biography
               [:h2.titleEdit "Biography"]
               [:div.textField
                [editor {:id "biography"
                         :disabled loading?
                         :value (:description form-values)
                         :onEditorChange (fn [value]
                                           (swap! form-data assoc :description value))}]]]
              [social-links input-params]
              [:hr.lineProfileEdit]
              [grant-info grant-status show-grant-popup-fn]
              (when (= grant-status :grant.status/approved)
              [:div.perks
               [:h2 "Perks Button URL"]
               [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit."]
               ;[:input.inputField {:type "url" :defaultValue "TODO"}]
               [initializable-text-input
                (merge input-params
                       {:class "inputField"
                        :id :perks
                        :type :url})]

               ])
              ]]]
           [:div.submitField
            [:hr]
            [:button.btBasic.btBasic-light.btSubmit
             {:type "submit"
              :disabled (empty? @form-data)
              :on-click #(dispatch [::ms-events/save-settings
                                    {:form-data (clean-form-data form-data form-values initial-values)}])}
             "SAVE CHANGES"]]]
          (when (= :grant.status/approved grant-status)
            [:div.sectionYourContent
           [:div.container
            [:div.headerGrants
             [:div.container
              [:h2.titlePage]]]
            [:button.btBasic.btBasic-light.btAdd
             {:on-click #(show-add-content-popup-fn % true)}
             "ADD CONTENT"]
            [contents]
           ]])]
         [popup-request-grant grant-popup-open? show-grant-popup-fn (clean-form-data form-data form-values initial-values)]
         [popup-add-content add-content-popup-open? show-add-content-popup-fn]]))))
