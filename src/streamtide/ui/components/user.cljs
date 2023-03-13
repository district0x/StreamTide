(ns streamtide.ui.components.user
  "Components to show user-related stuff")

(defn user-photo [{:keys [:class :src]}]
  [:div.user
   {:class class}
   [:div.photo
    [:img {:src src}]]])

(defn user-photo-profile [{:keys [:src]} & children]
  [:div.photoProfile
   children
   [:div.photo
    [:img {:src src}]]])

(defn social-links [{:keys [:class :socials]}]
  [:div.socialLinks
   {:class class}
   (doall (map-indexed
            (fn [idx {:keys [:social/network :social/url]}]
              [:a {:key (str idx) :href url :target "_blank"}
               [:img {:class "normal" :src (str "/img/layout/social-" network ".svg")}]
               [:img {:class "hover" :src (str "/img/layout/social-" network "-hover.svg")}]])
            socials))])
