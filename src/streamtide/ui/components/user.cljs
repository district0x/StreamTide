(ns streamtide.ui.components.user
  "Components to show user-related stuff")

(def avatar-placeholder "/img/avatar/profile_placeholder.svg")

(defn user-photo [{:keys [:class :src]}]
  [:div.user
   {:class class}
   [:div.photo
    [:img {:src (or src avatar-placeholder)}]]])

(defn user-photo-profile [{:keys [:src :class]} & children]
  [:div.photoProfile
   children
   [:div.photo
    {:class class}
    [:img {:src (or src avatar-placeholder)}]]])

(defn social-links [{:keys [:class :socials]}]
  [:div.socialAccounts
   {:class class}
   (doall (map-indexed
            (fn [idx {:keys [:social/network :social/url]}]
              [:a {:key (str idx) :href url :target "_blank"}
               [:img {:class "normal" :src (str "/img/layout/social-ico-" network ".svg")}]
               [:img {:class "hover" :src (str "/img/layout/social-ico-" network "-hover.svg")}]])
            socials))])
