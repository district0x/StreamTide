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
         :components #js {:DropdownIndicator (fn [^js c]
                                               (let [is-open? (-> c .-selectProps .-menuIsOpen)
                                                     classname (str "dropdown-indicator " (if is-open? "dropdown-opened" "dropdown-closed"))]
                                                 (r/create-element "div" #js {:className classname})))
                          :IndicatorSeparator #()}
         :onChange on-change
         :value (clj->js value)
         :options (clj->js options)}))

(defn select [{:keys [:form-data :id :options :on-change :class :initial-value] :as opts}]
    (react-select (merge
                    {:options options
                     :class class
                     :on-change (fn [selected-entry]
                                  (let [val (.-value selected-entry)
                                        label (.-label selected-entry)]
                                    (swap! form-data (fn [current-value]
                                                       (with-meta (assoc-by-path current-value id val)
                                                                  (assoc (meta current-value) :touched? true))))
                                    (when on-change (on-change {:value val :label label}))))
                     :value (let [value (get-by-path @form-data id)
                                  value (or value initial-value)
                                  label (:label (first (filter #(= value (:value %)) options)))]
                              {:value value :label label})}
                    (apply dissoc opts [:form-data :id :options :on-change :class]))))
