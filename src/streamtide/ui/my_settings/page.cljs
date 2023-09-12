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
    [district.ui.router.events :as router-events]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [reagent.ratom :refer [reaction]]
    [streamtide.shared.utils :refer [valid-url? expected-root-domain? social-domains]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.error-notification :as error-notification]
    [streamtide.ui.components.general :refer [no-items-found support-seal discord-invite-link]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [avatar-placeholder]]
    [streamtide.ui.my-settings.events :as ms-events]
    [streamtide.ui.my-settings.subs :as ms-subs]
    [streamtide.ui.utils :refer [switch-popup from-wei check-session build-grant-status-query]]
    [taoensso.timbre :as log]))

(def page-size 6)

(def max-filesize 3145728)

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
                      (swap! form-data #(assoc-by-path (with-meta % {:touched? true}) value-id "")))}
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

(defn- profile-picture-edit [{:keys [:form-data :form-values :id :errors]}]
  [file-drag-input {:form-data form-data
                    :group-class "photo"
                    :id id
                    :img-attributes {:src (or (-> form-values id :selected-file :url-data) avatar-placeholder)}
                    :label nil
                    :accept "image/*"
                    :errors errors
                    :file-accept-pred (fn [{:keys [name type size] :as props}]
                                        (and (#{"image/png" "image/gif" "image/jpeg" "image/svg+xml"} type)
                                             (< size max-filesize)))
                    :on-file-accepted (fn [{:keys [name type size array-buffer] :as props}]
                                        (swap! form-data update-in [id] dissoc :error)
                                        (reset! form-data (with-meta @form-data {:touched? true}))
                                        (log/info "Accepted file" {:name name :type type :size size} ::file-accepted))
                    :on-file-rejected (fn [{:keys [name type size] :as props}]
                                        (swap! form-data assoc id {:error (if (>= size max-filesize)
                                                                            "File too large (> 3MB)"
                                                                            "Non .png .jpeg .gif .svg file selected")})
                                        (reset! form-data (with-meta @form-data {:touched? true}))
                                        (log/warn "Rejected file" {:name name :type type :size size} ::file-rejected))}])

(defn- grant-info [grant-status show-popup-fn errors]
  (let []
  [:div.apply
  [:h2.titleEdit "Apply to be a creator"]
 ;We need a way remove the denied state so they can apply again, or a new queue in admin that sends all denied accounts to a single feed that can be searched to approve accounts
  [:p "If you apply to be a verified creator, you will gain access to patronage tools and grant matching during an upcoming grant matching round, amplifying the impact of contributions you receive during the event. Please note that approval is manual, so please be patient while you wait. Join our "[:a {:href "https://discord.gg/VUP4b7djEN" :target "_blank" :style {:color "Goldenrod"}} "Discord "] "and open a support ticket if you don't receive a notification about your status via email or Discord."]
  (case grant-status
    :grant.status/unrequested [:button.btBasic.btBasic-light.btApply {:on-click #(show-popup-fn % true)
                                                                      :disabled (not-empty (-> @errors :local))} "APPLY FOR A GRANT"]
    :grant.status/requested [:div.requested "Your Grant has been requested and is pending for approval 🚀 Join our "[:a {:href "https://discord.gg/VUP4b7djEN" :target "_blank" :style {:color "Goldenrod"}} "Discord "] "while you wait for a response"]
    :grant.status/approved [:div.approved "Your Grant has been approved 🚀"]
    :grant.status/rejected [:div.rejected "Your Grant has been rejected. To appeal this decision please join our "[:a {:href "https://discord.gg/VUP4b7djEN" :target "_blank" :style {:color "Goldenrod"}} "Discord "] "and open a support ticket."]
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
           [:p "This software is in early access. By applying for a grant through Stream Tide, you agree to not hold Stream Tide or its affiliates liable for any claims, losses, or damages arising from the grant application process or the use of granted funds. You acknowledge that you are participating and using this open source software at your own risk and that Stream Tide makes no warranties or guarantees regarding the grant program."]
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
           [:a {:href discord-invite-link :target :_blank :rel "noopener noreferrer"} "district0x server"]]
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
                                    :placeholder "https://pinterest.com/..."})]
          [social-link-edit (merge input-params
                                   {:id [:socials :patreon]
                                    :icon-src "/img/layout/ico_patreon.svg"
                                    :placeholder "https://patreon.com/..."})]]]))))

(defn clean-form-data [form-data form-values initial-values]
  (try
    (cond-> form-values
            true (select-keys (keys (filter (fn [[key val]]
                                              (or (= key :name)
                                                  (not= (key initial-values) val))) form-values)))
            (:socials @form-data) (update :socials socials-kw->gql)
            (and (:photo @form-data) (-> @form-data :photo :error)) (dissoc :photo)
            (and (:photo @form-data) (-> @form-data :photo :error not)) (update :photo photo->gql)
            (and (:bg-photo @form-data) (-> @form-data :bg-photo :error)) (dissoc :bg-photo)
            (and (:bg-photo @form-data) (-> @form-data :bg-photo :error not)) (update :bg-photo photo->gql)
            (:min-donation @form-data) (update :min-donation #(if (empty? %) "0" (web3/to-wei % :ether))))
    (catch :default e
      (dispatch [::error-notification/show-error "Invalid data" e])
      (throw e))))

(defn- some-invalid-url?
  ([url]
   (some-invalid-url? url nil))
  ([url domains]
  (and (not-empty url)
       (if (some? domains) (not (expected-root-domain? url domains))
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
                                         (some-invalid-url? (-> @form-data :socials :patreon :url) (:patreon social-domains))
                                         (assoc-in [:socials :patreon :url] "URL not valid")

                                         (-> @form-data :photo :error)
                                         (assoc :photo (-> @form-data :photo :error))
                                         (-> @form-data :bg-photo :error)
                                         (assoc :bg-photo (-> @form-data :bg-photo :error)))})]
    (fn []
      (check-session)
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
                                      :form-values form-values
                                      :errors errors}]]
              [:div.photoProfile
               [profile-picture-edit {:form-data form-data
                                      :id :photo
                                      :form-values form-values
                                      :errors errors}]]]
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
                 {:title "Summarize here what you do"}
                 [:span "Tagline"]
                 [initializable-text-input
                  (merge input-params
                         {:id :tagline
                          :placeholder "Summarize what you do here"})]]]
               [:div.block
                [:label.inputField
                 {:title "Your username in social networks"}
                 [:span "Handle"]
                 [initializable-text-input
                  (merge input-params
                         {:id :handle
                          :placeholder "@username"})]]
                [:label.inputField
                 {:title "Enter your personal or project website"}
                 [:span "URL"]
                 [initializable-text-input
                  (merge input-params
                         {:id :url
                          :type :url
                          :placeholder "https://your-website.io"})]]]]
              [:div.biography
               {:title "Write something about you"}
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
                  [:p "Donations smaller to this amount will not unlock your 'supporter only' content"]

                  [:label.inputField
                   [:span "ETH"]
                   [initializable-text-input
                    (merge input-params
                           {:id :min-donation})]]]
                 [:div.perks
                  [:h2 "Perks Button URL"]
                  [:p "Add a redemption link for your supporters to claim when they support you. This could be anything from a swag discount code, Web3 redemtpion link, private streams, etc."]

                  [:label.inputField
                   [:span "URL"]
                   [initializable-text-input
                    (merge input-params
                           {:id :perks
                            :type :url})]]]])]]]
           [:div.submitField
            [:button.btBasic.btBasic-light.btSubmit
             {:type "submit"
              :disabled (or (empty? @form-data) (not (:touched? (meta @form-data))) (not-empty (-> @errors :local)))
              :on-click #(dispatch [::ms-events/save-settings
                                    {:form-data (clean-form-data form-data form-values initial-values)
                                     :on-success (fn []
                                                   (reset! form-data (with-meta @form-data {:touched? false}))
                                                   (dispatch [::router-events/navigate :route.profile/index
                                                              {:address @active-account}]))}])}
             "SAVE CHANGES"]]]]
         [popup-request-grant grant-popup-open? show-grant-popup-fn #(clean-form-data form-data form-values initial-values)]]))))
