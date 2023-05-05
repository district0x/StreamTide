(ns streamtide.ui.admin.announcements.page
  "Page to manage the announcements which appear in the top of the page"
  (:require
    [cljsjs.tinymce-react]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.admin.announcements.events :as a-events]
    [streamtide.ui.admin.announcements.subs :as a-subs]
    [streamtide.ui.components.admin-layout :refer [admin-layout]]
    [streamtide.ui.components.general :refer [no-items-found]]))

; TODO currently admins can create many announcements but users would only see the first one.
; Maybe we would allow enabling or disabling the announcement without having to delete them, or even give some validity period

; TODO maybe use a simpler editor. A textarea should be good enough
(def editor (r/adapt-react-class js/editor.Editor))

;; TODO maybe move this to a common ns?
(defn build-announcements-query []
  (let []
    [:announcements
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:announcement/id
               :announcement/text]]]]))

(defn announcement-entry [{:keys [:announcement/text :announcement/id]}]
  (let [removing? (subscribe [::a-subs/removing-announcement? id])]
    [:div.announcement
     [:p text]
     [:button.btRemove
      {:on-click #(dispatch [::a-events/remove-announcement {:id id}])
       :disabled @removing?
       :class (when @removing? "removing")}
      "Remove"]]))

(defn announcement-entries [announcements-search]
  (let [all-announcements (-> @announcements-search :announcements :items)
        loading? (:graphql/loading? (last @announcements-search))]
    (if (and (empty? all-announcements)
             (not loading?))
      [no-items-found]
      [:div {:id "announcements"
             :class "announcements"}
       (when-not (:graphql/loading? (first @announcements-search))
         (doall
           (for [announcement all-announcements]
             ^{:key (:announcement/id announcement)} [announcement-entry announcement])))])))

(defmethod page :route.admin/announcements []
  (let [form-data (r/atom {:announcement ""})
        announcements-search (subscribe [::gql/query {:queries [(build-announcements-query)]}
                                  {:refetch-on [::a-events/add-announcement-success ::a-events/remove-announcement-success]
                                   :refetch-id :announcements}])]
    (fn []
      (let [adding? (subscribe [::a-subs/adding-announcement?])]
        [admin-layout
         [:div.headerAnnouncement
          [:span.titleCel.col-user "Add Announcement"]
          [:div.form.formAnnouncement
           [:div.textField
            [editor {:id "textareaAnnouncement"
                     :disabled @adding?
                     :value (:announcement @form-data)
                     :onEditorChange (fn [value]
                                       (swap! form-data assoc :announcement value))}]]
           [:input.btBasic.btBasic-light
            {:type "submit"
             :value "POST"
             :disabled @adding?
             :on-click #(dispatch [::a-events/add-announcement {:form-data @form-data
                                                                :on-success (fn []
                                                                              (swap! form-data assoc :announcement ""))}])
             }]]]
         [announcement-entries announcements-search]]))))
