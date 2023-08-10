(ns streamtide.ui.my-settings.page
  ; Page to edit the user profile
  (:require
    [cljs-web3-next.core :as web3]
    [clojure.string :as string]
    [district.graphql-utils :as gql-utils]
    [district.ui.component.form.input :refer [text-input get-by-path assoc-by-path file-drag-input checkbox-input radio-group]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [district.ui.notification.events :as notification-events]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [reagent.ratom :refer [reaction]]
    [streamtide.shared.utils :refer [valid-url? expected-domain? social-domains]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [no-items-found support-seal]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [avatar-placeholder]]
    [streamtide.ui.my-settings.events :as ms-events]
    [streamtide.ui.my-settings.subs :as ms-subs]
    [streamtide.ui.utils :refer [switch-popup from-wei]]))

(def page-size 6)

(defn build-user-settings-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [:user/name
    :user/description
    :user/tagline
    :user/handle
    :user/url
    :user/min-donation
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


(defn initializable-text-input
  [{:keys [:form-values :id] :as opts}]
  [text-input (merge {:value (get-by-path form-values id)}
                     (apply dissoc opts [:form-values]))])

(defn social-link-edit [{:keys [:id :form-data :form-values :icon-src :verifiable? :errors :placeholder]}]
  (let [value-id (conj id :url)
        verified? (get-by-path form-values (conj id :verified))
        network (last id)]
    [:div.social
     [:div.header
      [:div.icon
       [:img.icon {:src icon-src}]]
      (if verified?
        [:div.verified
         {:title "verified"}])
      (when (and verifiable? (not verified?))
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
      (when-not (string/blank? (:url (get-by-path form-values id)))
        [:button.btRemove
         {:on-click (fn []
                      (swap! form-data assoc-by-path value-id ""))}
         "REMOVE"])]
     [:label.editField
      (when verifiable? {:class "verifiable"})
      [:span "URL"]
      [initializable-text-input
       (merge {:form-data form-data
               :form-values form-values
               :id value-id
               :type :url
               :errors errors
               :placeholder placeholder}
              (when verifiable?
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

(defn- parse-min-donation [entries]
  (update entries :min-donation #(if % (from-wei %) "0")))

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
                    :img-attributes {:src (or (-> form-values id :selected-file :url-data) avatar-placeholder)}
                    :label nil
                    :accept "image/*"
                    :file-accept-pred (fn [{:keys [name type size] :as props}]
                                        (and (#{"image/png" "image/gif" "image/jpeg" "image/svg+xml"} type)
                                             (< size 1500000)))}])

(defn- grant-info [grant-status show-popup-fn errors]
  (let []
  [:div.apply
  [:h2.titleEdit "Apply for a Grant"]
  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."]
  (case grant-status
    :grant.status/unrequested [:button.btBasic.btBasic-light.btApply {:on-click #(show-popup-fn % true)
                                                                      :disabled (not-empty (-> @errors :local))} "APPLY FOR A GRANT"]
    :grant.status/requested [:div.requested "Your Grant has been requested and is pending for approval"]
    :grant.status/approved [:div.approved "Your Grant has been approved"]
    :grant.status/rejected [:div.rejected "Your Grant has been rejected"]
    [:div.spinner-container [spinner/spin]])]))


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
                                                                 {:form-data (settings-form-data)
                                                                  :on-success
                                                                  (fn [] (close-popup nil))}])}]]]]]))))


(defn social-links []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (fn [{:keys [:form-data :errors]}]
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
                          :form-data form-data
                          :errors errors}]
        [:<>
         [:div.socialAccounts
          [:h2.titleEdit "Connected accounts"]
          [:p "Connect your social accounts to verify your identity and get the most out of Streamtide."]
          [:div.verifier-hint "Verify you have a valid Twitter account"]
          [social-link-edit (merge input-params
                                   {:id [:socials :twitter]
                                    :icon-src "/img/layout/ico_twitter.svg"
                                    :verifiable? true})]
          [:div.verifier-hint "Verify you have a valid Discord account connected to "
           [:a {:href "https://discord.com/invite/sS2AWYm" :target :_blank :rel "noopener noreferrer"} "district0x server"]]
          [social-link-edit (merge input-params
                                   {:id [:socials :discord]
                                    :icon-src "/img/layout/ico_discord.svg"
                                    :verifiable? true})]
          [:div.verifier-hint "Verify your wallet holds some ETH on any of the main networks"]
          [social-link-edit (merge input-params
                                   {:id [:socials :eth]
                                    :icon-src "/img/layout/ico_eth.svg"
                                    :verifiable? true})]]
         [:div.socialAccounts
          [:h2.titleEdit "Social Links"]
          [social-link-edit (merge input-params
                                   {:id [:socials :facebook]
                                    :icon-src "/img/layout/ico_facebook.svg"
                                    :placeholder "https://facebook.com/..."})]
          [social-link-edit (merge input-params
                                   {:id [:socials :linkedin]
                                    :icon-src "/img/layout/ico_linkedin.svg"
                                    :placeholder "https://linkedin.com/..."})]
          [social-link-edit (merge input-params
                                   {:id [:socials :instagram]
                                    :icon-src "/img/layout/ico_instagram.svg"
                                    :placeholder "https://instagram.com/..."})]
          [social-link-edit (merge input-params
                                   {:id [:socials :pinterest]
                                    :icon-src "/img/layout/ico_pinterest.svg"
                                    :placeholder "https://pinterest.com/..."})]]]))))

(defn clean-form-data [form-data form-values initial-values]
  (try
    (cond-> form-values
            true (select-keys (keys (filter (fn [[key val]]
                                              (or (= key :name)
                                                  (not= (key initial-values) val))) form-values)))
            (:socials @form-data) (update :socials socials-kw->gql)
            (:photo @form-data) (update :photo photo->gql)
            (:bg-photo @form-data) (update :bg-photo photo->gql)
            (:min-donation @form-data) (update :min-donation #(if (empty? %) "0" (web3/to-wei % :ether))))
    (catch :default e
      (dispatch [::notification-events/show "[ERROR] Invalid data"])
      (throw e))))

(defn- some-invalid-url?
  ([url]
   (some-invalid-url? url nil))
  ([url domains]
  (and (not-empty url)
       (if (some? domains) (not (expected-domain? url domains))
                           (not (valid-url? url))))))

(defmethod page :route.my-settings/index []
  (let [active-account (subscribe [::accounts-subs/active-account])
        grant-popup-open? (r/atom false)
        show-grant-popup-fn (fn [e show]
                              (when e (.stopPropagation e))
                     (switch-popup grant-popup-open? show))
        form-data (r/atom {})
        errors (reaction {:local (cond-> {}
                                         (and (:min-donation @form-data)
                                              (not (re-matches #"^\d+(\.\d{0,18})?$" (:min-donation @form-data))))
                                         (assoc :min-donation "Amount not valid")

                                         (some-invalid-url? (:url @form-data))
                                         (assoc :url "URL not valid")
                                         (some-invalid-url? (:perks @form-data))
                                         (assoc :perks "URL not valid")
                                         (some-invalid-url? (-> @form-data :socials :facebook :url) (:facebook social-domains))
                                         (assoc-in [:socials :facebook :url] "URL not valid")
                                         (some-invalid-url? (-> @form-data :socials :instagram :url) (:instagram social-domains))
                                         (assoc-in [:socials :instagram :url] "URL not valid")
                                         (some-invalid-url? (-> @form-data :socials :linkedin :url) (:linkedin social-domains))
                                         (assoc-in [:socials :linkedin :url] "URL not valid")
                                         (some-invalid-url? (-> @form-data :socials :pinterest :url) (:pinterest social-domains))
                                         (assoc-in [:socials :pinterest :url] "URL not valid")
                                         )})]
    (fn []
      (let [user-settings (when @active-account (subscribe [::gql/query {:queries [(build-user-settings-query {:user/address @active-account})]}]))
            grant-status-query (when @active-account (subscribe [::gql/query {:queries [(build-grant-status-query {:user/address @active-account})]}
                                                            {:refetch-on [::ms-events/request-grant-success]}]))
            grant-status (when grant-status-query (-> @grant-status-query :grant :grant/status gql-utils/gql-name->kw))
            loading? (or (nil? user-settings) (:graphql/loading? (last @user-settings)))
            initial-values (when user-settings (-> @user-settings
                                                   :user
                                                   remove-ns
                                                   (select-keys [:name :description :tagline :handle :url :photo :bg-photo :perks :min-donation])
                                                   select-photos
                                                   parse-min-donation))
            form-values (merge-with #(if (map? %2) (merge %1 %2) %2) initial-values @form-data)
            input-params {:read-only loading?
                          :form-values form-values
                          :form-data form-data
                          :errors errors}]
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
                         {:id :tagline})]]]
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
                         {:id :url
                          :type :url})]]]]
              [:div.biography
               [:h2.titleEdit "Biography"]
               [:div.textField
                [initializable-text-input
                 (merge input-params
                        {:id :description
                         :input-type :textarea})]]]
              [social-links input-params]
              [:hr.lineProfileEdit]
              [grant-info grant-status show-grant-popup-fn errors]
              (when (= grant-status :grant.status/approved)
                [:<>
                 [:div.min-donation
                  [:h2 "Minimum donation amount"]
                  [:p "Donations smaller to this amount will not unlock your restricted content"]

                  [:label.inputField
                   [:span "ETH"]
                   [initializable-text-input
                    (merge input-params
                           {:id :min-donation})]]]
                 [:div.perks
                  [:h2 "Perks Button URL"]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit."]

                  [:label.inputField
                   [:span "URL"]
                   [initializable-text-input
                    (merge input-params
                           {:id :perks
                            :type :url})]]]])]]]
             [:div.submitField
            [:hr]
            [:button.btBasic.btBasic-light.btSubmit
             {:type "submit"
              :disabled (or (empty? @form-data) (not-empty (-> @errors :local)))
              :on-click #(dispatch [::ms-events/save-settings
                                    {:form-data (clean-form-data form-data form-values initial-values)}])}
             "SAVE CHANGES"]]]]
         [popup-request-grant grant-popup-open? show-grant-popup-fn #(clean-form-data form-data form-values initial-values)]]))))
