(ns streamtide.ui.components.custom-select
  (:require ["react-select" :default Select]
            [district.ui.component.form.input :refer [assoc-by-path get-by-path]]
            [reagent.core :as r]))

(defn react-select [{:keys [:on-change :options :class :value]}]
  (r/create-element
    Select
    #js {:className class
         :classNamePrefix "select"
         :isSearchable false
         :components #js {:DropdownIndicator #()
                          :IndicatorSeparator #()}
         :dropdownindicator nil
         :onChange on-change
         :value (clj->js value)
         :options (clj->js options)}))

(defn select [{:keys [:form-data :id :options :on-change :class]}]
    (react-select {:options options
                   :class class
                   :on-change (fn [selected-entry]
                                (let [val (.-value selected-entry)
                                      label (.-label selected-entry)]
                                  (swap! form-data assoc-by-path id val)
                                  (when on-change (on-change {:value val :label label}))))
                   :value (let [value (get-by-path @form-data id)
                                label (:label (first (filter #(= value (:value %)) options)))]
                            {:value value :label label})}))
